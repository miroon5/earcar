package com.team.firstcar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.team.firstcar.MainViewModel
import com.team.firstcar.Screen
import com.team.firstcar.UiState
import com.team.firstcar.settings.compare.Comparator
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.ui.components.Toast
import com.team.firstcar.ui.theme.LocalOmni
import com.team.firstcar.ui.theme.OmniTheme
import com.team.firstcar.ui.theme.Type
import kotlinx.coroutines.delay

/**
 * 화면 원칙 (Pleos UI Design Guide)
 * - 주행 중(restricted): 모든 화면을 닫고 한 줄 상태만 보여 준다
 * - 정차 중: 홈, 저장, 비교 요약, 비교 상세, 진단
 * - 폭 900dp 이상(landscape 2 window): 요약과 상세를 나란히
 */
@Composable
fun AppScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val driving by vm.driving.state.collectAsStateWithLifecycle()

    OmniTheme(reduceMotion = ui.reduceMotion || driving.restricted) {
        val c = LocalOmni.current
        BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
            val wide = maxWidth >= 900.dp
            // 차량 화면은 dp 기준으로 매우 크다(밀도 1.0에 1600dp 이상). 고정 크기로 그리면 글자와 버튼이
            // 작아지고 화면이 비므로, 기준 화면(넓은 화면 1350x1050dp, 좁은 화면 폭 640dp)보다 클 때만 그 비율만큼 조금 키운다.
            val scale = (if (wide) minOf(maxWidth / 1350.dp, maxHeight / 1050.dp) else maxWidth / 640.dp).coerceIn(1f, 1.6f)
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
                Box(Modifier.fillMaxSize()) {
                    val pad = if (wide) 28.dp else 24.dp
                    if (driving.restricted) {
                        DrivingLine(ui)
                    } else {
                        BackHandler(enabled = ui.screen != Screen.HOME) { vm.back() }
                        Box(Modifier.fillMaxSize().padding(horizontal = pad, vertical = 24.dp)) {
                            when (ui.screen) {
                                Screen.HOME -> HomeScreen(ui, vm, wide)
                                Screen.SAVING -> SaveScreen(ui, vm, wide)
                                Screen.SUMMARY -> SummaryScreen(ui, vm, wide)
                                Screen.DETAIL -> DetailScreen(ui, vm, wide)
                                Screen.DIAGNOSTICS -> DiagnosticsScreen(ui, vm, driving.connected)
                            }
                        }
                    }

                    ui.message?.let { msg ->
                        LaunchedEffect(msg) { delay(3500); vm.clearMessage() }
                        Toast(msg, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp, start = pad, end = pad))
                    }
                }
            }
        }
    }
}

/** 주행 중 한 줄 상태. 애니메이션, 버튼 없음 */
@Composable
private fun DrivingLine(ui: UiState) {
    val c = LocalOmni.current
    val text = ui.compare?.let { r ->
        val s = Comparator.summary(r)
        "주행 중입니다. 그대로 ${s[CompareStatus.SAME]}, 비슷하게 ${s[CompareStatus.ADAPTED]}, 없는 기능 ${s[CompareStatus.UNSUPPORTED]}"
    } ?: if (ui.profile != null) "주행 중입니다. 정차하면 내 세팅과 비교할 수 있어요" else "주행 중입니다. 정차하면 세팅을 저장할 수 있어요"
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(c.accent))
            Text(text, style = Type.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
