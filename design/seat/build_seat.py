"""
운전석 옆모습(벡터) 정의. 여기서 미리보기 SVG와 앱이 쓰는 Kotlin(SeatArt.kt)을 같이 만든다.
  python3 design/seat/build_seat.py            미리보기 design/seat/seat_preview.svg 만 생성
  python3 design/seat/build_seat.py --kotlin   app/.../ui/components/SeatArt.kt 도 다시 생성

좌표는 1000x1000, 시트 앞이 오른쪽. 등받이와 헤드레스트는 똑바로 선 모양으로 그리고
힌지(HINGE)를 중심으로 기본 18도 눕혀서 보여 준다 (앱에서도 같은 방식으로 회전).
"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
HINGE = (394, 688)
BASE_RECLINE = 18

SHELL = ("lg", 0, 250, 0, 800, "#2E3238", "#17191C")      # 뒤판, 하부 쉘
PAD = ("lg", 0, 250, 0, 760, "#C3C8D0", "#A2A8B2")        # 앉는 면
BOLSTER = ("lg", 0, 380, 0, 700, "#989FA9", "#7C838D")    # 옆구리 지지대
METAL = "#8B919A"
STITCH = "#E9EBEE"

def shape(d, fill=None, stroke=None, width=0):
    return {"d": d, "fill": fill, "stroke": stroke, "width": width}

LAYERS = {
 "RAILS": [
  shape("M300 884a300 13 0 1 0 600 0a300 13 0 1 0 -600 0z", "#E4E6EA"),
  shape("M476 764h258l26 90H450z", "#34383F"),
  shape("M352 852h516a11 11 0 0 1 0 22H352a11 11 0 0 1 0-22z", SHELL),
 ],
 "HEAD": [
  # 기둥 없이 등받이 위에 바로 얹힌 얇은 헤드레스트
  shape("M390 160C420 158 442 174 445 202L448 262C449 290 430 304 404 304C378 304 360 290 359 262L357 204C356 178 368 162 390 160z", SHELL),
  shape("M404 170C430 170 443 184 445 204L448 262C449 284 436 298 416 300C424 258 420 208 404 170z", PAD),
 ],
 "BACK": [
  # 뒤판: 얇고 매끈한 일체형
  shape("M364 704C340 600 330 430 350 318C356 288 376 272 400 272C426 272 440 290 444 316C456 420 474 560 462 700z", SHELL),
  # 앉는 면
  shape("M398 286C424 284 438 298 442 320C454 424 478 560 464 692L426 692C436 560 420 410 398 286z", PAD),
  # 어깨에서 허리로 내려오는 옆 지지대
  shape("M440 372C486 410 516 520 506 628C502 664 482 690 462 694C470 590 462 470 440 372z", BOLSTER),
  shape("M446 400C478 446 494 536 488 620", None, STITCH, 4),
 ],
 "CUSHION": [
  shape("M394 716L806 706C808 752 782 778 742 780L446 786C412 786 394 760 394 716z", SHELL),
  shape("M404 668C500 662 640 644 728 634C782 628 816 650 816 684C816 716 792 738 752 740L424 746C398 746 384 726 386 702C388 684 394 670 404 668z", PAD),
  shape("M640 646C700 636 740 632 760 632C800 632 818 656 816 688C814 716 794 736 760 740L668 742C700 720 690 668 640 646z", BOLSTER),
  shape("M424 716C520 712 600 708 668 704", None, STITCH, 4),
  # 등받이 힌지 덮개
  shape("M358 690a36 36 0 1 0 72 0a36 36 0 1 0 -72 0z", SHELL),
  shape("M381 690a13 13 0 1 0 26 0a13 13 0 1 0 -26 0z", METAL),
 ],
}

def svg():
    defs, body, n = [], [], 0
    def paint(s):
        nonlocal n
        f = s["fill"]
        if f is None: fill = "none"
        elif isinstance(f, str): fill = f
        else:
            n += 1
            defs.append(f'<linearGradient id="g{n}" gradientUnits="userSpaceOnUse" x1="{f[1]}" y1="{f[2]}" x2="{f[3]}" y2="{f[4]}"><stop offset="0" stop-color="{f[5]}"/><stop offset="1" stop-color="{f[6]}"/></linearGradient>')
            fill = f"url(#g{n})"
        st = f' stroke="{s["stroke"]}" stroke-width="{s["width"]}" stroke-linecap="round"' if s["stroke"] else ""
        return f'<path d="{s["d"]}" fill="{fill}"{st}/>'
    rot = f'rotate({-BASE_RECLINE} {HINGE[0]} {HINGE[1]})'
    body.append("".join(paint(s) for s in LAYERS["RAILS"]))
    body.append(f'<g transform="{rot}">' + "".join(paint(s) for s in LAYERS["HEAD"] + LAYERS["BACK"]) + "</g>")
    body.append("".join(paint(s) for s in LAYERS["CUSHION"]))
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1000" viewBox="0 0 1000 1000">'
            '<rect width="1000" height="1000" fill="#fff"/><defs>' + "".join(defs) + "</defs>" + "".join(body) + "</svg>")

def kcolor(c): return "Color(0xFF" + c[1:].upper() + ")"

def kotlin():
    out = ['package com.team.firstcar.ui.components', '',
           'import androidx.compose.ui.geometry.Offset', 'import androidx.compose.ui.graphics.Brush',
           'import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Path',
           'import androidx.compose.ui.graphics.SolidColor', 'import androidx.compose.ui.graphics.vector.PathParser', '',
           '/** design/seat/build_seat.py가 만든 파일. 직접 고치지 말고 스크립트를 고친 뒤 다시 생성한다. */',
           'internal class SeatShape(d: String, val fill: Brush?, val stroke: Color? = null, val strokeWidth: Float = 0f) {',
           '    val path: Path = PathParser().parsePathString(d).toPath()', '}', '',
           'private fun lg(x1: Float, y1: Float, x2: Float, y2: Float, a: Long, b: Long): Brush =',
           '    Brush.linearGradient(listOf(Color(a), Color(b)), Offset(x1, y1), Offset(x2, y2))', '',
           '/** 좌표는 1000x1000, 시트 앞이 오른쪽. 등받이와 헤드레스트는 똑바로 선 모양 (그릴 때 힌지 중심으로 눕힌다) */',
           'internal object SeatArt {', '    const val SIZE = 1000f',
           f'    const val HINGE_X = {HINGE[0]}f', f'    const val HINGE_Y = {HINGE[1]}f',
           f'    const val BASE_RECLINE_DEG = {BASE_RECLINE}f', '']
    for name, shapes in LAYERS.items():
        out.append(f'    val {name}: List<SeatShape> by lazy {{ listOf(')
        for s in shapes:
            f = s["fill"]
            if f is None: fill = "null"
            elif isinstance(f, str): fill = f"SolidColor({kcolor(f)})"
            else: fill = f'lg({f[1]}f, {f[2]}f, {f[3]}f, {f[4]}f, 0xFF{f[5][1:].upper()}, 0xFF{f[6][1:].upper()})'
            st = f', {kcolor(s["stroke"])}, {s["width"]}f' if s["stroke"] else ""
            out.append(f'        SeatShape("{s["d"]}", {fill}{st}),')
        out.append('    ) }')
    out.append('}\n')
    return "\n".join(out)

open(os.path.join(HERE, "seat_preview.svg"), "w").write(svg())
if "--kotlin" in sys.argv:
    p = os.path.join(HERE, "../../app/src/main/java/com/team/firstcar/ui/components/SeatArt.kt")
    open(p, "w", encoding="utf-8").write(kotlin())
