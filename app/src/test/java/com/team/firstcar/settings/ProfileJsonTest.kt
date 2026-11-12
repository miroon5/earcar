package com.team.firstcar.settings

import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CarIdentity
import com.team.firstcar.settings.model.ProfileSnapshot
import com.team.firstcar.settings.model.SavedItem
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import com.team.firstcar.settings.store.ProfileJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileJsonTest {
    @Test fun `저장과 복원`() {
        val p = ProfileSnapshot(
            savedAt = 1_762_300_000_000L,
            sourceCar = CarIdentity("real:x", "테스트 차"),
            items = listOf(
                SavedItem("hvac.temperature", SettingValue.Number(22.5, TempUnit.CELSIUS),
                    CapabilityInfo(true, 16.0, 32.0, 0.5, unit = TempUnit.CELSIUS)),
                SavedItem("hvac.seatHeat", SettingValue.Level(3), CapabilityInfo(true, 0.0, 3.0)),
                SavedItem("drivingMode", SettingValue.Enum("ECO"), CapabilityInfo(true, allowed = setOf("ECO", "SPORT"))),
                SavedItem("ambient.color", SettingValue.Color(0x2F80ED, 4), CapabilityInfo(true, palette = listOf(0x2F80ED))),
                SavedItem("hvac.ac", SettingValue.Switch(false), null),
                SavedItem("seat.height", SettingValue.Position(40), CapabilityInfo(true, 0.0, 80.0)),
                SavedItem("hvac.fanSpeed", null, null, "값 응답 없음"),
            ),
        )
        assertEquals(p, ProfileJson.decode(ProfileJson.encode(p)))
    }

    @Test fun `미래 버전이나 깨진 파일은 null`() {
        assertNull(ProfileJson.decode("""{"version":99,"savedAt":0,"sourceCar":{"key":"a","name":"b"},"items":[]}"""))
        assertNull(ProfileJson.decode("not json"))
    }
}
