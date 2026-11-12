package com.team.firstcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.team.firstcar.MainViewModel
import com.team.firstcar.UiState
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.reader.ItemReadResult
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.transform.ValueFormat
import com.team.firstcar.ui.components.ButtonKind
import com.team.firstcar.ui.components.ColorDot
import com.team.firstcar.ui.components.Glyph
import com.team.firstcar.ui.components.GlyphIcon
import com.team.firstcar.ui.components.IconTile
import com.team.firstcar.ui.components.PillButton
import com.team.firstcar.ui.components.SeatPartDiagram
import com.team.firstcar.ui.components.Spinner
import com.team.firstcar.ui.components.groupVisual
import com.team.firstcar.ui.components.visualFor
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.Shapes
import com.team.firstcar.ui.theme.Type
import kotlin.math.roundToInt

/**
 * 저장 진행과 결과 (F1). 역할을 둘로만 나눈다.
 * 왼쪽: 저장이 끝났다는 확인 (체크 표시, "세팅 N개를 저장했어요", 홈으로).
 * 오른쪽: 저장한 설정을 분류별로 묶은 상세 목록. 필터나 분류 버튼은 두지 않는다.
 * 읽은 설정만 보여 준다 (못 읽은 항목과 이유는 진단 화면의 몫).
 */
@Composable
fun SaveScreen(ui: UiState, vm: MainViewModel, wide: Boolean) {
    val result = ui.saveResult
    if (ui.saving || result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 88.dp) }
        return
    }
    val groups = remember(result) { groups(result.filter { it.value != null }) }
    val total = groups.sumOf { it.items.size }
    val car = ui.profile?.sourceCar?.displayName

    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    DoneMark(total > 0, if (total > 0) "세팅 ${total}개를 저장했어요" else "저장한 세팅이 없어요", car, big = true)
                }
                PillButton("홈으로", vm::back, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
            }
            SettingSections(groups, Modifier.weight(1.1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DoneMark(total > 0, if (total > 0) "세팅 ${total}개를 저장했어요" else "저장한 세팅이 없어요", car, big = false)
            }
            SettingSections(groups, Modifier.weight(1f).fillMaxWidth())
            PillButton("홈으로", vm::back, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }
}

/** 결과 확인: 검정 원 안의 체크(없으면 X), 큰 한 문장, 차 이름. 저장과 적용 화면이 같이 쓴다 */
@Composable
internal fun DoneMark(done: Boolean, title: String, car: String?, big: Boolean) {
    val c = LocalOmni.current
    val size = if (big) 128.dp else 80.dp
    // 된 것이 있으면 체크, 하나도 없으면 X
    Box(Modifier.size(size).clip(Shapes.pill).background(c.text), contentAlignment = Alignment.Center) {
        GlyphIcon(if (done) Glyph.CHECK else Glyph.CLOSE, c.bg, size * 0.56f)
    }
    // 넓은 화면(big)에서는 아이콘과 문구를 가운데로 맞춘다
    val align = if (big) TextAlign.Center else TextAlign.Start
    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = if (big) Alignment.CenterHorizontally else Alignment.Start) {
        Text(title, style = Type.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
        if (done && car != null) Text(car, style = Type.bodyL, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
    }
}

private data class GroupResult(val name: String, val items: List<ItemReadResult>)

/** 분류별로 묶는다. "시트 위치(참고)"는 시트에 합친다 */
private fun groups(result: List<ItemReadResult>): List<GroupResult> =
    result.groupBy { (SettingRegistry.get(it.itemId)?.group ?: "기타").substringBefore(" 위치") }
        .map { (name, rs) -> GroupResult(name, rs) }

private fun valueText(r: ItemReadResult): String =
    ValueFormat.format(r.value, SettingRegistry.get(r.itemId)?.enumLabels.orEmpty())

/** 분류 제목 아래에 그 분류의 설정을 한 덩어리로 묶은 목록 (설정 앱의 묶음 목록 방식) */
@Composable
private fun SettingSections(groups: List<GroupResult>, modifier: Modifier) {
    val c = LocalOmni.current
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(40.dp)) {
        items(groups, key = { it.name }) { g ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(groupVisual(g.name), size = 48.dp)
                    Text(g.name, style = Type.h2, color = c.text, maxLines = 1)
                    Text("${g.items.size}", style = Type.h2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.text)
                }
                Column(Modifier.fillMaxWidth().clip(Shapes.box).background(c.box)) {
                    g.items.forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.padding(start = 140.dp, end = 28.dp).fillMaxWidth().height(1.dp).background(c.line))
                        SettingRow(r)
                    }
                }
            }
        }
    }
}

/** 설정 한 줄: 왼쪽에 아이콘과 이름, 오른쪽에 눈으로 읽는 표시(단계 칸, 범위 막대, 스위치, 색)와 굵은 값 */
@Composable
private fun SettingRow(r: ItemReadResult) {
    val c = LocalOmni.current
    val v = r.value
    val min = r.capability?.min
    val max = r.capability?.max
    val ranged = min != null && max != null && max > min
    Row(
        Modifier.fillMaxWidth().heightIn(min = 132.dp).padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 시트 조절 항목은 어느 부위가 어느 쪽으로 움직이는지 그림으로, 나머지는 아이콘으로
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            if (r.itemId.startsWith("seat.")) SeatPartDiagram(r.itemId, 88.dp) else IconTile(visualFor(r.itemId), size = 60.dp)
        }
        Text(SettingRegistry.get(r.itemId)?.label ?: r.itemId, style = Type.bodyL, color = c.text,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (v) {
            null -> {}
            is SettingValue.Switch -> SwitchMark(v.on)
            is SettingValue.Color -> { ColorDot(v.rgb, size = 44.dp); Value(ValueFormat.hex(v.rgb)) }
            is SettingValue.Level -> {
                if (ranged) {
                    if (min == 0.0 && max!! <= 8.0) LevelBar(v.value, max.toInt()) else RangeBar(ratio(v.value.toDouble(), min!!, max!!))
                }
                Value(valueText(r))
            }
            is SettingValue.Number -> { if (ranged) RangeBar(ratio(v.value, min!!, max!!)); Value(valueText(r)) }
            is SettingValue.Position -> {
                if (ranged) { val p = ratio(v.value.toDouble(), min!!, max!!); RangeBar(p); Value("${(p * 100).roundToInt()}%") }
                else Value(valueText(r))
            }
            else -> Value(valueText(r))
        }
    }
}

@Composable
private fun Value(text: String) = Text(text, style = Type.title, color = LocalOmni.current.text, maxLines = 1)

private fun ratio(v: Double, min: Double, max: Double): Float = ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)

private val TRACK = Color(0xFFD3D6DB)

/** 단계 값: 전체 단계 수만큼 칸을 깔고 지금 단계까지 채운다 */
@Composable
private fun LevelBar(value: Int, max: Int) {
    val c = LocalOmni.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(max) { i -> Box(Modifier.size(width = 18.dp, height = 12.dp).clip(Shapes.pill).background(if (i < value) c.text else TRACK)) }
    }
}

/** 범위가 있는 값: 범위 안에서 지금 값이 어디쯤인지 */
@Composable
private fun RangeBar(ratio: Float) {
    val c = LocalOmni.current
    Box(Modifier.width(120.dp).height(12.dp).clip(Shapes.pill).background(TRACK)) {
        Box(Modifier.fillMaxWidth(ratio.coerceAtLeast(0.04f)).height(12.dp).clip(Shapes.pill).background(c.text))
    }
}

/** 켜짐/꺼짐: 스위치 모양과 글자를 같이 보여 준다 */
@Composable
private fun SwitchMark(on: Boolean) {
    val c = LocalOmni.current
    Box(
        Modifier.size(width = 72.dp, height = 42.dp).clip(Shapes.pill).background(if (on) c.text else TRACK).padding(5.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(32.dp).clip(Shapes.pill).background(Color.White)) }
    Value(if (on) "켜짐" else "꺼짐")
}
