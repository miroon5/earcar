package com.team.firstcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.team.firstcar.MainViewModel
import com.team.firstcar.Screen
import com.team.firstcar.UiState
import com.team.firstcar.settings.compare.Comparator
import com.team.firstcar.settings.model.CompareResult
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.transform.ValueFormat
import com.team.firstcar.ui.components.ButtonKind
import com.team.firstcar.ui.components.CircleButton
import com.team.firstcar.ui.components.ColorDot
import com.team.firstcar.ui.components.EmptyState
import com.team.firstcar.ui.components.FilterChips
import com.team.firstcar.ui.components.Glyph
import com.team.firstcar.ui.components.GlyphIcon
import com.team.firstcar.ui.components.IconTile
import com.team.firstcar.ui.components.SelectChip
import com.team.firstcar.ui.components.pressScale
import com.team.firstcar.ui.components.visualFor
import com.team.firstcar.ui.components.PillButton
import com.team.firstcar.ui.components.Spinner
import com.team.firstcar.ui.components.StatusChip
import com.team.firstcar.ui.components.statusLabel
import com.team.firstcar.ui.theme.Dimens
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.Shapes
import com.team.firstcar.ui.theme.Type

@Composable
internal fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = LocalOmni.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CircleButton(Glyph.BACK, "뒤로", onBack)
        Text(title, style = Type.h2, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/**
 * 비교 요약. 스크롤 없이 한 화면에 들어간다.
 * 넓은 화면: 왼쪽에 누르는 것(상태별 숫자, 항목별 보기), 오른쪽에 보는 것(저장한 시트 자세 그림).
 * 좁은 화면: 그림이 위, 버튼이 아래.
 */
@Composable
fun SummaryScreen(ui: UiState, vm: MainViewModel, wide: Boolean) {
    val results = ui.compare
    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                SummaryHeader(ui, vm)
                if (results != null) SummaryButtons(vm, results)
            }
            if (results == null) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Spinner() }
            else SeatPoseCard(ui.profile, results, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            SummaryHeader(ui, vm)
            if (results == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            } else {
                SeatPoseCard(ui.profile, results, Modifier.weight(1.2f).fillMaxWidth())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    SummaryButtons(vm, results)
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(ui: UiState, vm: MainViewModel) {
    Header("비교 결과", vm::back)
    CompareBanner(ui)
}

/** 무엇과 무엇을 비교했는지: 세팅을 저장한 차 → 지금 맞춰 보는 차 */
@Composable
internal fun CompareBanner(ui: UiState) {
    val c = LocalOmni.current
    val from = ui.profile ?: return
    val to = ui.compareCar ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CarBox("세팅을 저장한 차", from.sourceCar.displayName, filled = false, Modifier.weight(1f))
        Text("→", style = Type.h2, color = c.text)
        CarBox("지금 타는 차", to.displayName, filled = true, Modifier.weight(1f))
    }
}

@Composable
private fun CarBox(label: String, name: String, filled: Boolean, modifier: Modifier) {
    val c = LocalOmni.current
    val fg = if (filled) c.onAccent else c.text
    Column(
        modifier.clip(Shapes.tile).background(if (filled) c.accent else c.box).padding(horizontal = 26.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = Type.meta, color = fg, maxLines = 1)
        Text(name, style = Type.bodyL.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ColumnScope.SummaryButtons(vm: MainViewModel, results: List<CompareResult>) {
    StatGrid(results, Modifier) { vm.openDetail(it) }
    Box(Modifier.weight(1f))
    PillButton("항목별로 보기", { vm.openDetail() }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
}

/**
 * 화면에 내보내는 결과는 세 가지뿐이다: 그대로 맞는 것, 비슷하게 맞춘 것, 이 차에 없는 것.
 * 판단할 수 없는 항목과 참고용 항목(시트 위치)은 목록과 숫자에서 뺀다. 시트 위치는 시트 그림으로 보여 준다.
 */
private val SHOWN = listOf(CompareStatus.SAME, CompareStatus.ADAPTED, CompareStatus.UNSUPPORTED)

private fun statTitle(s: CompareStatus) = when (s) {
    CompareStatus.SAME -> "그대로 쓸 수 있어요"
    CompareStatus.ADAPTED -> "비슷하게 쓸 수 있어요"
    else -> "이 차엔 없는 기능이에요"
}

/** 결과 세 줄: 숫자와 쉬운 말. 화면을 다 채우지 않는 적당한 높이. 누르면 그 항목만 모아 보여 준다 */
@Composable
private fun StatGrid(results: List<CompareResult>, modifier: Modifier, onOpen: (CompareStatus) -> Unit) {
    val c = LocalOmni.current
    val s = Comparator.summary(results)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
        SHOWN.forEach { st ->
            val src = remember(st) { MutableInteractionSource() }
            val scale = pressScale(src)
            Row(
                Modifier.height(108.dp).fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(Shapes.tile).background(c.box)
                    .clickable(interactionSource = src, indication = null, role = Role.Button) { onOpen(st) }
                    .padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("${s[st] ?: 0}", style = Type.display, color = c.text, modifier = Modifier.width(76.dp), maxLines = 1)
                Text(statTitle(st), style = Type.title, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                GlyphIcon(Glyph.CHEVRON, c.text, 30.dp)
            }
        }
    }
}

/**
 * 항목별 결과. 필터 목록 없이 결과 카드만 화면 전체에 깐다.
 * 결과 종류(그대로, 비슷하게, 이 차엔 없음)마다 제목 한 줄을 두고 그 아래에 카드를 여러 열로 놓는다.
 * 요약에서 숫자 타일을 눌러 들어오면 그 종류만 보여 준다.
 */
@Composable
fun DetailScreen(ui: UiState, vm: MainViewModel, wide: Boolean) {
    val c = LocalOmni.current
    val results = ui.compare.orEmpty().filter { it.status in SHOWN && (ui.detailFilter == null || it.status == ui.detailFilter) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Header("항목별 결과", vm::back)
        CompareBanner(ui)
        if (results.isEmpty()) {
            EmptyState("해당하는 항목이 없어요", Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(minSize = if (wide) 600.dp else 460.dp), Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SHOWN.forEach { st ->
                    val group = results.filter { it.status == st }
                    if (group.isNotEmpty()) {
                        item(key = "head_$st", span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(statTitle(st), style = Type.h2, color = c.text)
                                Text("${group.size}", style = Type.h2.copy(fontWeight = FontWeight.Normal), color = c.text)
                            }
                        }
                        items(group, key = { it.itemId }) { ResultCard(it) }
                    }
                }
            }
        }
    }
}

/** 항목 카드: 아이콘 + 이름 + 값 변화 + 한 줄 안내 */
@Composable
private fun ResultCard(r: CompareResult) {
    val c = LocalOmni.current
    val item = SettingRegistry.get(r.itemId)
    val labels = item?.enumLabels.orEmpty()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 190.dp).clip(Shapes.box).background(c.box).padding(horizontal = 32.dp, vertical = 30.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        IconTile(visualFor(r.itemId), size = 112.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item?.label ?: r.itemId, style = Type.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.saved != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 어느 쪽 값인지 이름을 붙인다: 저장한 내 세팅 → 이 차에서 맞춘 값
                    Text("내 세팅", style = Type.body, color = c.text)
                    ValueText(r.saved, labels, c.text)
                    if (r.proposed != null && r.status != CompareStatus.SAME) {
                        Text("→", style = Type.body, color = c.text)
                        Text("이 차", style = Type.body, color = c.text)
                        ValueText(r.proposed, labels, c.text)
                    }
                }
            }
            val hint = when (r.status) {
                CompareStatus.SAME, CompareStatus.ADAPTED -> item?.whereToSet
                CompareStatus.UNSUPPORTED -> item?.alternative
                else -> null
            }
            if (hint != null) Text(hint, style = Type.body, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ValueText(v: SettingValue, labels: Map<String, String>, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (v is SettingValue.Color) ColorDot(v.rgb)
        Text(ValueFormat.format(v, labels), style = Type.title, color = color, maxLines = 1)
    }
}
