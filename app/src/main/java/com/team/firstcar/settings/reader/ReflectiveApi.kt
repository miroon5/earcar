package com.team.firstcar.settings.reader

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties

/**
 * Vehicle SDK 도메인 API를 이름 규칙으로 찾아 호출한다.
 *
 * 왜 리플렉션인가: 공식 문서에는 리스너 이름만 있고 HVAC, Light, Seat 메서드 시그니처가 없다.
 * 이름을 추측해 직접 호출하면 하나만 틀려도 빌드 전체가 깨진다. 공식 샘플(pleos-sample-vehicle-app의
 * VehicleApiExplorer)도 같은 방식으로 19개 도메인을 호출하므로 이 패턴을 따른다.
 * 못 찾은 메서드는 진단 화면에 "메서드 없음"으로 보이고, 실제 이름은 "API 목록"에서 확인한다.
 */
object ReflectiveApi {

    /** 공식 샘플 PublicApiPolicy에서 비공개 처리된 API */
    private val blocked = setOf(
        "CarInfo.getCapabilityInfo",
        "Safety.checkTelltalesCapabilities", "Safety.getTelltalesInfo",
        "Tire.checkTirePressureCapabilities", "Tire.getTirePressure",
    )

    private val fnCache = ConcurrentHashMap<KClass<*>, List<KFunction<*>>>()

    fun functions(cls: KClass<*>): List<KFunction<*>> = fnCache.getOrPut(cls) {
        runCatching {
            cls.declaredMemberFunctions.filter { f ->
                f.annotations.none { it is Deprecated } && "${cls.simpleName}.${f.name}" !in blocked
            }
        }.getOrDefault(emptyList())
    }

    data class Resolved(val capability: KFunction<*>?, val read: KFunction<*>?) {
        val isEmpty get() = capability == null && read == null
        fun describe(): String = listOfNotNull(capability?.name, read?.name).joinToString(" / ").ifEmpty { "메서드 없음" }
    }

    /** key 예: "HvacSeatTemperature" */
    fun resolve(cls: KClass<*>, key: String): Resolved {
        val fns = functions(cls).filter { f -> f.parameters.none { it.isListener() } }
        val variants = listOf(key, key.removeSuffix("State"), key + "State").distinct()
        fun pick(names: List<String>): KFunction<*>? = names.firstNotNullOfOrNull { n ->
            fns.filter { it.name.equals(n, ignoreCase = true) }.minByOrNull { f -> f.parameters.count { it.kind == KParameter.Kind.VALUE } }
        }
        val cap = pick(variants.flatMap { listOf("check${it}Capabilities", "check${it}Capability") })
        val read = pick(variants.flatMap { listOf("getCurrent$it", "get$it", "is$it") })
        return Resolved(cap, read)
    }

    /**
     * 설정(set) 메서드. 조회 이름과 설정 이름이 조금씩 달라 여러 후보를 본다.
     * - State, Value 접미사를 뗀 이름: key "HvacTemperatureValue" -> setHvacTemperature
     * - Hvac 접두사를 붙이거나 뗀 이름: Seat의 key "SeatTemperature" -> setHvacSeatTemperature,
     *   SteeringWheel의 key "SteeringWheelHeatState" -> setHvacSteeringWheelHeat
     */
    fun resolveSetter(cls: KClass<*>, key: String): KFunction<*>? {
        val fns = functions(cls).filter { f -> f.parameters.none { it.isListener() } }
        val bases = listOf(key, key.removeSuffix("State"), key.removeSuffix("Value")).distinct()
        val variants = bases.flatMap { n -> listOf(n, "Hvac$n", n.removePrefix("Hvac")) }.distinct()
        return variants.firstNotNullOfOrNull { n -> fns.firstOrNull { it.name.equals("set$n", ignoreCase = true) } }
    }

    sealed interface Outcome {
        data class Success(val values: List<Any?>) : Outcome
        data class Failure(val message: String) : Outcome
        data object Timeout : Outcome
    }

    /**
     * 비동기 SDK 함수 호출. onComplete/onFailed 같은 함수형 파라미터에 콜백을 넣고, area 파라미터는
     * 운전석 기준으로 채운다. 콜백이 없는 동기 함수면 반환값을 결과로 쓴다.
     * [valueArg]를 주면 설정(set) 호출로 보고, 나머지 값 파라미터를 그 함수가 돌려주는 값으로 채운다.
     */
    suspend fun call(target: Any, fn: KFunction<*>, timeoutMs: Long = 2_500, valueArg: ((KClass<*>) -> Any?)? = null): Outcome =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Outcome> { cont ->
                val done = AtomicBoolean(false)
                fun finish(o: Outcome) { if (done.compareAndSet(false, true) && cont.isActive) cont.resume(o) }
                try {
                    var hasCallback = false
                    val args = mutableMapOf<KParameter, Any?>()
                    for (p in fn.parameters) {
                        if (p.kind == KParameter.Kind.INSTANCE) { args[p] = target; continue }
                        val cls = p.type.classifier as? KClass<*>
                        when {
                            cls != null && cls in FUNCTION_CLASSES -> {
                                hasCallback = true
                                val isFailure = p.name.orEmpty().let { it.contains("fail", true) || it.contains("error", true) }
                                args[p] = lambda(cls) { values ->
                                    finish(if (isFailure) Outcome.Failure(describeError(values)) else Outcome.Success(values))
                                }
                            }
                            cls != null && AreaFactory.isArea(cls) ->
                                args[p] = AreaFactory.create(cls) ?: error("area 생성 실패: ${cls.simpleName}")
                            // 설정 호출: 콜백, area가 아닌 값 파라미터에 적용할 값을 타입에 맞춰 넣는다
                            valueArg != null && cls != null ->
                                args[p] = valueArg(cls) ?: error("값을 ${cls.simpleName} 형식으로 바꿀 수 없음")
                            p.isOptional -> Unit
                            p.type.isMarkedNullable -> args[p] = null
                            else -> error("지원하지 않는 파라미터 ${p.name}: ${p.type}")
                        }
                    }
                    val r = fn.callBy(args)
                    when {
                        !hasCallback -> finish(Outcome.Success(listOf(r)))
                        r == false -> finish(Outcome.Failure("${fn.name}이(가) false를 반환"))
                    }
                } catch (e: Throwable) {
                    val cause = (e as? InvocationTargetException)?.targetException ?: e
                    finish(Outcome.Failure(cause.message ?: cause.javaClass.simpleName))
                }
            }
        } ?: Outcome.Timeout

    /** 진단용: 도메인 클래스의 공개 메서드 목록 (실제 이름 확인용) */
    fun catalog(cls: KClass<*>): List<String> = functions(cls).map { f ->
        val ps = f.parameters.filter { it.kind == KParameter.Kind.VALUE }
            .joinToString { "${it.name}: ${(it.type.classifier as? KClass<*>)?.simpleName ?: it.type}" }
        "${f.name}($ps)"
    }.sorted()

    /** 객체에서 이름 후보에 맞는 프로퍼티 값을 꺼낸다. 없으면 null */
    fun prop(obj: Any?, vararg names: String): Any? {
        if (obj == null) return null
        return runCatching {
            val props = obj::class.memberProperties
            names.firstNotNullOfOrNull { n -> props.firstOrNull { it.name.equals(n, true) }?.getter?.call(obj) }
        }.getOrNull()
    }

    private val FUNCTION_CLASSES = setOf(Function0::class, Function1::class, Function2::class, Function3::class)

    private fun lambda(cls: KClass<*>, report: (List<Any?>) -> Unit): Any {
        val f0: () -> Unit = { report(emptyList()) }
        val f1: (Any?) -> Unit = { a -> report(listOf(a)) }
        val f2: (Any?, Any?) -> Unit = { a, b -> report(listOf(a, b)) }
        val f3: (Any?, Any?, Any?) -> Unit = { a, b, c -> report(listOf(a, b, c)) }
        return when (cls) {
            Function0::class -> f0
            Function1::class -> f1
            Function2::class -> f2
            else -> f3
        }
    }

    private fun describeError(values: List<Any?>): String {
        val t = values.filterIsInstance<Throwable>().firstOrNull()
        return t?.message ?: t?.javaClass?.simpleName ?: values.joinToString { RawText.of(it) }
    }

    private fun KParameter.isListener(): Boolean =
        (type.classifier as? KClass<*>)?.qualifiedName?.startsWith("ai.pleos.playground.vehicle.listener.") == true
}

/** area 파라미터를 운전석(1열 좌측) 기준으로 만든다. 생성자가 단일 enum이든 vararg든 처리한다. */
object AreaFactory {
    private const val AREA_PACKAGE = "ai.pleos.playground.vehicle.api.model.area."
    private val preferred = listOf("ROW_1_LEFT", "FRONT_LEFT", "DRIVER", "LEFT", "ROW_1", "FRONT", "ALL", "GLOBAL")
    private val cache = ConcurrentHashMap<KClass<*>, Any>()

    fun isArea(cls: KClass<*>) = cls.qualifiedName?.startsWith(AREA_PACKAGE) == true
    fun isAreaObject(o: Any?) = o != null && isArea(o::class)

    fun create(cls: KClass<*>): Any? = cache[cls] ?: runCatching {
        cls.constructors.sortedBy { it.parameters.size }.firstNotNullOfOrNull { ctor ->
            val p = ctor.parameters.singleOrNull() ?: return@firstNotNullOfOrNull null
            val enumCls = (if (p.isVararg) p.type.arguments.firstOrNull()?.type?.classifier else p.type.classifier) as? KClass<*>
                ?: return@firstNotNullOfOrNull null
            val constants = enumCls.java.enumConstants ?: return@firstNotNullOfOrNull null
            val chosen = preferred.firstNotNullOfOrNull { n -> constants.firstOrNull { (it as Enum<*>).name == n } }
                ?: constants.firstOrNull() ?: return@firstNotNullOfOrNull null
            val arg = if (p.isVararg) java.lang.reflect.Array.newInstance(enumCls.java, 1).also { java.lang.reflect.Array.set(it, 0, chosen) } else chosen
            runCatching { ctor.call(arg) }.getOrNull()
        }
    }.getOrNull()?.also { cache[cls] = it }
}

/** 진단 화면용 값 문자열. 객체 식별자(@1a2b3c)를 지운다 (공식 샘플 FeatureValueFormatter와 같은 처리) */
object RawText {
    private val identity = Regex("""([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*)@[0-9a-fA-F]+""")
    fun of(v: Any?): String {
        val s = when (v) {
            null -> "null"
            is Array<*> -> v.contentDeepToString()
            is IntArray -> v.contentToString()
            is FloatArray -> v.contentToString()
            else -> v.toString()
        }
        return identity.replace(s) { it.value.substringBefore('@').substringAfterLast('.') }.take(160)
    }
}
