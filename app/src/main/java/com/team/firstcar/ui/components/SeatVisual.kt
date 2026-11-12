package com.team.firstcar.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.team.firstcar.ui.theme.LocalReduceMotion
import com.team.firstcar.ui.theme.Motion

/**
 * 시트 자세. 값은 각 조절 범위에서의 비율(0..1)이고 0.5가 기준 자세다.
 * heat, vent는 0..1 세기 (0이면 표시 안 함).
 */
data class SeatPose(
    val foreAft: Float = 0.5f,
    val height: Float = 0.5f,
    val backrest: Float = 0.5f,
    val headrest: Float = 0.5f,
    val heat: Float = 0f,
    val vent: Float = 0f,
)

/**
 * 운전석 옆모습을 벡터(SeatArt)로 그리고 자세 값에 따라 움직인다.
 * 레일은 고정, 방석과 등받이는 앞뒤/높이로 같이 움직이고, 등받이는 힌지를 중심으로 돌며,
 * 헤드레스트는 등받이 축을 따라 오르내린다. 모양은 design/seat/build_seat.py에서 고친다.
 */
@Composable
fun SeatVisual(pose: SeatPose, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val spec: AnimationSpec<Float> = if (reduce) snap() else tween(500, easing = Motion.Ease)
    val foreAft by animateFloatAsState(pose.foreAft, spec, label = "foreAft")
    val height by animateFloatAsState(pose.height, spec, label = "height")
    val backrest by animateFloatAsState(pose.backrest, spec, label = "backrest")
    val headrest by animateFloatAsState(pose.headrest, spec, label = "headrest")
    val heat by animateFloatAsState(pose.heat, spec, label = "heat")
    val vent by animateFloatAsState(pose.vent, spec, label = "vent")

    Canvas(
        modifier
            // SrcAtop으로 시트 위에만 열선/통풍 빛을 입히려면 오프스크린 합성이 필요하다
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics { contentDescription = "운전석 시트 자세 그림" },
    ) {
        val s = size.minDimension
        val k = s / SeatArt.SIZE
        val m = SeatArt.SIZE / METERS_PER_IMAGE // 그림 좌표에서 1m
        val dx = (foreAft - 0.5f) * FORE_AFT_M * m
        val dy = -(height - 0.5f) * HEIGHT_M * m
        val hinge = Offset(SeatArt.HINGE_X, SeatArt.HINGE_Y)

        withTransform({
            translate((size.width - s) / 2, (size.height - s) / 2)
            scale(k, k, Offset.Zero)
        }) {
            shapes(SeatArt.RAILS)
            translate(dx, dy) {
                rotate(-(SeatArt.BASE_RECLINE_DEG + (backrest - 0.5f) * RECLINE_RANGE_DEG), hinge) {
                    translate(0f, -(headrest - 0.5f) * HEADREST_M * m) { shapes(SeatArt.HEAD) }
                    shapes(SeatArt.BACK)
                }
                shapes(SeatArt.CUSHION)
            }
            if (heat > 0.01f) glow(Color(0xFFFF5A1F), heat, Offset(470f + dx, 600f + dy), 360f)
            if (vent > 0.01f) glow(Color(0xFF3FA9FF), vent, Offset(470f + dx, 600f + dy), 360f)
        }
    }
}

private fun DrawScope.shapes(list: List<SeatShape>) = list.forEach { sh ->
    sh.fill?.let { drawPath(sh.path, it) }
    sh.stroke?.let { drawPath(sh.path, it, style = Stroke(sh.strokeWidth, cap = StrokeCap.Round)) }
}

private fun DrawScope.glow(color: Color, strength: Float, center: Offset, radius: Float) {
    drawRect(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.85f * strength.coerceIn(0f, 1f)),
            0.6f to color.copy(alpha = 0.35f * strength.coerceIn(0f, 1f)),
            1f to Color.Transparent,
            center = center, radius = radius,
        ),
        topLeft = Offset.Zero, size = Size(SeatArt.SIZE, SeatArt.SIZE),
        blendMode = BlendMode.SrcAtop,
    )
}

private const val METERS_PER_IMAGE = 1.6f
private const val FORE_AFT_M = 0.2f       // 앞뒤 전체 이동 폭
private const val HEIGHT_M = 0.06f        // 높이 전체 이동 폭
private const val HEADREST_M = 0.07f      // 헤드레스트 전체 이동 폭
private const val RECLINE_RANGE_DEG = 30f // 등받이 전체 회전 폭
