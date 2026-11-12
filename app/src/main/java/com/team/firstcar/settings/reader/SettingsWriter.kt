package com.team.firstcar.settings.reader

import android.util.Log
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import com.team.firstcar.settings.registry.SettingItem
import com.team.firstcar.vehicle.VehicleSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/** 한 항목을 이 차에 적용한 결과. 못 바꾼 항목은 [failure]에 이유가 남는다 (진단, 로그용) */
data class ApplyResult(
    val itemId: String,
    val value: SettingValue,
    val applied: Boolean,
    val failure: String? = null,
    /** 적용하기 전에 그 차에 설정돼 있던 값 (화면에서 "이전 값 → 적용한 값"으로 보여 준다) */
    val before: SettingValue? = null,
)

/**
 * 저장한 세팅을 지금 차에 적용한다 (Vehicle SDK의 set… 호출).
 * 읽기와 같은 방식으로 이름 규칙으로 메서드를 찾는다: key "HvacFanSpeed" -> setHvacFanSpeed(area, value, onComplete, onFailed).
 * 시트 위치는 "이 값으로" 설정하는 메서드가 없고 moveSeat…(앞으로/뒤로/정지)만 있어서,
 * 현재 위치를 읽어 가며 목표 위치에 닿을 때까지 움직였다가 멈춘다 ([applyPosition]).
 * 항목 하나가 실패해도 나머지는 계속한다. 차례로 하나씩 보낸다.
 */
class SettingsWriter(private val session: VehicleSession) {

    suspend fun applyAll(targets: List<Pair<SettingItem, SettingValue>>): List<ApplyResult> =
        targets.map { (item, value) ->
            runCatching { applyItem(item, value) }
                .getOrElse { e -> ApplyResult(item.id, value, false, "예외: ${e.message ?: e.javaClass.simpleName}") }
                .also { Log.d(TAG, "apply ${it.itemId} | ${it.value} | applied=${it.applied} | fail=${it.failure}") }
        }

    private suspend fun applyItem(item: SettingItem, value: SettingValue): ApplyResult {
        if (!session.initialized) return ApplyResult(item.id, value, false, session.initError ?: "Vehicle 미초기화")
        if (value is SettingValue.Position) return applyPosition(item, value)
        var failure = "설정 메서드 없음"
        for (loc in item.locators) {
            val (cls, target) = session.domain(loc.domain) ?: continue
            val fn = ReflectiveApi.resolveSetter(cls, loc.key) ?: continue
            when (val o = ReflectiveApi.call(target, fn, 4_000) { type -> toRaw(value, type) }) {
                is ReflectiveApi.Outcome.Success -> return ApplyResult(item.id, value, true)
                is ReflectiveApi.Outcome.Failure -> failure = "${fn.name}: ${o.message}"
                ReflectiveApi.Outcome.Timeout -> failure = "${fn.name}: 응답 없음"
            }
        }
        return ApplyResult(item.id, value, false, failure)
    }

    /**
     * 시트 축 하나를 목표 위치까지 움직인다.
     * - 목표가 현재보다 크면 FORWARD, 작으면 BACKWARD로 시작한다. 잠시 뒤 오히려 멀어지고 있으면 방향을 한 번 뒤집는다
     *   (축마다 FORWARD가 값을 키우는 쪽인지 문서에 없어서 움직임을 보고 판단한다).
     * - 목표에 닿거나 지나치면 멈춘다. 1.5초 동안 값이 안 변하거나 전체 12초가 지나도 멈춘다.
     * - 어떤 경우에도(취소, 예외 포함) 마지막에 STOP을 보낸다.
     */
    private suspend fun applyPosition(item: SettingItem, value: SettingValue.Position): ApplyResult {
        for (loc in item.locators) {
            val (cls, target) = session.domain(loc.domain) ?: continue
            val readFn = ReflectiveApi.resolve(cls, loc.key).read ?: continue
            val moveFn = ReflectiveApi.resolveMover(cls, loc.key) ?: continue
            suspend fun read(): Int? =
                (ReflectiveApi.call(target, readFn, 1_500) as? ReflectiveApi.Outcome.Success)
                    ?.let { ValueMapper.pickValue(it.values) as? Number }?.toInt()
            suspend fun move(control: String): ReflectiveApi.Outcome =
                ReflectiveApi.call(target, moveFn, 1_500) { type -> type.java.enumConstants?.firstOrNull { (it as Enum<*>).name == control } }

            val start = read() ?: return ApplyResult(item.id, value, false, "${readFn.name}: 현재 위치를 읽지 못함")
            val goal = value.value
            if (kotlin.math.abs(start - goal) <= POSITION_TOLERANCE) return ApplyResult(item.id, value, true)

            var control = if (goal > start) "FORWARD" else "BACKWARD"
            var failure: String? = null
            try {
                val first = move(control)
                if (first !is ReflectiveApi.Outcome.Success) {
                    return ApplyResult(item.id, value, false, "${moveFn.name}: " + ((first as? ReflectiveApi.Outcome.Failure)?.message ?: "응답 없음"))
                }
                var last = start
                var flipped = false
                var stillMs = 0L
                var elapsed = 0L
                while (true) {
                    delay(POLL_MS); elapsed += POLL_MS
                    val now = read() ?: run { failure = "움직이는 중 위치를 읽지 못함"; null } ?: break
                    val reached = kotlin.math.abs(now - goal) <= POSITION_TOLERANCE || (start < goal) == (now > goal) && now != start
                    if (reached) break
                    stillMs = if (now == last) stillMs + POLL_MS else 0L
                    // 멀어지고 있으면 방향이 반대인 것이다. 한 번만 뒤집는다
                    if (!flipped && elapsed >= 600 && kotlin.math.abs(now - goal) > kotlin.math.abs(start - goal)) {
                        move("STOP")
                        control = if (control == "FORWARD") "BACKWARD" else "FORWARD"
                        move(control)
                        flipped = true
                    }
                    last = now
                    if (stillMs >= 1_500) { failure = "${moveFn.name}: 시트가 움직이지 않음"; break }
                    if (elapsed >= 12_000) { failure = "${moveFn.name}: 12초 안에 목표 위치에 닿지 못함"; break }
                }
            } finally {
                withContext(NonCancellable) { move("STOP") }
            }
            return ApplyResult(item.id, value, failure == null, failure)
        }
        return ApplyResult(item.id, value, false, "시트 이동 메서드 없음")
    }

    companion object {
        private const val TAG = "FirstCar"
        private const val POLL_MS = 120L
        private const val POSITION_TOLERANCE = 1

        /** 저장 값을 설정 메서드의 파라미터 타입에 맞는 원시 값으로. 맞출 수 없으면 null */
        fun toRaw(v: SettingValue, type: KClass<*>): Any? {
            val n: Double? = when (v) {
                is SettingValue.Switch -> if (v.on) 1.0 else 0.0
                is SettingValue.Level -> v.value.toDouble()
                is SettingValue.Number -> v.value
                is SettingValue.Color -> v.rgb.toDouble()
                is SettingValue.Position -> v.value.toDouble()
                is SettingValue.Enum -> v.name.toDoubleOrNull()
            }
            return when {
                type == Boolean::class -> when (v) {
                    is SettingValue.Switch -> v.on
                    is SettingValue.Level -> v.value > 0
                    is SettingValue.Enum -> onOff(v.name)
                    else -> null
                }
                type == Int::class -> n?.roundToInt()
                type == Long::class -> n?.toLong()
                type == Float::class -> n?.toFloat()
                type == Double::class -> n
                type == String::class -> (v as? SettingValue.Enum)?.name
                type.java.isEnum -> enumOf(v, type)
                else -> null
            }
        }

        private fun onOff(name: String): Boolean? = when (name.uppercase()) {
            "ON", "TRUE", "RECIRCULATION", "INTERNAL" -> true
            "OFF", "FALSE", "FRESH", "EXTERNAL" -> false
            else -> null
        }

        private fun enumOf(v: SettingValue, type: KClass<*>): Any? {
            val constants = type.java.enumConstants?.filterIsInstance<Enum<*>>().orEmpty()
            fun named(vararg names: String) = constants.firstOrNull { c -> names.any { it.equals(c.name, ignoreCase = true) } }
            // 온도 설정은 값과 함께 단위를 받는다
            if (type.simpleName == "TemperatureUnit") {
                return named(((v as? SettingValue.Number)?.unit ?: TempUnit.CELSIUS).name)
            }
            return when (v) {
                is SettingValue.Switch -> if (v.on) named("ON", "ENABLE", "ENABLED") else named("OFF", "DISABLE", "DISABLED")
                is SettingValue.Enum -> named(v.name)
                is SettingValue.Level ->
                    if (v.value == 0) named("OFF", "NONE", "LEVEL_0")
                    else constants.firstOrNull { c -> Regex("""\d+""").find(c.name)?.value?.toInt() == v.value }
                else -> null
            }
        }
    }
}
