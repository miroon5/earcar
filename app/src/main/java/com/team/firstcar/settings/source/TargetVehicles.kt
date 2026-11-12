package com.team.firstcar.settings.source

import android.content.Context
import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CarIdentity
import com.team.firstcar.settings.model.SettingType
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.reader.ApplyResult
import com.team.firstcar.settings.reader.SettingsWriter
import com.team.firstcar.settings.registry.SettingItem
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.reader.SettingsReader
import com.team.firstcar.settings.reader.toCapabilityMap
import com.team.firstcar.settings.store.ProfileJson
import org.json.JSONObject

/** 비교 대상 차량. 실제 SDK(현재 에뮬레이터)와 시연용 가상 차량이 같은 인터페이스를 쓴다 */
interface CapabilityProvider {
    val id: String
    val title: String
    val description: String
    val isDemo: Boolean
    suspend fun identity(): CarIdentity
    suspend fun capabilities(): Map<String, CapabilityInfo>
    /** 이 차에 지금 설정돼 있는 값 */
    suspend fun currentValues(): Map<String, SettingValue>
    /** 세팅을 이 차에 적용한다 */
    suspend fun apply(targets: List<Pair<SettingItem, SettingValue>>): List<ApplyResult>
}

class RealCapabilityProvider(private val reader: SettingsReader, private val writer: SettingsWriter) : CapabilityProvider {
    override val id = REAL_ID
    override val title = "IONIQ 9"
    override val description = "Vehicle SDK로 이 차의 지원 범위를 조회합니다"
    override val isDemo = false
    private var cached: CarIdentity? = null
    override suspend fun identity() = cached ?: reader.readCarIdentity().also { cached = it }
    override suspend fun capabilities() = reader.readAll().toCapabilityMap()
    override suspend fun currentValues() = reader.readAll().mapNotNull { r -> r.value?.let { r.itemId to it } }.toMap()
    override suspend fun apply(targets: List<Pair<SettingItem, SettingValue>>) = writer.applyAll(targets)

    companion object { const val REAL_ID = "real" }
}

/**
 * assets/vehicles/[id].json 에 적어 둔 지원 범위로 비교하는 차량 (KONA, GRANDEUR).
 * 비교 대상의 지원 범위만 파일에서 오고, 저장은 항상 실제 SDK 조회다.
 */
class DemoCapabilityProvider(
    override val id: String,
    override val title: String,
    override val description: String,
    private val items: Map<String, CapabilityInfo>,
) : CapabilityProvider {
    override val isDemo = true
    override suspend fun identity() = CarIdentity("demo:$id", title, isDemo = true)
    override suspend fun capabilities() = items

    /**
     * 파일로 정의한 차량은 실제 차가 아니므로 "지금 설정된 값"을 앱 안에서 흉내 낸다.
     * 처음에는 출고 상태(꺼짐, 가장 낮은 단계와 온도, 첫 번째 선택지)이고, 적용하면 이 값이 바뀐다.
     * 실제 차량 제어(SDK set… 호출)는 일어나지 않는다.
     */
    private val state: MutableMap<String, SettingValue> = items.mapNotNull { (id, cap) ->
        if (cap.supported != true) return@mapNotNull null
        val v: SettingValue? = when (SettingRegistry.get(id)?.type) {
            SettingType.SWITCH -> SettingValue.Switch(false)
            SettingType.LEVEL -> SettingValue.Level(cap.min?.toInt() ?: 0)
            SettingType.NUMBER -> SettingValue.Number(cap.min ?: 0.0, cap.unit)
            SettingType.ENUM -> cap.allowed?.firstOrNull()?.let { SettingValue.Enum(it) }
            SettingType.COLOR -> cap.palette?.firstOrNull()?.let { SettingValue.Color(it) }
            else -> null
        }
        v?.let { id to it }
    }.toMap().toMutableMap()

    override suspend fun currentValues(): Map<String, SettingValue> = synchronized(state) { state.toMap() }

    override suspend fun apply(targets: List<Pair<SettingItem, SettingValue>>): List<ApplyResult> = synchronized(state) {
        targets.map { (item, value) ->
            state[item.id] = value
            ApplyResult(item.id, value, applied = true)
        }
    }

    companion object {
        fun loadAll(context: Context): List<DemoCapabilityProvider> {
            val am = context.assets
            val files = runCatching { am.list("vehicles")?.filter { it.endsWith(".json") }.orEmpty() }.getOrDefault(emptyList())
            return files.sorted().mapNotNull { f ->
                runCatching { parse(am.open("vehicles/$f").bufferedReader().use { it.readText() }) }.getOrNull()
            }
        }

        fun parse(text: String): DemoCapabilityProvider {
            val o = JSONObject(text)
            val itemsJson = o.getJSONObject("items")
            val items = itemsJson.keys().asSequence().associateWith { k -> ProfileJson.capabilityFromJson(itemsJson.getJSONObject(k)) }
            return DemoCapabilityProvider(o.getString("id"), o.getString("name"), o.optString("description"), items)
        }
    }
}
