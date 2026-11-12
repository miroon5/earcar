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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.team.firstcar.MainViewModel
import com.team.firstcar.UiState
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.reader.ApplyResult
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.transform.ValueFormat
import com.team.firstcar.ui.components.ButtonKind
import com.team.firstcar.ui.components.ColorDot
import com.team.firstcar.ui.components.IconTile
import com.team.firstcar.ui.components.PillButton
import com.team.firstcar.ui.components.Spinner
import com.team.firstcar.ui.components.visualFor
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.LocalReduceMotion
import com.team.firstcar.ui.theme.Motion
import com.team.firstcar.ui.theme.Shapes
import com.team.firstcar.ui.theme.Type

/**
 * 적용 진행과 결과 (F4). 저장 화면과 같은 틀이다.
 * 왼쪽: 적용이 끝났다는 확인 (체크 표시, "세팅 N개를 적용했어요", 홈으로).
 * 오른쪽: 이 차에 적용한 설정(이전 값 → 적용한 값이 위에서부터 차례로 바뀐다), 그리고 차에서 직접 바꿔야 하는 설정과 그 위치.
 */
@Composable
fun ApplyScreen(ui: UiState, vm: MainViewModel, wide: Boolean) {
    val result = ui.applyResult
    if (ui.applying || result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 88.dp) }
        return
    }
    val applied = result.filter { it.applied }
    val manual = result.filter { !it.applied }
    val car = ui.compareCar?.displayName
    val title = if (applied.isNotEmpty()) "세팅 ${applied.size}개를 적용했어요" else "적용한 세팅이 없어요"

    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    DoneMark(applied.isNotEmpty(), title, car, big = true)
                }
                PillButton("홈으로", { vm.back(); vm.back() }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
            }
            ApplyList(applied, manual, Modifier.weight(1.1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DoneMark(applied.isNotEmpty(), title, car, big = false)
            }
            ApplyList(applied, manual, Modifier.weight(1f).fillMaxWidth())
            PillButton("홈으로", { vm.back(); vm.back() }, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
        }
    }
}

@Composable
private fun ApplyList(applied: List<ApplyResult>, manual: List<ApplyResult>, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(40.dp)) {
        if (applied.isNotEmpty()) item(key = "applied") { Section("이 차에 적용했어요", applied, manual = false) }
        if (manual.isNotEmpty()) item(key = "manual") { Section("차에서 직접 바꿔 주세요", manual, manual = true) }
    }
}

@Composable
private fun Section(title: String, rows: List<ApplyResult>, manual: Boolean) {
    val c = LocalOmni.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = Type.h2, color = c.text, maxLines = 1)
            Text("${rows.size}", style = Type.h2.copy(fontWeight = FontWeight.Normal), color = c.text)
        }
        Column(Modifier.fillMaxWidth().clip(Shapes.box).background(c.box)) {
            rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.padding(start = 112.dp, end = 28.dp).fillMaxWidth().height(1.dp).background(c.line))
                val item = SettingRegistry.get(r.itemId)
                val labels = item?.enumLabels.orEmpty()
                // 적용한 항목은 이전 값이 먼저 보이고, 위에서부터 차례로 "→ 적용한 값"이 나타난다
                val before = r.before?.takeIf { !manual && it != r.value }
                val reduce = LocalReduceMotion.current
                var shown by remember(r.itemId) { mutableStateOf(manual || reduce) }
                LaunchedEffect(r.itemId) { if (!shown) { delay(500L + i * 260L); shown = true } }
                val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(420, easing = Motion.Ease), label = "reveal")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 116.dp).padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    IconTile(visualFor(r.itemId), size = 60.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item?.label ?: r.itemId, style = Type.bodyL, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // 직접 바꿔야 하는 설정은 차 안 어디에서 바꾸는지 알려 준다
                        if (manual) item?.whereToSet?.let { Text(it, style = Type.caption, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (before != null) {
                        Text(ValueFormat.format(before, labels), style = Type.body, color = c.text, maxLines = 1)
                        Text("→", style = Type.body, color = c.text, modifier = Modifier.graphicsLayer { alpha = reveal })
                    }
                    Row(
                        Modifier.graphicsLayer { alpha = reveal; translationX = (1f - reveal) * 24f },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        (r.value as? SettingValue.Color)?.let { ColorDot(it.rgb, size = 44.dp) }
                        Text(ValueFormat.format(r.value, labels), style = Type.title, color = c.text, maxLines = 1)
                    }
                }
            }
        }
    }
}
