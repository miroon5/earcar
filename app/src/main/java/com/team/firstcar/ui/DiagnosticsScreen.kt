package com.team.firstcar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.firstcar.MainViewModel
import com.team.firstcar.UiState
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.transform.ValueFormat
import com.team.firstcar.ui.components.ButtonKind
import com.team.firstcar.ui.components.Divider
import com.team.firstcar.ui.components.OmniCard
import com.team.firstcar.ui.components.PillButton
import com.team.firstcar.ui.components.SectionTitle
import com.team.firstcar.ui.components.SelectChip
import com.team.firstcar.ui.components.Spinner
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.Type

/**
 * 진단 (개발과 PT 장애 대응용).
 * - 비교 대상 차량 전환: 현재 차량(실제 SDK) / 데모 차 B, C (시연용 가상 차량)
 * - 항목별 읽기 결과: 실제로 찾은 메서드 이름, 원시 값, capability, 실패 이유 -> 11/7 게이트 결과표
 * - API 목록: 도메인 클래스의 실제 메서드 이름 (이름 후보가 틀렸을 때 SettingRegistry를 고치는 근거)
 * - 내 데이터 지우기, 첫 탑승 기록 초기화 (시연 반복용)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(ui: UiState, vm: MainViewModel, carServiceConnected: Boolean) {
    val c = LocalOmni.current
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Header("진단", vm::back) }

        item {
            OmniCard(Modifier.fillMaxWidth()) {
                Text("연결 상태", style = Type.title, color = c.text)
                Text("Vehicle SDK: ${ui.vehicleError ?: "초기화됨"}", style = Type.body, color = c.muted)
                Text("주행 규제(CarUxRestrictions): ${if (carServiceConnected) "연결됨" else "연결 안 됨"}", style = Type.body, color = c.muted)
                Text("현재 차량: ${ui.currentCar?.displayName ?: "-"}", style = Type.body, color = c.muted)
            }
        }

        item { SectionTitle("비교 대상 차량") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ui.targets.forEach { t -> SelectChip(t.title, t.id == ui.targetId) { vm.selectTarget(t.id) } }
            }
        }
        item {
            Text(ui.target?.description ?: "",
                style = Type.caption, color = c.muted)
        }

        item { SectionTitle("읽기 진단") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ui.diagnosing) Spinner(size = 56.dp)
                PillButton("모든 항목 읽기", vm::runDiagnostics, Modifier.weight(1f),
                    kind = ButtonKind.OUTLINE, enabled = !ui.diagnosing)
                PillButton("API 목록", vm::loadCatalog, Modifier.weight(1f), kind = ButtonKind.OUTLINE)
            }
        }
        if (ui.diagnostics.isNotEmpty()) {
            item {
                val ok = ui.diagnostics.count { it.value != null }
                Text("읽힘 $ok / 못 읽음 ${ui.diagnostics.size - ok}, Logcat 태그 FirstCar에도 출력", style = Type.body, color = c.text)
            }
            items(ui.diagnostics, key = { it.itemId }) { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text((SettingRegistry.get(r.itemId)?.label ?: r.itemId) + if (r.value != null) "" else " (못 읽음)",
                        style = Type.title, color = if (r.value != null) c.text else c.muted)
                    Mono("api: ${r.api}")
                    r.value?.let { Mono("값: ${ValueFormat.format(it)} (raw ${r.rawValue})") }
                    r.capability?.let { Mono("cap: supported=${it.supported} min=${it.min} max=${it.max} step=${it.step} allowed=${it.allowed}") }
                    r.rawCapability?.let { Mono("cap raw: $it") }
                    r.failure?.let { Mono("실패: $it") }
                }
                Divider()
            }
        }
        if (ui.catalog.isNotEmpty()) {
            item { SectionTitle("API 목록") }
            ui.catalog.forEach { (domain, fns) ->
                item(key = "cat_$domain") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(domain, style = Type.title, color = c.text)
                        fns.forEach { Mono(it) }
                    }
                }
            }
        }

        item { SectionTitle("데이터") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("내 데이터 지우기", vm::deleteMyData, Modifier.weight(1f), kind = ButtonKind.OUTLINE)
                PillButton("첫 탑승 기록 초기화", vm::resetFirstRide, Modifier.weight(1f), kind = ButtonKind.OUTLINE)
            }
        }
        item { Text("공유 차량에서는 내리기 전에 '내 데이터 지우기'로 저장된 세팅을 지워 주세요.", style = Type.caption, color = c.muted) }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, style = Type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp), color = LocalOmni.current.muted)
}
