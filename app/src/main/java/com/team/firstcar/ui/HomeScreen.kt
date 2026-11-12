package com.team.firstcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.team.firstcar.MainViewModel
import com.team.firstcar.Screen
import com.team.firstcar.UiState
import com.team.firstcar.ui.components.ActionTile
import com.team.firstcar.ui.components.ButtonKind
import com.team.firstcar.ui.components.KvStage
import com.team.firstcar.ui.components.OmniCard
import com.team.firstcar.ui.components.PillButton
import com.team.firstcar.ui.components.SeatPose
import com.team.firstcar.ui.components.SeatVisual
import com.team.firstcar.ui.theme.Dimens
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.Shapes
import com.team.firstcar.ui.theme.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(ui: UiState, vm: MainViewModel, wide: Boolean) {
    if (wide) {
        // 왼쪽은 누르는 것(버튼), 오른쪽은 보는 것. 버튼은 적당한 높이로 위에 모으고 나머지는 비워 둔다
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                Actions(ui, vm, tile = Modifier.height(168.dp), large = true)
                Box(Modifier.weight(1f))
                DiagnosticsButton(vm)
            }
            Hero(ui, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Hero(ui, Modifier.fillMaxWidth().height(520.dp))
            Actions(ui, vm, tile = Modifier, large = false)
            DiagnosticsButton(vm)
        }
    }
}

@Composable
private fun DiagnosticsButton(vm: MainViewModel) {
    PillButton("진단", { vm.open(Screen.DIAGNOSTICS) }, kind = ButtonKind.OUTLINE, height = Dimens.touchSmall,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "진단과 차량 전환" })
}

/** 흰 무대: 위에 시트 그림, 아래에 지금 차 이름과 저장 현황 타일 */
@Composable
private fun Hero(ui: UiState, modifier: Modifier) {
    val c = LocalOmni.current
    KvStage(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SeatVisual(SeatPose(), Modifier.fillMaxWidth().weight(1f))
            ui.currentCar?.let {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("지금 차량", style = Type.meta, color = c.onKv)
                    Text(it.displayName, style = Type.display, color = c.onKv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ui.profile?.let { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    InfoTile("저장한 세팅", "${p.readCount}개", Modifier.weight(1f))
                    InfoTile("저장한 날", fmt(p.savedAt), Modifier.weight(1.3f))
                }
            }
        }
    }
}

/** 작은 이름 위에 큰 값 */
@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier) {
    val c = LocalOmni.current
    Column(modifier.clip(Shapes.tile).background(c.box).padding(horizontal = 26.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = Type.meta, color = c.text, maxLines = 1)
        Text(value, style = Type.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** [tile]은 큰 타일 두 개에만 붙는 Modifier (넓은 화면에서는 weight로 남는 높이를 나눠 갖는다) */
@Composable
private fun ColumnScope.Actions(ui: UiState, vm: MainViewModel, tile: Modifier, large: Boolean) {
    val c = LocalOmni.current

    if (ui.firstRidePrompt) {
        OmniCard(Modifier.fillMaxWidth()) {
            Text("처음 타는 차입니다", style = Type.h2, color = c.text)
            Text("내 세팅을 이 차에서도 쓸 수 있는지 비교해 볼까요?", style = Type.bodyL, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap), modifier = Modifier.padding(top = 10.dp)) {
                PillButton("비교하기", vm::compare, Modifier.weight(1.4f))
                PillButton("다음에", vm::dismissFirstRide, Modifier.weight(1f), kind = ButtonKind.OUTLINE)
            }
        }
    }

    if (!ui.isDemoTarget) {
        ActionTile("내 세팅 저장하기", null, vm::save,
            tile.fillMaxWidth(), primary = !ui.firstRidePrompt, large = large)
    }
    ActionTile(
        "이 차와 비교하기",
        if (ui.profile == null) "먼저 세팅을 저장해 주세요" else null,
        vm::compare, tile.fillMaxWidth(),
        primary = ui.isDemoTarget && !ui.firstRidePrompt, enabled = ui.profile != null, large = large,
    )
    ui.vehicleError?.let {
        OmniCard(Modifier.fillMaxWidth()) {
            Text("차량 연결을 확인해 주세요", style = Type.title, color = c.error)
            Text(it, style = Type.caption, color = c.muted)
        }
    }
}

internal fun fmt(t: Long): String = SimpleDateFormat("M.d HH:mm", Locale.KOREA).format(Date(t))
