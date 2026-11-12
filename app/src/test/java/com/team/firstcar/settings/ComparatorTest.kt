package com.team.firstcar.settings

import com.team.firstcar.settings.compare.Comparator
import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CarIdentity
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.settings.model.ProfileSnapshot
import com.team.firstcar.settings.model.SavedItem
import com.team.firstcar.settings.model.SettingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparatorTest {
    private val snapshot = ProfileSnapshot(
        savedAt = 0L,
        sourceCar = CarIdentity("real:a", "내 차"),
        items = listOf(
            SavedItem("hvac.seatHeat", SettingValue.Level(3), CapabilityInfo(true, 0.0, 3.0)),
            SavedItem("hvac.steeringHeat", SettingValue.Switch(true), CapabilityInfo(true)),
            SavedItem("hvac.ac", SettingValue.Switch(true), CapabilityInfo(true)),
            SavedItem("hvac.fanSpeed", null, null, "값 응답 없음"),
            SavedItem("unknown.item", SettingValue.Switch(true), null),
        ),
    )

    @Test fun `시연 핵심 장면 - 유사, 안 됨, 확인 불가`() {
        val target = mapOf(
            "hvac.seatHeat" to CapabilityInfo(true, 0.0, 2.0),
            "hvac.steeringHeat" to CapabilityInfo(false),
            // hvac.ac는 대상 정보 없음 -> UNKNOWN
        )
        val r = Comparator.compare(snapshot, target).associateBy { it.itemId }
        assertEquals(CompareStatus.ADAPTED, r["hvac.seatHeat"]!!.status)
        assertEquals(CompareStatus.UNSUPPORTED, r["hvac.steeringHeat"]!!.status)
        assertTrue(r["hvac.steeringHeat"]!!.note.contains("시트 열선")) // 대체 안내 포함
        assertEquals(CompareStatus.UNKNOWN, r["hvac.ac"]!!.status)
        assertEquals(CompareStatus.UNKNOWN, r["hvac.fanSpeed"]!!.status) // 저장 때 못 읽음
        assertTrue("레지스트리에 없는 항목은 제외", "unknown.item" !in r)
    }

    @Test fun `요약 개수`() {
        val r = Comparator.compare(snapshot, mapOf("hvac.ac" to CapabilityInfo(true)))
        val s = Comparator.summary(r)
        assertEquals(1, s[CompareStatus.SAME])
        assertEquals(3, s[CompareStatus.UNKNOWN])
    }
}
