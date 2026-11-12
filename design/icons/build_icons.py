"""아이콘 정의 하나에서 미리보기 SVG와 Android VectorDrawable XML을 같이 만든다. 24 그리드."""
import os, sys
SW = 1.6
# (kind, pathData)  kind: s=선, f=반투명 면(듀오톤), d=흰 점
ICONS = {
 "climate": [
  ("s", "M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2.5"),
  ("s", "M9.6 4.6A2 2 0 1 1 11 8H2.5"),
  ("s", "M12.6 19.4A2 2 0 1 0 14 16H2.5"),
 ],
 "seat": [
  ("f", "M8 2.4h2.8a1.7 1.7 0 0 1 1.7 1.7v1a1.7 1.7 0 0 1-1.7 1.7H8a1.7 1.7 0 0 1-1.7-1.7v-1A1.7 1.7 0 0 1 8 2.4z"),
  ("s", "M8 2.4h2.8a1.7 1.7 0 0 1 1.7 1.7v1a1.7 1.7 0 0 1-1.7 1.7H8a1.7 1.7 0 0 1-1.7-1.7v-1A1.7 1.7 0 0 1 8 2.4z"),
  ("f", "M6.6 10.2l1.8 6.6a2.4 2.4 0 0 0 2.3 1.8h8.1a2.3 2.3 0 0 0 0-4.6h-5.4l-1.1-4.6a2.9 2.9 0 0 0-5.7.8z"),
  ("s", "M6.6 10.2l1.8 6.6a2.4 2.4 0 0 0 2.3 1.8h8.1a2.3 2.3 0 0 0 0-4.6h-5.4l-1.1-4.6a2.9 2.9 0 0 0-5.7.8z"),
  ("s", "M7.5 21.8h13"),
 ],
 "wheel": [
  ("s", "M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0-19z"),
  ("f", "M12 9.2a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6z"),
  ("s", "M12 9.2a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6z"),
  ("s", "M2.9 10.6l6.4.7"), ("s", "M21.1 10.6l-6.4.7"), ("s", "M12 14.8v6.6"),
 ],
 "mirror": [
  ("f", "M7.5 4.5h7.5a5 5 0 0 1 5 5v2a5 5 0 0 1-5 5H9.5a5.5 5.5 0 0 1-5.5-5.5V8a3.5 3.5 0 0 1 3.5-3.5z"),
  ("s", "M7.5 4.5h7.5a5 5 0 0 1 5 5v2a5 5 0 0 1-5 5H9.5a5.5 5.5 0 0 1-5.5-5.5V8a3.5 3.5 0 0 1 3.5-3.5z"),
  ("s", "M13.5 16.5v1.5a2 2 0 0 0 2 2h5"),
  ("s", "M8 10.5l2.5-2.5"), ("s", "M9.5 13l5-5"),
 ],
 "light": [
  ("f", "M15 14c.2-1 .7-1.7 1.5-2.5c1-.9 1.5-2.2 1.5-3.5a6 6 0 0 0-12 0c0 1 .2 2.2 1.5 3.5c.7.7 1.3 1.5 1.5 2.5z"),
  ("s", "M15 14c.2-1 .7-1.7 1.5-2.5c1-.9 1.5-2.2 1.5-3.5a6 6 0 0 0-12 0c0 1 .2 2.2 1.5 3.5c.7.7 1.3 1.5 1.5 2.5"),
  ("s", "M9 18h6"), ("s", "M10.2 21.5h3.6"),
 ],
 "display": [
  ("f", "M4.5 4.5h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-15a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z"),
  ("s", "M4.5 4.5h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-15a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z"),
  ("s", "M8.5 21h7"), ("s", "M6.5 9h4.5"), ("s", "M6.5 13h8"),
 ],
 "drive": [
  ("s", "M4.2 18.5a9.5 9.5 0 1 1 15.6 0"),
  ("f", "M12 11.4a2.1 2.1 0 1 0 0 4.2a2.1 2.1 0 1 0 0-4.2z"),
  ("s", "M12 11.4a2.1 2.1 0 1 0 0 4.2a2.1 2.1 0 1 0 0-4.2z"),
  ("s", "M13.4 12l3.4-4.2"),
  ("s", "M6 13.5h1.2"), ("s", "M12 6.6v1.2"),
 ],
 "thermo": [("s", "M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0z"), ("s", "M12 9v8")],
 "fan": [
  ("s", "M10.827 16.379a6.082 6.082 0 0 1-8.618-7.002l5.412 1.45a6.082 6.082 0 0 1 7.002-8.618l-1.45 5.412a6.082 6.082 0 0 1 8.618 7.002l-5.412-1.45a6.082 6.082 0 0 1-7.002 8.618l1.45-5.412z"),
  ("s", "M12 12v.01"),
 ],
 "snow": [("s", "M2 12h20"), ("s", "M12 2v20"), ("s", "M20 16l-4-4l4-4"), ("s", "M4 8l4 4l-4 4"),
          ("s", "M16 4l-4 4l-4-4"), ("s", "M8 20l4-4l4 4")],
 "recirc": [("s", "M3 12a9 9 0 0 1 9-9a9.75 9.75 0 0 1 6.74 2.74L21 8"), ("s", "M21 3v5h-5"),
            ("s", "M21 12a9 9 0 0 1-9 9a9.75 9.75 0 0 1-6.74-2.74L3 16"), ("s", "M8 16H3v5")],
 "sparkle": [("s", "M11 4l2 5.4l5.4 2l-5.4 2l-2 5.4l-2-5.4l-5.4-2l5.4-2z"), ("s", "M19 3v4"), ("s", "M17 5h4"),
             ("s", "M19.5 17v3"), ("s", "M18 18.5h3")],
 "sun": [("s", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z"), ("s", "M12 2v2"), ("s", "M12 20v2"), ("s", "M4.93 4.93l1.41 1.41"),
         ("s", "M17.66 17.66l1.41 1.41"), ("s", "M2 12h2"), ("s", "M20 12h2"), ("s", "M6.34 17.66l-1.41 1.41"),
         ("s", "M19.07 4.93l-1.41 1.41")],
 "volume": [("s", "M11 5L6 9H3v6h3l5 4z"), ("s", "M15.5 8.5a5 5 0 0 1 0 7"), ("s", "M18.5 5.5a9 9 0 0 1 0 13")],
 "convenience": [
  ("s", "M3.5 7h8.3"), ("s", "M17.8 7h2.7"),
  ("f", "M14.8 4.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z"),
  ("s", "M14.8 4.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z"),
  ("s", "M3.5 17h2.7"), ("s", "M12.2 17h8.3"),
  ("f", "M9.2 14.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z"),
  ("s", "M9.2 14.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z"),
 ],
}

def svg_paths(paths):
    out = []
    for k, d in paths:
        if k == "s":
            out.append(f'<path d="{d}" fill="none" stroke="#fff" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round"/>')
        else:
            out.append(f'<path d="{d}" fill="#fff" fill-opacity="0.2"/>')
    return "".join(out)

def sheet():
    cell = 160; cols = 4; rows = 2
    body = [f'<rect width="{cols*cell}" height="{cols*cell}" fill="#000"/>']
    for i, (name, paths) in enumerate(ICONS.items()):
        x, y = (i % cols) * cell, (i // cols) * cell
        body.append(f'<rect x="{x+12}" y="{y+12}" width="{cell-24}" height="{cell-24}" rx="28" fill="#1c1e22"/>')
        body.append(f'<g transform="translate({x+40},{y+40}) scale({80/24})">{svg_paths(paths)}</g>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols*cell}" height="{cols*cell}" viewBox="0 0 {cols*cell} {cols*cell}">{"".join(body)}</svg>'

def vd(paths):
    out = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="48dp" android:height="48dp" android:viewportWidth="24" android:viewportHeight="24">']
    for k, d in paths:
        if k == "s":
            out.append(f'    <path android:pathData="{d}" android:strokeColor="#FFFFFFFF" android:strokeWidth="{SW}"\n        android:strokeLineCap="round" android:strokeLineJoin="round" />')
        else:
            out.append(f'    <path android:pathData="{d}" android:fillColor="#33FFFFFF" />')
    out.append('</vector>\n')
    return "\n".join(out)

# 색을 흰색 하나로 통일: 반투명 면(f)은 쓰지 않고 선만 남긴다
ICONS = {name: [p for p in paths if p[0] == "s"] for name, paths in ICONS.items()}

open("sheet.svg", "w").write(sheet())
if len(sys.argv) > 1:
    for name, paths in ICONS.items():
        open(os.path.join(sys.argv[1], f"ic_{name}.xml"), "w").write(vd(paths))
