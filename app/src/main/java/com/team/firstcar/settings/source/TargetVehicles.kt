package com.team.firstcar.settings.source

import android.content.Context
import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CarIdentity
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
}

class RealCapabilityProvider(private val reader: SettingsReader) : CapabilityProvider {
    override val id = REAL_ID
    override val title = "IONIQ 9"
    override val description = "Vehicle SDK로 이 차의 지원 범위를 조회합니다"
    override val isDemo = false
    private var cached: CarIdentity? = null
    override suspend fun identity() = cached ?: reader.readCarIdentity().also { cached = it }
    override suspend fun capabilities() = reader.readAll().toCapabilityMap()

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
