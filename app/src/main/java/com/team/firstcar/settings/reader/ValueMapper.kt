package com.team.firstcar.settings.reader

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.SettingType
import com.team.firstcar.settings.model.SettingValue

/**
 * SDK 원시 값 -> SettingValue, capability 콜백 값 -> CapabilityInfo.
 * capability 콜백은 (CapabilityConfig, AreaBounds 또는 Map<Area, AreaBounds>) 형태다 [검증: 공식 샘플 ApiNumericRange.kt].
 * 원시 값의 타입(Int, Float, Boolean, enum, 객체)은 항목마다 다를 수 있어 넓게 받는다 [추정].
 */
object ValueMapper {

    fun pickValue(values: List<Any?>): Any? =
        values.filterNot { AreaFactory.isAreaObject(it) }.lastOrNull { it != null }

    fun toSetting(type: SettingType, raw: Any?): SettingValue? {
        val v = unwrap(raw) ?: return null
        return when (type) {
            SettingType.SWITCH -> when (v) {
                is Boolean -> SettingValue.Switch(v)
                is Number -> SettingValue.Switch(v.toDouble() != 0.0)
                is Enum<*> -> switchOf(v.name)?.let { SettingValue.Switch(it) }
                is String -> switchOf(v)?.let { SettingValue.Switch(it) }
                else -> null
            }
            SettingType.LEVEL -> when (v) {
                is Number -> SettingValue.Level(v.toInt())
                is Boolean -> SettingValue.Level(if (v) 1 else 0)
                is Enum<*> -> levelOf(v.name)?.let { SettingValue.Level(it) }
                else -> null
            }
            SettingType.NUMBER -> (v as? Number)?.let { SettingValue.Number(it.toDouble()) }
            SettingType.ENUM -> when (v) {
                is Enum<*> -> SettingValue.Enum(v.name)
                is Boolean -> SettingValue.Enum(if (v) "ON" else "OFF")
                is Number, is String -> SettingValue.Enum(v.toString())
                else -> null
            }
            SettingType.COLOR -> colorOf(raw)
            SettingType.POSITION -> (v as? Number)?.let { SettingValue.Position(it.toInt()) }
        }
    }

    /** 데이터 객체로 감싸져 오면 value/state/level 프로퍼티를 꺼낸다 */
    private fun unwrap(raw: Any?): Any? = when (raw) {
        null, is Number, is Boolean, is Enum<*>, is String -> raw
        else -> ReflectiveApi.prop(raw, "value", "state", "level", "status", "position") ?: raw
    }

    private fun switchOf(name: String): Boolean? {
        val n = name.uppercase()
        return when {
            n == "ON" || n == "TRUE" || n.startsWith("ENABLE") || n == "ACTIVE" -> true
            n == "OFF" || n == "FALSE" || n.startsWith("DISABLE") || n == "INACTIVE" -> false
            else -> null
        }
    }

    private fun levelOf(name: String): Int? {
        val n = name.uppercase()
        if (n == "OFF" || n == "NONE") return 0
        Regex("""\d+""").find(n)?.let { return it.value.toInt() }
        return when (n) { "LOW" -> 1; "MID", "MIDDLE", "MEDIUM" -> 2; "HIGH" -> 3; else -> null }
    }

    private fun colorOf(raw: Any?): SettingValue.Color? {
        if (raw is Number) return SettingValue.Color(raw.toInt() and 0xFFFFFF)
        val c = ReflectiveApi.prop(raw, "color", "rgb", "colorValue", "ambientColor") as? Number ?: return null
        val b = ReflectiveApi.prop(raw, "brightness", "level") as? Number
        return SettingValue.Color(c.toInt() and 0xFFFFFF, b?.toInt())
    }

    fun toCapability(values: List<Any?>): CapabilityInfo {
        val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
        val supported = config?.let { it.accessType != AccessType.NONE }
        val area = values.firstOrNull { AreaFactory.isAreaObject(it) }
        val bounds: AreaBounds<*>? = values.firstNotNullOfOrNull { v ->
            when (v) {
                is AreaBounds<*> -> v
                is Map<*, *> -> (v.entries.firstOrNull { (k, _) -> area != null && k == area }?.value ?: v.entries.firstOrNull { (k, _) -> isDriverArea(k) }?.value
                    ?: v.values.firstOrNull()) as? AreaBounds<*>
                else -> null
            }
        }
        val step = (ReflectiveApi.prop(bounds, "step", "increment", "resolution")
            ?: ReflectiveApi.prop(config, "step", "increment", "resolution")) as? Number
        val allowedRaw = ReflectiveApi.prop(bounds, "supportedValues", "supportedValueList", "values", "allowedValues")
            ?: ReflectiveApi.prop(config, "supportedValues", "supportedValueList", "allowedValues")
        val allowed = when (allowedRaw) {
            is Collection<*> -> allowedRaw.mapNotNull { (it as? Enum<*>)?.name ?: it?.toString() }.toSet()
            is Array<*> -> allowedRaw.mapNotNull { (it as? Enum<*>)?.name ?: it?.toString() }.toSet()
            else -> null
        }?.takeIf { it.isNotEmpty() }
        return CapabilityInfo(
            supported = supported,
            min = (bounds?.minValue as? Number)?.toDouble(),
            max = (bounds?.maxValue as? Number)?.toDouble(),
            step = step?.toDouble()?.takeIf { it > 0 },
            allowed = allowed,
        )
    }

    private fun isDriverArea(k: Any?): Boolean = k != null && RawText.of(k).contains("ROW_1_LEFT")
}
