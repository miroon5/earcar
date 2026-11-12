package com.team.firstcar.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser

/** design/seat/build_seat.py가 만든 파일. 직접 고치지 말고 스크립트를 고친 뒤 다시 생성한다. */
internal class SeatShape(d: String, val fill: Brush?, val stroke: Color? = null, val strokeWidth: Float = 0f) {
    val path: Path = PathParser().parsePathString(d).toPath()
}

private fun lg(x1: Float, y1: Float, x2: Float, y2: Float, a: Long, b: Long): Brush =
    Brush.linearGradient(listOf(Color(a), Color(b)), Offset(x1, y1), Offset(x2, y2))

/** 좌표는 1000x1000, 시트 앞이 오른쪽. 등받이와 헤드레스트는 똑바로 선 모양 (그릴 때 힌지 중심으로 눕힌다) */
internal object SeatArt {
    const val SIZE = 1000f
    const val HINGE_X = 394f
    const val HINGE_Y = 688f
    const val BASE_RECLINE_DEG = 18f

    val RAILS: List<SeatShape> by lazy { listOf(
        SeatShape("M300 884a300 13 0 1 0 600 0a300 13 0 1 0 -600 0z", SolidColor(Color(0xFFE4E6EA))),
        SeatShape("M476 764h258l26 90H450z", SolidColor(Color(0xFF34383F))),
        SeatShape("M352 852h516a11 11 0 0 1 0 22H352a11 11 0 0 1 0-22z", lg(0f, 250f, 0f, 800f, 0xFF2E3238, 0xFF17191C)),
    ) }
    val HEAD: List<SeatShape> by lazy { listOf(
        SeatShape("M390 160C420 158 442 174 445 202L448 262C449 290 430 304 404 304C378 304 360 290 359 262L357 204C356 178 368 162 390 160z", lg(0f, 250f, 0f, 800f, 0xFF2E3238, 0xFF17191C)),
        SeatShape("M404 170C430 170 443 184 445 204L448 262C449 284 436 298 416 300C424 258 420 208 404 170z", lg(0f, 250f, 0f, 760f, 0xFFC3C8D0, 0xFFA2A8B2)),
    ) }
    val BACK: List<SeatShape> by lazy { listOf(
        SeatShape("M364 704C340 600 330 430 350 318C356 288 376 272 400 272C426 272 440 290 444 316C456 420 474 560 462 700z", lg(0f, 250f, 0f, 800f, 0xFF2E3238, 0xFF17191C)),
        SeatShape("M398 286C424 284 438 298 442 320C454 424 478 560 464 692L426 692C436 560 420 410 398 286z", lg(0f, 250f, 0f, 760f, 0xFFC3C8D0, 0xFFA2A8B2)),
        SeatShape("M440 372C486 410 516 520 506 628C502 664 482 690 462 694C470 590 462 470 440 372z", lg(0f, 380f, 0f, 700f, 0xFF989FA9, 0xFF7C838D)),
        SeatShape("M446 400C478 446 494 536 488 620", null, Color(0xFFE9EBEE), 4f),
    ) }
    val CUSHION: List<SeatShape> by lazy { listOf(
        SeatShape("M394 716L806 706C808 752 782 778 742 780L446 786C412 786 394 760 394 716z", lg(0f, 250f, 0f, 800f, 0xFF2E3238, 0xFF17191C)),
        SeatShape("M404 668C500 662 640 644 728 634C782 628 816 650 816 684C816 716 792 738 752 740L424 746C398 746 384 726 386 702C388 684 394 670 404 668z", lg(0f, 250f, 0f, 760f, 0xFFC3C8D0, 0xFFA2A8B2)),
        SeatShape("M640 646C700 636 740 632 760 632C800 632 818 656 816 688C814 716 794 736 760 740L668 742C700 720 690 668 640 646z", lg(0f, 380f, 0f, 700f, 0xFF989FA9, 0xFF7C838D)),
        SeatShape("M424 716C520 712 600 708 668 704", null, Color(0xFFE9EBEE), 4f),
        SeatShape("M358 690a36 36 0 1 0 72 0a36 36 0 1 0 -72 0z", lg(0f, 250f, 0f, 800f, 0xFF2E3238, 0xFF17191C)),
        SeatShape("M381 690a13 13 0 1 0 26 0a13 13 0 1 0 -26 0z", SolidColor(Color(0xFF8B919A))),
    ) }
}
