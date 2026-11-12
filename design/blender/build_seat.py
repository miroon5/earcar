"""
FirstCarGuide 운전석 시트 3D 모델 생성 + 렌더 스크립트.

실행 (프로젝트 루트에서):
  /Applications/Blender.app/Contents/MacOS/Blender -b -P design/blender/build_seat.py
  ... -- --preview      낮은 샘플/해상도로 빠르게 확인

결과:
  design/blender/seat.blend                모델 원본 (다시 열어 수정 가능)
  design/blender/renders/seat_hero.png     3/4 뷰 히어로 (투명 배경)
  design/blender/renders/seat_side_*.png   옆모습 레이어 4장 (rails / cushion / back / head)
  design/blender/renders/seat_layers.json  앱에서 레이어를 움직일 때 쓰는 피벗 좌표

좌표계: 시트 앞 = +X, 위 = +Z, 좌우 = Y. 단위 m.
옆모습 레이어는 정사영이라 앱에서 2D 회전/이동만으로 등받이 각도, 앞뒤, 높이, 헤드레스트를 정확히 표현할 수 있다.
"""
import bpy
import bmesh
import json
import math
import os
import sys
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
RENDERS = os.path.join(HERE, "renders")
os.makedirs(RENDERS, exist_ok=True)
PREVIEW = "--preview" in sys.argv

X, Y, Z = Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))

HINGE = Vector((-0.25, 0.0, 0.33))  # 등받이 회전축 (Y축 방향)
RECLINE_DEG = 18.0                   # 기준 자세의 등받이 기울기 (뒤로)
SIDE_SIZE = 640 if PREVIEW else 1280
SIDE_SCALE = 1.6                     # 정사영 폭 (m)
SIDE_CENTER = Vector((-0.08, 0.0, 0.62))


# ---------------------------------------------------------------- scene reset
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene


def collection(name):
    col = bpy.data.collections.new(name)
    scene.collection.children.link(col)
    return col


COL = {n: collection(n) for n in ("RAILS", "CUSHION", "BACK", "HEAD", "STAGE")}


# ---------------------------------------------------------------- materials
def material(name, color, rough, metallic=0.0, coat=0.0, emission=None, bump=0.0, bump_scale=300.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*color, 1)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metallic
    if coat and "Coat Weight" in bsdf.inputs:
        bsdf.inputs["Coat Weight"].default_value = coat
        bsdf.inputs["Coat Roughness"].default_value = 0.25
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission[0], 1)
        bsdf.inputs["Emission Strength"].default_value = emission[1]
    if bump:
        noise = nt.nodes.new("ShaderNodeTexNoise")
        noise.inputs["Scale"].default_value = bump_scale
        noise.inputs["Detail"].default_value = 6
        b = nt.nodes.new("ShaderNodeBump")
        b.inputs["Strength"].default_value = bump
        b.inputs["Distance"].default_value = 0.002
        nt.links.new(noise.outputs["Fac"], b.inputs["Height"])
        nt.links.new(b.outputs["Normal"], bsdf.inputs["Normal"])
    return m


WHITE = (0.85, 0.86, 0.88)  # 포인트는 흰색 (블랙 앤 화이트)
LIME = WHITE
M_LEATHER = material("Leather_Graphite", (0.060, 0.064, 0.072), 0.42, coat=0.15, bump=0.12, bump_scale=420)
M_INSERT = material("Insert_Stone", (0.42, 0.43, 0.45), 0.62, bump=0.25, bump_scale=900)
M_PLASTIC = material("Trim_Plastic", (0.022, 0.024, 0.028), 0.38)
M_METAL = material("Rail_Metal", (0.32, 0.33, 0.35), 0.28, metallic=1.0)
M_CHROME = material("Post_Chrome", (0.8, 0.8, 0.82), 0.12, metallic=1.0)
M_PIPING = material("Piping_White", LIME, 0.4, emission=(LIME, 0.9))


# ---------------------------------------------------------------- mesh helpers
def superellipse(a, e):
    ca, sa = math.cos(a), math.sin(a)
    p = 2.0 / e
    return math.copysign(abs(ca) ** p, ca), math.copysign(abs(sa) ** p, sa)


def end_round(t, k):
    """양 끝 k 구간에서 단면을 원호처럼 줄여 둥근 끝을 만든다."""
    d = min(t, 1.0 - t)
    if k <= 0 or d >= k:
        return 1.0
    return math.sqrt(max(0.0, 1.0 - (1.0 - d / k) ** 2))


def tube(name, col, mat, path, u, v, exp=2.5, k=0.15, n=32, parent=None, subsurf=2):
    """path(t)를 따라 초타원 단면(u(t), v(t) 반축)을 로프트한 부드러운 덩어리."""
    edge = [0.004, 0.012, 0.03, 0.06, 0.1, 0.15, 0.22, 0.3, 0.4]
    ts = sorted(set(edge + [0.5] + [1 - t for t in edge]))
    bm = bmesh.new()
    rings = []
    for t in ts:
        s = end_round(t, k)
        c, uu, vv = path(t), u(t) * s, v(t) * s
        ring = []
        for i in range(n):
            x, y = superellipse(2 * math.pi * i / n, exp)
            ring.append(bm.verts.new(c + uu * x + vv * y))
        rings.append(ring)
    for r0, r1 in zip(rings, rings[1:]):
        for i in range(n):
            bm.faces.new((r0[i], r0[(i + 1) % n], r1[(i + 1) % n], r1[i]))
    for ring, t in ((rings[0], 0.0), (rings[-1], 1.0)):
        cv = bm.verts.new(path(t))
        for i in range(n):
            bm.faces.new((cv, ring[i], ring[(i + 1) % n]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    ob = bpy.data.objects.new(name, me)
    col.objects.link(ob)
    me.materials.append(mat)
    if subsurf:
        mod = ob.modifiers.new("Subsurf", "SUBSURF")
        mod.levels = 1
        mod.render_levels = subsurf
    if parent:
        ob.parent = parent
    return ob


def line(a, b):
    a, b = Vector(a), Vector(b)
    return lambda t: a.lerp(b, t)


def const(vec):
    return lambda t: vec


def empty(name, col, loc, parent=None):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_size = 0.08
    ob.location = loc
    col.objects.link(ob)
    if parent:
        ob.parent = parent
    return ob


def cylinder(name, col, mat, loc, radius, depth, rot=(0, 0, 0), parent=None, bevel=0.0):
    bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=radius, depth=depth, location=loc, rotation=rot)
    ob = bpy.context.active_object
    ob.name = name
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    col.objects.link(ob)
    ob.data.materials.append(mat)
    for p in ob.data.polygons:
        p.use_smooth = True
    if bevel:
        mod = ob.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 4
        mod.limit_method = "ANGLE"
    if parent:
        ob.parent = parent
    return ob


# ---------------------------------------------------------------- seat: rails (고정)
for side in (-1, 1):
    tube(f"Rail_{side}", COL["RAILS"], M_METAL, line((-0.46, side * 0.17, 0.085), (0.44, side * 0.17, 0.085)),
         const(Y * 0.028), const(Z * 0.02), exp=5, k=0.03)
    tube(f"RailFoot_{side}", COL["RAILS"], M_PLASTIC, line((-0.48, side * 0.17, 0.05), (0.46, side * 0.17, 0.05)),
         const(Y * 0.04), const(Z * 0.018), exp=4, k=0.05)

# ---------------------------------------------------------------- seat: cushion (앞뒤, 높이로 움직이는 묶음)
SEAT = empty("SEAT_ROOT", COL["CUSHION"], (0, 0, 0))  # 앞뒤/높이 이동의 루트


def cushion_z(t):
    return 0.30 + 0.045 * t + 0.012 * math.sin(math.pi * t)


def cushion_path(t):
    return Vector((-0.29 + 0.57 * t, 0, cushion_z(t)))


tube("Cushion", COL["CUSHION"], M_LEATHER, cushion_path, const(Y * 0.245), const(Z * 0.068), exp=3.6, k=0.2, parent=SEAT)
for side in (-1, 1):
    tube(f"CushionBolster_{side}", COL["CUSHION"], M_LEATHER,
         lambda t, s=side: Vector((-0.25 + 0.53 * t, s * 0.222, cushion_z(t) + 0.038)),
         const(Y * 0.06), const(Z * 0.062), exp=2.6, k=0.28, parent=SEAT)
    tube(f"CushionPiping_{side}", COL["CUSHION"], M_PIPING,
         lambda t, s=side: Vector((-0.19 + 0.44 * t, s * 0.168, cushion_z(0.1 + 0.8 * t) + 0.069)),
         const(Y * 0.0045), const(Z * 0.0045), exp=2, k=0.02, n=12, parent=SEAT, subsurf=1)
    # 측면 플라스틱 트림 + 리클라이너 커버
    tube(f"Valance_{side}", COL["CUSHION"], M_PLASTIC,
         lambda t, s=side: Vector((-0.33 + 0.57 * t, s * 0.268, 0.255 + 0.02 * t)),
         const(Y * 0.02), lambda t: Z * (0.062 - 0.02 * t), exp=3.5, k=0.16, parent=SEAT)
    cylinder(f"HingeCover_{side}", COL["CUSHION"], M_PLASTIC, (HINGE.x, side * 0.272, HINGE.z), 0.062, 0.03,
             rot=(math.pi / 2, 0, 0), parent=SEAT, bevel=0.008)
    cylinder(f"HingeCap_{side}", COL["CUSHION"], M_METAL, (HINGE.x, side * 0.289, HINGE.z), 0.022, 0.006,
             rot=(math.pi / 2, 0, 0), parent=SEAT, bevel=0.002)
# 방석 가운데 퀼팅 4줄
for i in range(4):
    tc = 0.2 + i * 0.185
    tube(f"CushionRib_{i}", COL["CUSHION"], M_INSERT,
         lambda t, tc=tc: Vector((-0.29 + 0.57 * tc, -0.158 + 0.316 * t, cushion_z(tc) + 0.056)),
         const(X * 0.05), const(Z * 0.024), exp=2.8, k=0.12, parent=SEAT)
# 하부 프레임
tube("UnderShell", COL["CUSHION"], M_PLASTIC, line((-0.31, 0, 0.2), (0.25, 0, 0.215)),
     const(Y * 0.235), const(Z * 0.07), exp=4.5, k=0.12, parent=SEAT)
for side in (-1, 1):
    for x in (-0.2, 0.14):
        tube(f"Riser_{side}_{x}", COL["CUSHION"], M_METAL, line((x, side * 0.17, 0.09), (x, side * 0.17, 0.2)),
             const(X * 0.035), const(Y * 0.02), exp=4, k=0.05, parent=SEAT)

# ---------------------------------------------------------------- seat: backrest (HINGE 기준 회전)
BACK = empty("BACK_PIVOT", COL["BACK"], HINGE, parent=SEAT)


def back_x(t):  # 허리 곡선
    return 0.012 * math.sin(math.pi * min(1.0, t * 1.3))


tube("Backrest", COL["BACK"], M_LEATHER, lambda t: Vector((back_x(t), 0, 0.0 + 0.66 * t)),
     lambda t: Y * (0.25 - 0.045 * t * t), lambda t: X * (0.075 - 0.022 * t), exp=3.6, k=0.16, parent=BACK)
for side in (-1, 1):
    tube(f"BackBolster_{side}", COL["BACK"], M_LEATHER,
         lambda t, s=side: Vector((0.05 + back_x(t) - 0.015 * t, s * (0.218 - 0.03 * t * t), 0.05 + 0.5 * t)),
         const(Y * 0.055), lambda t: X * (0.075 - 0.015 * t), exp=2.6, k=0.3, parent=BACK)
    tube(f"BackPiping_{side}", COL["BACK"], M_PIPING,
         lambda t, s=side: Vector((0.083 + back_x(0.2 + 0.6 * t), s * 0.163, 0.12 + 0.42 * t)),
         const(Y * 0.0045), const(X * 0.0045), exp=2, k=0.02, n=12, parent=BACK, subsurf=1)
# 등받이 가운데 퀼팅 5줄 + 어깨 패널
for i in range(5):
    zc = 0.115 + i * 0.094
    tube(f"BackRib_{i}", COL["BACK"], M_INSERT,
         lambda t, zc=zc: Vector((0.062 + back_x(zc / 0.66) - 0.018 * (zc / 0.66), -0.152 + 0.304 * t, zc)),
         const(Z * 0.046), const(X * 0.024), exp=2.8, k=0.12, parent=BACK)
tube("ShoulderPanel", COL["BACK"], M_LEATHER, lambda t: Vector((0.04, -0.15 + 0.3 * t, 0.585)),
     const(Z * 0.05), const(X * 0.03), exp=2.8, k=0.2, parent=BACK)
tube("BackShell", COL["BACK"], M_PLASTIC, lambda t: Vector((-0.045 - 0.012 * t, 0, 0.04 + 0.56 * t)),
     lambda t: Y * (0.235 - 0.05 * t * t), const(X * 0.035), exp=4, k=0.14, parent=BACK)

# ---------------------------------------------------------------- seat: headrest (등받이 축을 따라 이동)
HEAD = empty("HEAD_SLIDE", COL["HEAD"], (0, 0, 0), parent=BACK)
for side in (-1, 1):
    cylinder(f"HeadPost_{side}", COL["HEAD"], M_CHROME, (0.0, side * 0.065, 0.67), 0.0065, 0.16, parent=HEAD)
tube("Headrest", COL["HEAD"], M_LEATHER, lambda t: Vector((0.012 + 0.03 * t, 0, 0.672 + 0.2 * t)),
     lambda t: Y * (0.135 - 0.015 * t), lambda t: X * (0.06 - 0.008 * t), exp=3.0, k=0.36, parent=HEAD)
tube("HeadrestInsert", COL["HEAD"], M_INSERT, lambda t: Vector((0.066, -0.085 + 0.17 * t, 0.772)),
     const(Z * 0.05), const(X * 0.016), exp=2.8, k=0.2, parent=HEAD)

BACK.rotation_euler = (0, -math.radians(RECLINE_DEG), 0)

# ---------------------------------------------------------------- lights
def area(name, loc, target, power, size, color=(1, 1, 1)):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = power
    data.size = size
    data.color = color
    ob = bpy.data.objects.new(name, data)
    ob.location = loc
    ob.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    COL["STAGE"].objects.link(ob)
    return ob


TARGET = (0, 0, 0.55)
area("Key", (1.6, -2.2, 2.2), TARGET, 120, 2.2, (1.0, 0.97, 0.94))
area("Fill", (2.2, 1.6, 0.8), TARGET, 22, 3.0, (0.85, 0.92, 1.0))
area("Rim", (-1.9, 1.4, 1.9), TARGET, 190, 1.4, (0.82, 0.92, 1.0))
area("RimSide", (-1.6, -2.0, 1.2), TARGET, 60, 1.2, (0.9, 0.95, 1.0))
area("Top", (0.1, 0, 2.6), TARGET, 30, 2.0)
area("LimeKick", (-0.6, 2.4, 0.3), TARGET, 45, 1.0, (0.9, 0.94, 1.0))

world = bpy.data.worlds.new("World")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.01, 0.011, 0.013, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.0
scene.world = world

# ---------------------------------------------------------------- cameras
def camera(name, loc, target, ortho=None, lens=70):
    data = bpy.data.cameras.new(name)
    if ortho:
        data.type = "ORTHO"
        data.ortho_scale = ortho
    else:
        data.lens = lens
    ob = bpy.data.objects.new(name, data)
    ob.location = loc
    ob.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    COL["STAGE"].objects.link(ob)
    return ob


CAM_HERO = camera("Cam_Hero", (2.35, -2.05, 1.25), (-0.04, 0, 0.56), lens=85)
CAM_SIDE = camera("Cam_Side", (SIDE_CENTER.x, -4.0, SIDE_CENTER.z), SIDE_CENTER, ortho=SIDE_SCALE)

# ---------------------------------------------------------------- render settings
scene.render.engine = "CYCLES"
scene.cycles.samples = 24 if PREVIEW else 192
scene.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "METAL"
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    scene.cycles.device = "GPU"
except Exception as e:  # GPU가 없으면 CPU로 렌더
    print("GPU 설정 실패, CPU 사용:", e)
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.image_settings.compression = 90
try:
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
except Exception as e:
    print("뷰 변환 설정 생략:", e)

scene.camera = CAM_HERO
scene.render.resolution_x = 600 if PREVIEW else 1200
scene.render.resolution_y = 700 if PREVIEW else 1400
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "seat.blend"))


def render(path, cam, w, h, visible=None):
    scene.camera = cam
    scene.render.resolution_x, scene.render.resolution_y = w, h
    for name in ("RAILS", "CUSHION", "BACK", "HEAD"):
        COL[name].hide_render = visible is not None and name not in visible
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print("rendered", path)


render(os.path.join(RENDERS, "seat_hero.png"), CAM_HERO, scene.render.resolution_x, scene.render.resolution_y)
for layer in ("RAILS", "CUSHION", "BACK", "HEAD"):
    render(os.path.join(RENDERS, f"seat_side_{layer.lower()}.png"), CAM_SIDE, SIDE_SIZE, SIDE_SIZE, visible=[layer])
render(os.path.join(RENDERS, "seat_side_all.png"), CAM_SIDE, SIDE_SIZE, SIDE_SIZE)


# ---------------------------------------------------------------- pivot metadata (0..1 정규화, 원점 좌상단)
def to_uv(p):
    return [round(0.5 + (p.x - SIDE_CENTER.x) / SIDE_SCALE, 5), round(0.5 - (p.z - SIDE_CENTER.z) / SIDE_SCALE, 5)]


meta = {
    "note": "seat_side_*.png 공통. 좌표는 이미지 폭/높이에 대한 비율, 시트 앞이 오른쪽",
    "metersPerImage": SIDE_SCALE,
    "hinge": to_uv(HINGE),
    "baseReclineDeg": RECLINE_DEG,
    "cushionTiltPivot": to_uv(Vector((-0.2, 0, 0.3))),
}
with open(os.path.join(RENDERS, "seat_layers.json"), "w") as f:
    json.dump(meta, f, indent=2, ensure_ascii=False)

for col in COL.values():
    col.hide_render = False
scene.camera = CAM_HERO
scene.render.resolution_x, scene.render.resolution_y = (600, 700) if PREVIEW else (1200, 1400)
scene.render.filepath = "//renders/seat_hero.png"
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "seat.blend"))
print("DONE")
