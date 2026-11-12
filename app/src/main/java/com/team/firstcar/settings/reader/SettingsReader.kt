package com.team.firstcar.settings.reader

import android.util.Log
import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CarIdentity
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import com.team.firstcar.settings.registry.SettingItem
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.registry.VehicleDomain
import com.team.firstcar.vehicle.VehicleSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 한 항목의 읽기 결과. 저장, 비교, 진단 화면이 모두 이것을 쓴다 */
data class ItemReadResult(
    val itemId: String,
    val value: SettingValue?,
    val capability: CapabilityInfo?,
    val failure: String?,
    val api: String,           // 실제로 쓴 메서드 이름 (진단용)
    val rawValue: String? = null,
    val rawCapability: String? = null,
)

/**
 * 모든 설정 항목을 병렬로 1회 읽는다 (구독 아님, 스냅샷).
 * 항목 하나가 실패하거나 멈춰도 나머지는 계속된다 (항목별 타임아웃 2.5초).
 */
class SettingsReader(private val session: VehicleSession) {

    suspend fun readAll(items: List<SettingItem> = SettingRegistry.items): List<ItemReadResult> = coroutineScope {
        val results = items.map { item -> async { runCatching { readItem(item) }.getOrElse { e ->
            ItemReadResult(item.id, null, null, "예외: ${e.message ?: e.javaClass.simpleName}", "-")
        } } }.awaitAll()
        applyTemperatureUnit(results).onEach { r ->
            Log.d(TAG, "${r.itemId} | ${r.api} | value=${r.rawValue} | cap=${r.rawCapability} | fail=${r.failure}")
        }
    }

    suspend fun readItem(item: SettingItem): ItemReadResult {
        if (!session.initialized) return ItemReadResult(item.id, null, null, session.initError ?: "Vehicle 미초기화", "-")
        var last: ItemReadResult? = null
        for (loc in item.locators) {
            val domain = session.domain(loc.domain)
            if (domain == null) {
                if (last == null) last = ItemReadResult(item.id, null, null, "${loc.domain} 도메인을 얻지 못함", loc.key)
                continue
            }
            val (cls, target) = domain
            val api = ReflectiveApi.resolve(cls, loc.key)
            if (api.isEmpty) {
                if (last == null) last = ItemReadResult(item.id, null, null, "메서드 없음 (${loc.key})", loc.key)
                continue
            }

            // 1) capability: 지원 여부와 범위
            var cap: CapabilityInfo? = null
            var capRaw: String? = null
            var capFail: String? = null
            val capFn = api.capability
            if (capFn != null) {
                val o = ReflectiveApi.call(target, capFn)
                if (o is ReflectiveApi.Outcome.Success) {
                    cap = ValueMapper.toCapability(o.values)
                    capRaw = o.values.joinToString { RawText.of(it) }
                } else if (o is ReflectiveApi.Outcome.Failure) {
                    capFail = o.message
                    if (o.message.contains("not supported", ignoreCase = true)) cap = CapabilityInfo(supported = false)
                } else {
                    capFail = "capability 응답 없음"
                }
            }
            if (cap?.supported == false) {
                return ItemReadResult(item.id, null, cap, "이 차 미지원", api.describe(), null, capRaw)
            }

            // 2) 현재 값
            var value: SettingValue? = null
            var valueRaw: String? = null
            var readFail: String? = null
            val readFn = api.read
            if (readFn == null) {
                readFail = "조회 메서드 없음"
            } else {
                val o = ReflectiveApi.call(target, readFn)
                if (o is ReflectiveApi.Outcome.Success) {
                    val raw = ValueMapper.pickValue(o.values)
                    valueRaw = RawText.of(raw)
                    value = ValueMapper.toSetting(item.type, raw)
                    if (value == null) readFail = if (raw == null) "값이 null" else "값 형식을 해석하지 못함"
                } else if (o is ReflectiveApi.Outcome.Failure) {
                    readFail = o.message
                } else {
                    readFail = "값 응답 없음"
                }
            }

            // 값을 읽었으면 지원하는 것이다. capability가 없거나 실패해도 supported=true로 둔다
            val finalCap = if (value != null) (cap ?: CapabilityInfo()).copy(supported = true) else cap
            val failure = if (value == null) listOfNotNull(readFail, capFail).joinToString(" / ") else null
            val result = ItemReadResult(item.id, value, finalCap, failure, api.describe(), valueRaw, capRaw)
            if (value != null) return result
            last = result
        }
        return last ?: ItemReadResult(item.id, null, null, "API 후보 없음", "-")
    }

    /** 온도 단위를 표시 단위에서 가져와 온도 값과 capability에 붙인다 (단위 환산의 근거) */
    private fun applyTemperatureUnit(results: List<ItemReadResult>): List<ItemReadResult> {
        val unitName = (results.firstOrNull { it.itemId == "display.tempUnit" }?.value as? SettingValue.Enum)?.name
        val unit = TempUnit.entries.firstOrNull { unitName?.contains(it.name, ignoreCase = true) == true }
            ?: return results
        return results.map { r ->
            if (r.itemId != "hvac.temperature") return@map r
            val v = (r.value as? SettingValue.Number)?.copy(unit = unit)
            r.copy(value = v ?: r.value, capability = r.capability?.copy(unit = unit))
        }
    }

    /** 차종 표시. CarInfo 메서드 이름은 [추정]이므로 못 읽으면 에뮬레이터 이름으로 대체한다 */
    suspend fun readCarIdentity(): CarIdentity {
        suspend fun read(keys: List<String>): String? {
            val (cls, target) = session.domain(VehicleDomain.CAR_INFO) ?: return null
            for (k in keys) {
                val fn = ReflectiveApi.resolve(cls, k).read ?: continue
                val o = ReflectiveApi.call(target, fn, 1_500) as? ReflectiveApi.Outcome.Success ?: continue
                val raw = ValueMapper.pickValue(o.values) ?: continue
                val text = (raw as? Enum<*>)?.name ?: RawText.of(raw)
                if (text.isNotBlank() && text != "null") return text
            }
            return null
        }
        if (!session.initialized) return CarIdentity("real:emulator", DEFAULT_CAR_NAME)
        val parts = listOfNotNull(read(SettingRegistry.carInfoMake), read(SettingRegistry.carInfoModel), read(SettingRegistry.carInfoYear))
        val name = parts.joinToString(" ").ifBlank { DEFAULT_CAR_NAME }
        return CarIdentity("real:" + parts.joinToString("|").ifBlank { "emulator" }, name)
    }

    /** 진단용 API 목록. Logcat 태그 FirstCar로도 출력한다 */
    fun catalog(): Map<String, List<String>> = VehicleDomain.entries.associate { d ->
        val list = session.domain(d)?.first?.let(ReflectiveApi::catalog) ?: listOf("(도메인을 얻지 못함)")
        list.forEach { Log.d(TAG, "API ${d.name}.$it") }
        d.name to list
    }

    private companion object { const val TAG = "FirstCar" }
}

/** 이 차(실제 SDK)의 capability 맵 */
fun List<ItemReadResult>.toCapabilityMap(): Map<String, CapabilityInfo> =
    mapNotNull { r -> r.capability?.let { r.itemId to it } }.toMap()

/** 차량 정보(CarInfo)를 못 읽었을 때 화면에 쓰는 차 이름 */
const val DEFAULT_CAR_NAME = "현대 아이오닉 9 프레스티지"
