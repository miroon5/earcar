package com.team.firstcar.settings.store

import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CarIdentity
import com.team.firstcar.settings.model.ProfileSnapshot
import com.team.firstcar.settings.model.SavedItem
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * ProfileSnapshot <-> JSON. 개인정보(이름, 연락처)는 넣지 않는다.
 * 데모 차량 capability JSON(assets/vehicles)도 같은 [capabilityFromJson]으로 읽는다.
 */
object ProfileJson {

    fun encode(p: ProfileSnapshot): String = JSONObject().apply {
        put("version", p.version)
        put("savedAt", p.savedAt)
        put("sourceCar", JSONObject().put("key", p.sourceCar.key).put("name", p.sourceCar.displayName)
            .put("demo", p.sourceCar.isDemo))
        put("items", JSONArray().apply {
            p.items.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.itemId)
                    s.value?.let { put("value", valueToJson(it)) }
                    s.capability?.let { put("capability", capabilityToJson(it)) }
                    s.failure?.let { put("failure", it) }
                })
            }
        })
    }.toString()

    /** 버전이 맞지 않거나 깨진 파일이면 null (앱은 "저장된 세팅 없음"으로 동작) */
    fun decode(text: String): ProfileSnapshot? = runCatching {
        val o = JSONObject(text)
        val version = o.getInt("version")
        if (version > ProfileSnapshot.CURRENT_VERSION) return null
        val car = o.getJSONObject("sourceCar")
        val arr = o.getJSONArray("items")
        ProfileSnapshot(
            version = version,
            savedAt = o.getLong("savedAt"),
            sourceCar = CarIdentity(car.getString("key"), car.getString("name"), car.optBoolean("demo")),
            items = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SavedItem(
                    itemId = obj.getString("id"),
                    value = obj.optJSONObject("value")?.let(::valueFromJson),
                    capability = obj.optJSONObject("capability")?.let(::capabilityFromJson),
                    failure = if (obj.has("failure")) obj.getString("failure") else null,
                )
            },
        )
    }.getOrNull()

    fun valueToJson(v: SettingValue): JSONObject = when (v) {
        is SettingValue.Switch -> JSONObject().put("t", "switch").put("on", v.on)
        is SettingValue.Level -> JSONObject().put("t", "level").put("v", v.value)
        is SettingValue.Number -> JSONObject().put("t", "number").put("v", v.value).apply { v.unit?.let { put("unit", it.name) } }
        is SettingValue.Enum -> JSONObject().put("t", "enum").put("name", v.name)
        is SettingValue.Color -> JSONObject().put("t", "color").put("rgb", v.rgb).apply { v.brightness?.let { put("brightness", it) } }
        is SettingValue.Position -> JSONObject().put("t", "position").put("v", v.value)
    }

    fun valueFromJson(o: JSONObject): SettingValue? = when (o.optString("t")) {
        "switch" -> SettingValue.Switch(o.getBoolean("on"))
        "level" -> SettingValue.Level(o.getInt("v"))
        "number" -> SettingValue.Number(o.getDouble("v"), o.optStringOrNull("unit")?.let(::unitOf))
        "enum" -> SettingValue.Enum(o.getString("name"))
        "color" -> SettingValue.Color(o.getInt("rgb"), if (o.has("brightness")) o.getInt("brightness") else null)
        "position" -> SettingValue.Position(o.getInt("v"))
        else -> null
    }

    fun capabilityToJson(c: CapabilityInfo): JSONObject = JSONObject().apply {
        c.supported?.let { put("supported", it) }
        c.min?.let { put("min", it) }
        c.max?.let { put("max", it) }
        c.step?.let { put("step", it) }
        c.allowed?.let { put("allowed", JSONArray(it.toList())) }
        c.palette?.let { p -> put("palette", JSONArray(p.map { "#%06X".format(it and 0xFFFFFF) })) }
        c.unit?.let { put("unit", it.name) }
    }

    fun capabilityFromJson(o: JSONObject): CapabilityInfo = CapabilityInfo(
        supported = if (o.has("supported")) o.getBoolean("supported") else null,
        min = if (o.has("min")) o.getDouble("min") else null,
        max = if (o.has("max")) o.getDouble("max") else null,
        step = if (o.has("step")) o.getDouble("step") else null,
        allowed = o.optJSONArray("allowed")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
        palette = o.optJSONArray("palette")?.let { a ->
            (0 until a.length()).mapNotNull { parseColor(a.getString(it)) }
        },
        unit = o.optStringOrNull("unit")?.let(::unitOf),
    )

    fun parseColor(s: String): Int? = s.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)

    private fun unitOf(s: String): TempUnit? = TempUnit.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
