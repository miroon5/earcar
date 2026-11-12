package com.team.firstcar.settings

import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import com.team.firstcar.settings.transform.Transformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformerTest {
    private fun range(min: Int, max: Int) = CapabilityInfo(supported = true, min = min.toDouble(), max = max.toDouble())

    @Test fun `열선 3단계 중 3은 2단계 차에서 2`() {
        val o = Transformer.transform(SettingValue.Level(3), range(0, 3), range(0, 2))
        assertEquals(CompareStatus.ADAPTED, o.status)
        assertEquals(SettingValue.Level(2), o.proposed)
    }

    @Test fun `열선 1단계는 1단계 유지 (값이 같으면 SAME)`() {
        val o = Transformer.transform(SettingValue.Level(1), range(0, 3), range(0, 2))
        assertEquals(CompareStatus.SAME, o.status)
        assertEquals(SettingValue.Level(1), o.proposed)
    }

    @Test fun `꺼짐은 꺼짐 유지`() {
        val o = Transformer.transform(SettingValue.Level(0), range(0, 3), range(0, 5))
        assertEquals(CompareStatus.SAME, o.status)
        assertEquals(SettingValue.Level(0), o.proposed)
    }

    @Test fun `같은 범위면 SAME`() {
        assertEquals(CompareStatus.SAME, Transformer.transform(SettingValue.Level(2), range(0, 3), range(0, 3)).status)
    }

    @Test fun `mapLevel 경계값`() {
        assertEquals(5, Transformer.mapLevel(3, 0, 3, 0, 5))
        assertEquals(1, Transformer.mapLevel(1, 0, 3, 0, 5))
        assertEquals(3, Transformer.mapLevel(2, 0, 3, 0, 5))   // 1 + 0.5*4 = 3
        assertEquals(2, Transformer.mapLevel(1, 0, 1, 0, 2))   // 1단계뿐인 차의 켜짐 -> 최고 단계
        assertEquals(1, Transformer.mapLevel(8, 1, 8, 1, 1))   // 최소 1인 풍량
        assertEquals(3, Transformer.mapLevel(4, 1, 8, 1, 6))   // 1 + 3/7*5 = 3.14 -> 3
    }

    @Test fun `대상 capability를 모르면 UNKNOWN (절대 SAME 아님)`() {
        assertEquals(CompareStatus.UNKNOWN, Transformer.transform(SettingValue.Switch(true), null, null).status)
        assertEquals(CompareStatus.UNKNOWN, Transformer.transform(SettingValue.Switch(true), null, CapabilityInfo()).status)
        val noRange = CapabilityInfo(supported = true)
        assertEquals(CompareStatus.UNKNOWN, Transformer.transform(SettingValue.Level(2), range(0, 3), noRange).status)
    }

    @Test fun `미지원이면 UNSUPPORTED`() {
        val o = Transformer.transform(SettingValue.Switch(true), null, CapabilityInfo(supported = false))
        assertEquals(CompareStatus.UNSUPPORTED, o.status)
        assertNull(o.proposed)
    }

    @Test fun `온도는 범위로 클램프하고 스텝에 맞춘다`() {
        val target = CapabilityInfo(supported = true, min = 17.0, max = 27.0, step = 0.5, unit = TempUnit.CELSIUS)
        val o = Transformer.transform(SettingValue.Number(16.0, TempUnit.CELSIUS), null, target)
        assertEquals(CompareStatus.ADAPTED, o.status)
        assertEquals(SettingValue.Number(17.0, TempUnit.CELSIUS), o.proposed)
        val same = Transformer.transform(SettingValue.Number(22.0, TempUnit.CELSIUS), null, target)
        assertEquals(CompareStatus.SAME, same.status)
        val snap = Transformer.transform(SettingValue.Number(22.3, TempUnit.CELSIUS), null, target)
        assertEquals(SettingValue.Number(22.5, TempUnit.CELSIUS), snap.proposed)
    }

    @Test fun `화씨 72도는 섭씨 차에서 22도`() {
        val target = CapabilityInfo(supported = true, min = 16.0, max = 32.0, step = 0.5, unit = TempUnit.CELSIUS)
        val o = Transformer.transform(SettingValue.Number(72.0, TempUnit.FAHRENHEIT), null, target)
        assertEquals(CompareStatus.ADAPTED, o.status)
        assertEquals(SettingValue.Number(22.0, TempUnit.CELSIUS), o.proposed)
    }

    @Test fun `Enum은 값 집합에 있으면 SAME, 없으면 UNSUPPORTED, 목록 모르면 UNKNOWN`() {
        val cap = CapabilityInfo(supported = true, allowed = setOf("NORMAL", "ECO", "SPORT"))
        assertEquals(CompareStatus.SAME, Transformer.transform(SettingValue.Enum("eco"), null, cap).status)
        assertEquals(CompareStatus.UNSUPPORTED, Transformer.transform(SettingValue.Enum("SMART"), null, cap).status)
        assertEquals(CompareStatus.UNKNOWN, Transformer.transform(SettingValue.Enum("ECO"), null, CapabilityInfo(supported = true)).status)
    }

    @Test fun `색은 지원 색 중 가장 가까운 색`() {
        val cap = CapabilityInfo(supported = true, palette = listOf(0xFFFFFF, 0xFF8A00, 0x2F80ED))
        val o = Transformer.transform(SettingValue.Color(0x3366FF), null, cap)
        assertEquals(CompareStatus.ADAPTED, o.status)
        assertEquals(SettingValue.Color(0x2F80ED), o.proposed)
        assertEquals(CompareStatus.UNKNOWN, Transformer.transform(SettingValue.Color(0x3366FF), null, CapabilityInfo(supported = true)).status)
    }

    @Test fun `시트 위치는 참고용 비율만`() {
        val o = Transformer.transform(SettingValue.Position(150), range(0, 300), range(0, 200))
        assertEquals(CompareStatus.REFERENCE_ONLY, o.status)
        assertEquals(SettingValue.Position(100), o.proposed)
        val unknownTarget = Transformer.transform(SettingValue.Position(150), range(0, 300), CapabilityInfo())
        assertEquals(CompareStatus.REFERENCE_ONLY, unknownTarget.status)
    }
}
