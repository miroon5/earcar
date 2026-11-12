package com.team.firstcar.settings.reader

import android.util.Log
import com.team.firstcar.settings.model.SettingType
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import com.team.firstcar.settings.registry.SettingItem
import com.team.firstcar.vehicle.VehicleSession
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
 * 시트 위치는 적용하지 않는다: SDK에 위치 제어가 없고, 사람이 앉은 채로 시트를 움직이는 것은 안전 문제다.
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
        if (item.type == SettingType.POSITION) return ApplyResult(item.id, value, false, "시트 위치는 직접 조절")
        if (!session.initialized) return ApplyResult(item.id, value, false, session.initError ?: "Vehicle 미초기화")
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

    companion object {
        private const val TAG = "FirstCar"

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
