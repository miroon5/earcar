package com.team.firstcar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 시트 조절 항목이 "어느 부위가 어느 쪽으로 움직이는 것인지" 보여 주는 작은 그림.
 * 시트 옆모습을 옅은 회색으로 깔고, 해당 부위만 검정으로 칠한 뒤 움직이는 방향을 화살표로 그린다.
 * 그림에 없는 부위(허리 지지, 안전벨트 고정점)는 검정 점으로 위치를 찍는다.
 */
@Composable
fun SeatPartDiagram(itemId: String, size: Dp, modifier: Modifier = Modifier) {
    val spec = specFor(itemId)
    val shape = RoundedCornerShape(size * 0.2f)
    Canvas(modifier.size(size).clip(shape).background(Color.White).border(1.dp, Color(0xFFE3E5E8), shape)) {
        val k = this.size.minDimension / VIEW
        withTransform({ scale(k, k, Offset.Zero); translate(-VIEW_X, -VIEW_Y) }) {
            val hinge = Offset(SeatArt.HINGE_X, SeatArt.HINGE_Y)
            fun color(part: Part) = if (part in spec.highlight) INK else DIM
            mono(SeatArt.RAILS.drop(1), DIM) // 바닥 그림자는 뺀다
            rotate(-SeatArt.BASE_RECLINE_DEG, hinge) {
                mono(SeatArt.HEAD, color(Part.HEAD))
                mono(SeatArt.BACK, color(Part.BACK))
                if (Part.BOLSTER in spec.highlight) mono(SeatArt.BACK.subList(2, 3), INK)
            }
            mono(SeatArt.CUSHION, color(Part.CUSHION))
            spec.belt?.let { drawLine(INK, it, Offset(440f, 700f), 14f, StrokeCap.Round) }
            spec.dot?.let { drawCircle(Color.White, 46f, it); drawCircle(INK, 34f, it) }
            spec.marks.forEach { m ->
                when (m) {
                    is Mark.Line -> { drawLine(INK, m.a, m.b, STROKE, StrokeCap.Round); head(m.a, m.b - m.a); head(m.b, m.a - m.b) }
                    is Mark.Arc -> {
                        drawArc(INK, m.start, m.sweep, false, Offset(m.c.x - m.r, m.c.y - m.r), Size(m.r * 2, m.r * 2),
                            style = Stroke(STROKE, cap = StrokeCap.Round))
                        // 호의 양 끝에서 접선 방향으로 화살촉
                        listOf(m.start to 1f, m.start + m.sweep to -1f).forEach { (deg, dir) ->
                            val a = Math.toRadians(deg.toDouble())
                            val p = Offset(m.c.x + m.r * cos(a).toFloat(), m.c.y + m.r * sin(a).toFloat())
                            head(p, Offset(-sin(a).toFloat(), cos(a).toFloat()) * dir)
                        }
                    }
                }
            }
        }
    }
}

private enum class Part { HEAD, BACK, CUSHION, BOLSTER }

private sealed interface Mark {
    data class Line(val a: Offset, val b: Offset) : Mark
    data class Arc(val c: Offset, val r: Float, val start: Float, val sweep: Float) : Mark
}

private class Spec(val highlight: Set<Part>, val marks: List<Mark>, val dot: Offset? = null, val belt: Offset? = null)

private val INK = Color(0xFF16181B)
private val DIM = Color(0xFFCDD1D6)
private const val STROKE = 26f
private const val VIEW = 860f   // 그림 좌표(1000)에서 보여 줄 정사각형 한 변
private const val VIEW_X = 140f
private const val VIEW_Y = 50f

private val RECLINE = Math.toRadians(-SeatArt.BASE_RECLINE_DEG.toDouble())
private val N = Offset(cos(RECLINE).toFloat(), sin(RECLINE).toFloat())   // 등받이 앞쪽(탑승자 쪽) 방향
private val U = Offset(sin(RECLINE).toFloat(), -cos(RECLINE).toFloat())  // 등받이를 따라 위쪽 방향

/** 똑바로 선 등받이 좌표를 눕힌 뒤의 그림 좌표로 */
private fun onBack(x: Float, y: Float): Offset {
    val dx = x - SeatArt.HINGE_X
    val dy = y - SeatArt.HINGE_Y
    return Offset(SeatArt.HINGE_X + dx * cos(RECLINE).toFloat() - dy * sin(RECLINE).toFloat(),
        SeatArt.HINGE_Y + dx * sin(RECLINE).toFloat() + dy * cos(RECLINE).toFloat())
}

private val ALL = setOf(Part.HEAD, Part.BACK, Part.CUSHION)

private fun specFor(itemId: String): Spec {
    val head = onBack(404f, 230f)
    return when (itemId) {
        "seat.foreAft" -> Spec(ALL, listOf(Mark.Line(Offset(590f, 520f), Offset(930f, 520f))))
        "seat.height" -> Spec(ALL, listOf(Mark.Line(Offset(910f, 470f), Offset(910f, 790f))))
        "seat.depth" -> Spec(setOf(Part.CUSHION), listOf(Mark.Line(Offset(640f, 540f), Offset(940f, 540f))))
        "seat.tilt" -> Spec(setOf(Part.CUSHION), listOf(Mark.Arc(Offset(420f, 700f), 480f, -30f, 44f)))
        "seat.backrest" -> Spec(setOf(Part.BACK), listOf(Mark.Arc(Offset(SeatArt.HINGE_X, SeatArt.HINGE_Y), 585f, -106f, 40f)))
        "seat.lumbarForeAft" -> onBack(456f, 570f).let { p -> Spec(emptySet(), listOf(Mark.Line(p + N * 70f, p + N * 300f)), dot = p) }
        "seat.lumbarSide" -> onBack(500f, 540f).let { p -> Spec(setOf(Part.BOLSTER), listOf(Mark.Line(p + N * 50f, p + N * 280f))) }
        "seat.headrestAngle" -> Spec(setOf(Part.HEAD), listOf(Mark.Arc(onBack(404f, 300f), 200f, -92f, 56f)))
        "seat.headrestForeAft" -> Spec(setOf(Part.HEAD), listOf(Mark.Line(head + N * 80f, head + N * 330f)))
        "seat.headrestHeight" -> (head + N * 150f).let { p -> Spec(setOf(Part.HEAD), listOf(Mark.Line(p - U * 105f, p + U * 105f))) }
        "seat.beltHeight" -> onBack(330f, 330f).let { p ->
            Spec(emptySet(), listOf(Mark.Line(Offset(p.x + 330f, p.y - 150f), Offset(p.x + 330f, p.y + 150f))), dot = p, belt = p)
        }
        else -> Spec(ALL, emptyList())
    }
}

private fun DrawScope.mono(shapes: List<SeatShape>, color: Color) =
    shapes.forEach { if (it.fill != null) drawPath(it.path, color) }

/** [tip]에서 [back] 방향(화살 몸통 쪽)으로 벌어지는 화살촉 */
private fun DrawScope.head(tip: Offset, back: Offset) {
    val len = back.getDistance().takeIf { it > 0f } ?: return
    val d = back / len
    listOf(0.6f, -0.6f).forEach { a ->
        val v = Offset(d.x * cos(a) - d.y * sin(a), d.x * sin(a) + d.y * cos(a))
        drawLine(INK, tip, tip + v * 62f, STROKE, StrokeCap.Round)
    }
}
