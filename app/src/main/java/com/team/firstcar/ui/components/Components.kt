package com.team.firstcar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.team.firstcar.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.ui.theme.Dimens
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.Motion
import com.team.firstcar.ui.theme.Shapes
import com.team.firstcar.ui.theme.Type

enum class ButtonKind { PRIMARY, DARK, OUTLINE }

/** 선으로 그리는 작은 아이콘. 아이콘 라이브러리 없이 Canvas로만 그린다 */
enum class Glyph { BACK, CHEVRON, EMPTY, CHECK, CLOSE }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.1f
        fun l(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * w, y1 * w), Offset(x2 * w, y2 * w), sw, StrokeCap.Round)
        when (glyph) {
            Glyph.BACK -> { l(.2f, .5f, .82f, .5f); l(.2f, .5f, .46f, .24f); l(.2f, .5f, .46f, .76f) }
            Glyph.CHECK -> { l(.2f, .52f, .42f, .74f); l(.42f, .74f, .8f, .3f) }
            Glyph.CLOSE -> { l(.27f, .27f, .73f, .73f); l(.73f, .27f, .27f, .73f) }
            Glyph.CHEVRON -> { l(.38f, .2f, .68f, .5f); l(.68f, .5f, .38f, .8f) }
            Glyph.EMPTY -> { // 빈 서랍
                l(.14f, .3f, .86f, .3f); l(.14f, .3f, .14f, .78f); l(.86f, .3f, .86f, .78f); l(.14f, .78f, .86f, .78f)
                l(.38f, .52f, .62f, .52f)
            }
        }
    }
}

/** 누르는 동안 살짝 줄어드는 공통 눌림 효과 */
@Composable
fun pressScale(src: MutableInteractionSource): Float {
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.MICRO_MS), label = "press")
    return scale
}

/** pill 버튼. 높이 88dp(차량 터치 기준). 누르면 scale .97 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    height: Dp = Dimens.touch,
    glyph: Glyph? = null,
) {
    val c = LocalOmni.current
    val src = remember { MutableInteractionSource() }
    val scale = pressScale(src)
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> c.accent to c.onAccent
        ButtonKind.DARK -> c.text to c.bg
        ButtonKind.OUTLINE -> c.box to c.text
    }
    Row(
        modifier
            .heightIn(min = height)
            .widthIn(min = 220.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .clip(Shapes.pill)
            .background(bg)
            .then(if (kind == ButtonKind.OUTLINE) Modifier.border(2.dp, c.line, Shapes.pill) else Modifier)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) GlyphIcon(glyph, fg, 30.dp)
        Text(text, style = Type.button, color = fg, textAlign = TextAlign.Center)
    }
}

/** 원형 아이콘 버튼 (뒤로 등). 지름 88dp */
@Composable
fun CircleButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalOmni.current
    val src = remember { MutableInteractionSource() }
    val scale = pressScale(src)
    Box(
        modifier.size(Dimens.touch).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(c.box).border(2.dp, c.line, CircleShape)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, c.text, 36.dp) }
}

/**
 * 홈의 큰 액션 타일. 제목 + 화살표. 꼭 필요할 때만 한 줄 안내를 붙인다.
 * 넓은 화면에서는 남는 높이를 타일이 나눠 채우므로(large) 글자도 함께 키운다.
 */
@Composable
fun ActionTile(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val c = LocalOmni.current
    val src = remember { MutableInteractionSource() }
    val scale = pressScale(src)
    val fg = if (primary) c.onAccent else c.text
    val sub = if (primary) c.onAccent else c.text
    Row(
        modifier
            .heightIn(min = Dimens.tile)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .clip(Shapes.box)
            .background(if (primary) c.accent else c.box)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (large) 40.dp else 28.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (large) 10.dp else 4.dp)) {
            Text(title, style = Type.h2, color = fg)
            if (subtitle != null) Text(subtitle, style = if (large) Type.body else Type.caption, color = sub)
        }
        GlyphIcon(Glyph.CHEVRON, fg, if (large) 44.dp else 36.dp)
    }
}

/** --box 배경 + 16dp 라운드 카드 */
@Composable
fun OmniCard(modifier: Modifier = Modifier, color: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(Shapes.box).background(color ?: LocalOmni.current.box).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** 무대(보여 주는 큰 박스). 흰 배경 + 얇은 테두리 + 둥근 모서리 */
@Composable
fun KvStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalOmni.current
    Box(modifier.clip(Shapes.box).background(c.kv).border(2.dp, c.line, Shapes.box).padding(40.dp), content = content)
}

/** 필터 칩 줄. 칩 하나가 76dp 높이라 주행 전 한 번에 누를 수 있다 */
@Composable
fun FilterChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(labels) { idx, label -> SelectChip(label, idx == selected) { onSelect(idx) } }
    }
}

fun statusLabel(s: CompareStatus) = when (s) {
    CompareStatus.SAME -> "그대로"
    CompareStatus.ADAPTED -> "비슷하게"
    CompareStatus.UNSUPPORTED -> "이 차엔 없음"
    // 아래 둘은 화면에 내보내지 않는다 (CompareScreens의 SHOWN 참고)
    CompareStatus.UNKNOWN, CompareStatus.REFERENCE_ONLY -> ""
}

/** 상태 칩. 색 없이 면의 진하기로만 구분한다: 그대로(검정 면) > 비슷하게(회색 면) > 이 차엔 없음(테두리) */
@Composable
fun StatusChip(status: CompareStatus) {
    val c = LocalOmni.current
    val (bg, fg) = when (status) {
        CompareStatus.SAME -> c.accent to c.onAccent
        CompareStatus.ADAPTED -> c.line to c.text
        else -> Color.Transparent to c.muted
    }
    Box(Modifier.clip(Shapes.pill).background(bg).border(2.dp, if (bg == Color.Transparent) c.line else bg, Shapes.pill)
        .padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(statusLabel(status), style = Type.meta, color = fg)
    }
}

/** 로딩 표시. 글자 없이 원형 로딩만 돈다 */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 72.dp, color: Color = LocalOmni.current.text) {
    CircularProgressIndicator(
        modifier = modifier.size(size).semantics { contentDescription = "불러오는 중" },
        color = color, trackColor = color.copy(alpha = 0.15f), strokeWidth = size / 10, strokeCap = StrokeCap.Round,
    )
}

/**
 * 항목에 맞는 선 아이콘 (design/icons/build_icons.py로 만든 벡터, 흰 선 + 반투명 면).
 * 흰색이라 항상 어두운 면(검정 무대, 검정 타일) 위에 올린다.
 */
@DrawableRes
fun visualFor(itemId: String): Int = when {
    itemId == "hvac.steeringHeat" -> R.drawable.ic_wheel
    itemId == "hvac.temperature" || itemId == "display.tempUnit" -> R.drawable.ic_thermo
    itemId == "hvac.fanSpeed" -> R.drawable.ic_fan
    itemId == "hvac.ac" || itemId == "hvac.maxAc" -> R.drawable.ic_snow
    itemId == "hvac.recirculation" -> R.drawable.ic_recirc
    itemId == "hvac.airCleaner" -> R.drawable.ic_sparkle
    itemId == "ambient.brightness" -> R.drawable.ic_sun
    itemId == "toll.voiceVolume" -> R.drawable.ic_volume
    itemId == "hvac.mirrorHeat" -> R.drawable.ic_mirror
    itemId.startsWith("seat.") || itemId == "hvac.seatHeat" || itemId == "hvac.seatVent" -> R.drawable.ic_seat
    itemId.startsWith("hvac.") -> R.drawable.ic_climate
    itemId.startsWith("ambient.") -> R.drawable.ic_light
    itemId.startsWith("display.") -> R.drawable.ic_display
    itemId == "drivingMode" -> R.drawable.ic_drive
    else -> R.drawable.ic_convenience
}

/** 분류 이름에 맞는 아이콘 (저장 결과 화면) */
@DrawableRes
fun groupVisual(group: String): Int = when (group) {
    "공조" -> R.drawable.ic_climate
    "시트" -> R.drawable.ic_seat
    "외관" -> R.drawable.ic_mirror
    "조명" -> R.drawable.ic_light
    "표시" -> R.drawable.ic_display
    "주행" -> R.drawable.ic_drive
    else -> R.drawable.ic_convenience
}

/** 아이콘은 어디서나 같은 모습: 검정 둥근 타일 위의 흰 선 아이콘 */
@Composable
fun IconTile(@DrawableRes res: Int, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(0xFF16181B)), contentAlignment = Alignment.Center) {
        Image(painterResource(res), contentDescription = null, modifier = Modifier.size(size * 0.54f))
    }
}

/** 빈 상태: 가운데 아이콘 + 한 줄 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    val c = LocalOmni.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(c.box), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.EMPTY, c.faint, 56.dp)
        }
        Text(text, style = Type.bodyL, color = c.muted, textAlign = TextAlign.Center)
    }
}

/** 선택 칩. 높이 76dp. 선택 시 text 배경 + bg 글자 */
@Composable
fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalOmni.current
    val src = remember { MutableInteractionSource() }
    val scale = pressScale(src)
    Box(
        modifier.heightIn(min = Dimens.touchSmall).widthIn(min = 112.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Shapes.pill)
            .background(if (selected) c.text else c.box)
            .border(2.dp, if (selected) c.text else c.line, Shapes.pill)
            .clickable(interactionSource = src, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.title.copy(fontSize = 26.sp), color = if (selected) c.bg else c.text) }
}

/** 색 값 미리보기 원 */
@Composable
fun ColorDot(rgb: Int, size: Dp = 36.dp) {
    val c = LocalOmni.current
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF000000.toInt() or rgb))
            .border(1.dp, c.line, CircleShape)
            .semantics { contentDescription = "색 #%06X".format(rgb) },
    )
}

/** 하단 중앙 토스트 (명세서 4.10, #16181beb pill) */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Shapes.pill).background(Color(0xEB16181B)).padding(horizontal = 36.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(text, style = Type.body, color = Color.White) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = Type.h2, color = LocalOmni.current.text, modifier = modifier.padding(top = 8.dp))

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalOmni.current.line))
