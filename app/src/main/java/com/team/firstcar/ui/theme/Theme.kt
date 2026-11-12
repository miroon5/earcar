package com.team.firstcar.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 디자인 토큰. 참고 디자인 명세서(1장 컬러)의 :root 토큰을 그대로 옮기고,
 * 차량 화면 기준(중요 텍스트 5mm 이상, 대비 4.5:1)에 맞춰 글자 크기만 키웠다.
 * 원칙: 블랙 앤 화이트. 색은 쓰지 않고 검정/흰색 면의 대비로만 강조한다 (오류, 열선/통풍 표시만 예외).
 */
@Immutable
data class OmniColors(
    val bg: Color, val box: Color, val boxStrong: Color, val boxSoft: Color,
    val text: Color, val muted: Color, val faint: Color,
    val onStrong: Color, val onStrongMuted: Color, val line: Color,
    val accent: Color, val accentText: Color, val accentSoft: Color, val onAccent: Color,
    val error: Color,
    // 무대(보여 주는 큰 박스) 색. 앱 전체를 라이트로 통일해 흰 면 + 어두운 글자를 쓴다
    val kv: Color = Color(0xFFFFFFFF),
    val onKv: Color = Color(0xFF16181B),
    val onKvMuted: Color = Color(0xFF16181B),
)

val LightOmni = OmniColors(
    bg = Color(0xFFFFFFFF), box = Color(0xFFF0F1F3), boxStrong = Color(0xFF16181B), boxSoft = Color(0xFFF5F6F7),
    // 글자와 아이콘은 회색 없이 검정 하나로 쓴다 (muted, faint도 같은 검정). 구분은 크기와 굵기로 한다
    text = Color(0xFF16181B), muted = Color(0xFF16181B), faint = Color(0xFF16181B),
    onStrong = Color(0xFFF4F5F6), onStrongMuted = Color(0xFFA3A9B0), line = Color(0xFFE3E5E8),
    accent = Color(0xFF16181B), accentText = Color(0xFF16181B), // 블랙 앤 화이트: 강조는 검정 면 + 흰 글자
    accentSoft = Color(0xFFF0F1F3), onAccent = Color(0xFFFFFFFF), error = Color(0xFFD92D3A),
)

val DarkOmni = OmniColors(
    bg = Color(0xFF0A0B0D), box = Color(0xFF16181C), boxStrong = Color(0xFF23262B), boxSoft = Color(0xFF111215),
    text = Color(0xFFF1F2F4), muted = Color(0xFFA9AFB7), faint = Color(0xFF5E646B),
    onStrong = Color(0xFFF4F5F6), onStrongMuted = Color(0xFFA3A9B0), line = Color(0xFF2E3238),
    accent = Color(0xFFF1F2F4), accentText = Color(0xFFF1F2F4), // 어두운 화면에서는 반대로 흰 면 + 검정 글자
    accentSoft = Color(0xFF16181C), onAccent = Color(0xFF0A0B0D), error = Color(0xFFFF6B72),
)

/** 웨이트는 400, 700 두 가지만. Pretendard를 res/font에 넣으면 [Brand]만 바꾸면 된다 */
object Type {
    val Brand: FontFamily = FontFamily.Default
    private val TRACKING = (-0.02).em // 모든 텍스트 자간 -2%
    val display = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 64.sp, letterSpacing = TRACKING)
    val h2 = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 48.sp, letterSpacing = TRACKING)
    val title = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp, letterSpacing = TRACKING)
    val bodyL = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 38.sp, letterSpacing = TRACKING)
    val body = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 35.sp, letterSpacing = TRACKING)
    val caption = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 29.sp, letterSpacing = TRACKING)
    val meta = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = TRACKING)
    val button = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = TRACKING)
    val stat = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 76.sp, lineHeight = 78.sp, letterSpacing = TRACKING)
}

object Shapes {
    val box = RoundedCornerShape(28.dp)   // 카드, 무대
    val tile = RoundedCornerShape(24.dp)  // 큰 액션 타일
    val pill = RoundedCornerShape(999.dp) // 모든 주요 버튼
    val tag = RoundedCornerShape(6.dp)
}

/**
 * 차량 터치 기준. 흔들리는 차 안에서 팔을 뻗어 누르므로 폰(48dp)보다 훨씬 크게 잡는다.
 * 주 버튼 112dp, 보조 컨트롤(칩, 탭, 아이콘 버튼) 96dp 이상, 컨트롤 사이 간격 16dp 이상.
 */
object Dimens {
    val touch = 112.dp
    val touchSmall = 96.dp
    val tile = 128.dp
    val gap = 16.dp
}

/** 모션 토큰 (명세서 5.1) */
object Motion {
    val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)   // 기본: easeOutQuint 계열
    val Rise = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)   // 스크롤 리빌
    val Frame = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)   // 코너 프레임
    const val RISE_STAGGER_MS = 180
    const val MICRO_MS = 150
}

val LocalOmni = staticCompositionLocalOf { LightOmni }
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun OmniTheme(reduceMotion: Boolean, content: @Composable () -> Unit) {
    // 시스템 다크 설정과 상관없이 항상 라이트로 그린다 (화면마다 밝기가 섞이지 않게)
    val c = LightOmni
    val scheme = lightColorScheme(background = c.bg, surface = c.bg, onBackground = c.text, onSurface = c.text, primary = c.accent, onPrimary = c.onAccent, error = c.error)
    CompositionLocalProvider(LocalOmni provides c, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
