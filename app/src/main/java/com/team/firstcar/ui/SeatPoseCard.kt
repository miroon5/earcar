package com.team.firstcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.team.firstcar.settings.model.CompareResult
import com.team.firstcar.settings.model.ProfileSnapshot
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.ui.components.KvStage
import com.team.firstcar.ui.components.SeatPose
import com.team.firstcar.ui.components.SeatVisual
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.Shapes
import com.team.firstcar.ui.theme.Type
import kotlin.math.roundToInt

/** 저장 당시 범위에서의 위치 비율(0..1). 값이나 범위를 모르면 null (모르는 것을 기준 자세로 꾸미지 않는다) */
private fun ratioOf(profile: ProfileSnapshot?, id: String): Float? {
    val item = profile?.items?.firstOrNull { it.itemId == id } ?: return null
    val v = (item.value as? SettingValue.Position)?.value ?: return null
    val min = item.capability?.min ?: return null
    val max = item.capability?.max ?: return null
    if (max <= min) return null
    return ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)
}

/** 이 차에 맞춘 열선/통풍 단계와 0..1 세기. 꺼짐이거나 지원 안 되면 0 */
private fun levelOf(results: List<CompareResult>, id: String): Pair<Int, Float> {
    val level = (results.firstOrNull { it.itemId == id }?.proposed as? SettingValue.Level)?.value ?: 0
    return level to (level / 3f).coerceIn(0f, 1f)
}

private fun axesOf(profile: ProfileSnapshot?): List<Pair<String, Float?>> = listOf(
    "앞뒤" to ratioOf(profile, "seat.foreAft"),
    "높이" to ratioOf(profile, "seat.height"),
    "등받이" to ratioOf(profile, "seat.backrest"),
    "헤드레스트" to ratioOf(profile, "seat.headrestHeight"),
)

/**
 * 시트 자세 카드 (보기 전용). 시트 그림이 저장한 내 자세로 움직이고,
 * 열선은 주황, 통풍은 파랑 빛으로 보여 준다. 시트 위치는 자동 적용하지 않는 참고용이다.
 * 전환 버튼 없이 항상 저장한 자세 그대로 보여 준다 (저장값이 없는 축은 가운데).
 */
@Composable
fun SeatPoseCard(profile: ProfileSnapshot?, results: List<CompareResult>, modifier: Modifier = Modifier) {
    val c = LocalOmni.current
    val axes = axesOf(profile)
    val known = axes.any { it.second != null }
    val (heatLevel, heat) = levelOf(results, "hvac.seatHeat")
    val (ventLevel, vent) = levelOf(results, "hvac.seatVent")

    val pose = if (known) {
        SeatPose(axes[0].second ?: .5f, axes[1].second ?: .5f, axes[2].second ?: .5f, axes[3].second ?: .5f, heat, vent)
    } else SeatPose(heat = heat, vent = vent)

    KvStage(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("저장한 내 시트 자세", style = Type.h2, color = c.onKv, modifier = Modifier.weight(1f))
                if (heatLevel > 0) Badge("열선 $heatLevel", Color(0xFFC2410C))
                if (ventLevel > 0) Badge("통풍 $ventLevel", Color(0xFF1864AB))
            }
            SeatVisual(pose, Modifier.fillMaxWidth().weight(1f))
            if (known) {
                // 2x2로 나눠 이름과 값을 크게 보여 준다
                axes.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, r) -> Axis(label, r, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Row(
        Modifier.clip(Shapes.pill).background(color.copy(alpha = 0.12f)).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(text, style = Type.meta, color = color)
    }
}

/** 조절 축 하나: 왼쪽에 이름, 오른쪽에 범위에서의 위치(%)를 크게. 저장값이 없으면 "-" */
@Composable
private fun Axis(label: String, ratio: Float?, modifier: Modifier) {
    val c = LocalOmni.current
    Row(
        modifier.clip(Shapes.tile).background(c.box).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = Type.bodyL, color = c.onKvMuted, maxLines = 1, modifier = Modifier.weight(1f))
        Text(ratio?.let { "${(it * 100).roundToInt()}%" } ?: "-", style = Type.h2, maxLines = 1,
            color = if (ratio != null) c.onKv else c.onKvMuted)
    }
}
