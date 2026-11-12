package com.team.firstcar.settings.compare

import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CompareResult
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.settings.model.ProfileSnapshot
import com.team.firstcar.settings.registry.SettingItem
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.transform.Transformer

/** 저장 세팅 x 대상 차 capability -> 항목별 결과 (순수 함수) */
object Comparator {

    fun compare(
        snapshot: ProfileSnapshot,
        target: Map<String, CapabilityInfo>,
        registry: (String) -> SettingItem? = SettingRegistry::get,
    ): List<CompareResult> = snapshot.items.mapNotNull { saved ->
        val item = registry(saved.itemId) ?: return@mapNotNull null
        val value = saved.value
        if (value == null) {
            return@mapNotNull CompareResult(saved.itemId, CompareStatus.UNKNOWN, null, null,
                "저장할 때 읽지 못한 항목이에요")
        }
        val out = Transformer.transform(value, saved.capability, target[saved.itemId])
        val note = if (out.status == CompareStatus.UNSUPPORTED) "${out.note}. ${item.alternative}" else out.note
        CompareResult(saved.itemId, out.status, value, out.proposed, note)
    }

    fun summary(results: List<CompareResult>): Map<CompareStatus, Int> =
        CompareStatus.entries.associateWith { s -> results.count { it.status == s } }
}
