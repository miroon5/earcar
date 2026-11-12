"""
FirstCarGuide 항목별 3D 아이콘 생성 + 렌더 스크립트 (시트와 같은 재질, 흰색 포인트).

실행 (프로젝트 루트에서):
  /Applications/Blender.app/Contents/MacOS/Blender -b -P design/blender/build_icons.py
  ... -- --preview      낮은 샘플/해상도로 빠르게 확인

결과:
  design/blender/icons.blend                 모델 원본 (오브젝트마다 컬렉션 하나)
  design/blender/renders/icon_wheel.png      핸들. 정면 정사영이라 앱에서 2D로 돌려 로딩 스피너로 쓴다
  design/blender/renders/icon_fan.png        송풍 팬 (공조)
  design/blender/renders/icon_light.png      조명
  design/blender/renders/icon_dial.png       다이얼 (주행, 표시, 편의)
  design/blender/renders/icon_mirror.png     사이드미러 (외관)
"""
import bpy
import math
import os
import sys
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
RENDERS = os.path.join(HERE, "renders")
os.makedirs(RENDERS, exist_ok=True)
PREVIEW = "--preview" in sys.argv
SIZE = 256 if PREVIEW else 640

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene


def collection(name):
    col = bpy.data.collections.new(name)
    scene.collection.children.link(col)
    return col


NAMES = ("wheel", "fan", "light", "dial", "mirror")
COL = {n: collection(n.upper()) for n in NAMES}
STAGE = collection("STAGE")


def material(name, color, rough, metallic=0.0, emission=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*color, 1)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metallic
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission[0], 1)
        bsdf.inputs["Emission Strength"].default_value = emission[1]
    return m


LIME = (0.85, 0.86, 0.88)
M_GRAPHITE = material("Graphite", (0.075, 0.08, 0.09), 0.4)
M_STONE = material("Stone", (0.42, 0.43, 0.45), 0.55)
M_PLASTIC = material("Plastic", (0.03, 0.032, 0.036), 0.35)
M_METAL = material("Metal", (0.5, 0.51, 0.53), 0.25, metallic=1.0)
M_GLASS = material("MirrorGlass", (0.55, 0.62, 0.7), 0.32, metallic=0.6, emission=((0.3, 0.4, 0.5), 0.25))
M_LIME = material("Lime", LIME, 0.4, emission=(LIME, 0.9))
M_GLOW = material("Glow", (1.0, 0.98, 0.94), 0.3, emission=((1.0, 0.98, 0.94), 1.1))


def finish(ob, col, mat, parent=None, bevel=0.0, subsurf=0, smooth=True):
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    col.objects.link(ob)
    ob.data.materials.append(mat)
    if smooth:
        for p in ob.data.polygons:
            p.use_smooth = True
    if bevel:
        mod = ob.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 4
        mod.limit_method = "ANGLE"
    if subsurf:
        mod = ob.modifiers.new("Subsurf", "SUBSURF")
        mod.levels = 1
        mod.render_levels = subsurf
    if parent:
        ob.parent = parent
    return ob


def torus(col, mat, major, minor, loc=(0, 0, 0), rot=(math.pi / 2, 0, 0), parent=None):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=128, minor_segments=32,
                                     location=loc, rotation=rot)
    return finish(bpy.context.active_object, col, mat, parent)


def cyl(col, mat, radius, depth, loc=(0, 0, 0), rot=(math.pi / 2, 0, 0), bevel=0.0, parent=None):
    bpy.ops.mesh.primitive_cylinder_add(vertices=96, radius=radius, depth=depth, location=loc, rotation=rot)
    return finish(bpy.context.active_object, col, mat, parent, bevel=bevel)


def box(col, mat, size, loc=(0, 0, 0), rot=(0, 0, 0), bevel=0.0, subsurf=0, parent=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    ob = bpy.context.active_object
    ob.scale = size
    return finish(ob, col, mat, parent, bevel=bevel, subsurf=subsurf, smooth=bool(subsurf or bevel))


def ball(col, mat, scale, loc=(0, 0, 0), parent=None):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1, location=loc)
    ob = bpy.context.active_object
    ob.scale = scale
    return finish(ob, col, mat, parent)


def pivot(col, rot_y):
    ob = bpy.data.objects.new("Pivot", None)
    ob.rotation_euler = (0, rot_y, 0)
    col.objects.link(ob)
    return ob


# ---------------------------------------------------------------- wheel (정면 = -Y)
c = COL["wheel"]
torus(c, M_GRAPHITE, 0.2, 0.024)
cyl(c, M_PLASTIC, 0.062, 0.05, bevel=0.012)
cyl(c, M_METAL, 0.022, 0.056, bevel=0.004)
for ang in (0, 180, 270):  # 좌우 + 아래 스포크
    p = pivot(c, -math.radians(ang))
    box(c, M_PLASTIC, (0.15, 0.03, 0.04), loc=(0.125, 0, 0), bevel=0.01, parent=p)
# 12시 방향 라임 마커: 돌아가는 것이 보이도록
p = pivot(c, -math.radians(90))
box(c, M_LIME, (0.02, 0.052, 0.03), loc=(0.2, 0, 0), bevel=0.006, parent=p)

# ---------------------------------------------------------------- fan
c = COL["fan"]
torus(c, M_GRAPHITE, 0.2, 0.02)
torus(c, M_LIME, 0.172, 0.004, loc=(0, -0.012, 0))
cyl(c, M_PLASTIC, 0.05, 0.06, bevel=0.012)
cyl(c, M_METAL, 0.018, 0.066, bevel=0.004)
for i in range(7):
    p = pivot(c, 2 * math.pi * i / 7)
    box(c, M_STONE, (0.115, 0.006, 0.062), loc=(0.108, 0, 0), rot=(math.radians(32), 0, 0), bevel=0.003, subsurf=2, parent=p)

# ---------------------------------------------------------------- light
c = COL["light"]
ball(c, M_GLOW, (0.13, 0.13, 0.13), loc=(0, 0, 0.055))
cyl(c, M_GLOW, 0.085, 0.07, loc=(0, 0, -0.075), rot=(0, 0, 0))
for i in range(3):
    torus(c, M_METAL, 0.066, 0.011, loc=(0, 0, -0.118 - i * 0.021), rot=(0, 0, 0))
cyl(c, M_PLASTIC, 0.04, 0.03, loc=(0, 0, -0.185), rot=(0, 0, 0), bevel=0.01)

# ---------------------------------------------------------------- dial
c = COL["dial"]
torus(c, M_METAL, 0.19, 0.014, loc=(0, 0.03, 0))
cyl(c, M_PLASTIC, 0.2, 0.03, loc=(0, 0.045, 0), bevel=0.008)
cyl(c, M_GRAPHITE, 0.15, 0.11, loc=(0, -0.02, 0), bevel=0.02)
cyl(c, M_STONE, 0.105, 0.012, loc=(0, -0.076, 0), bevel=0.004)
box(c, M_LIME, (0.016, 0.014, 0.07), loc=(0, -0.08, 0.1), bevel=0.004)
for i in range(9):  # 눈금
    a = math.radians(-120 + i * 30)
    box(c, M_METAL, (0.007, 0.006, 0.02), loc=(0.176 * math.sin(a), 0.028, 0.176 * math.cos(a)), rot=(0, a, 0))

# ---------------------------------------------------------------- mirror
c = COL["mirror"]
ball(c, M_GRAPHITE, (0.2, 0.075, 0.13))
ball(c, M_GLASS, (0.168, 0.03, 0.102), loc=(0, -0.058, 0))
box(c, M_PLASTIC, (0.12, 0.05, 0.035), loc=(0.17, 0.01, -0.115), rot=(0, math.radians(-28), 0), bevel=0.012)
box(c, M_LIME, (0.07, 0.008, 0.008), loc=(-0.09, -0.071, -0.072), bevel=0.003)


# ---------------------------------------------------------------- lights, cameras
def area(name, loc, power, size, color=(1, 1, 1)):
    data = bpy.data.lights.new(name, "AREA")
    data.energy, data.size, data.color = power, size, color
    ob = bpy.data.objects.new(name, data)
    ob.location = loc
    ob.rotation_euler = (-Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    STAGE.objects.link(ob)


area("Key", (-1.2, -2.2, 1.8), 140, 2.0, (1.0, 0.97, 0.94))
area("Fill", (1.8, -1.4, 0.2), 30, 2.5, (0.85, 0.92, 1.0))
area("Rim", (1.4, 1.6, 1.6), 200, 1.4, (0.82, 0.92, 1.0))
area("Top", (0, -0.4, 2.4), 40, 2.0)
area("LimeKick", (-1.8, 1.0, -0.4), 50, 1.0, (0.9, 0.94, 1.0))

world = bpy.data.worlds.new("World")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.012, 0.013, 0.015, 1)
scene.world = world


def camera(name, loc):
    data = bpy.data.cameras.new(name)
    data.type = "ORTHO"
    data.ortho_scale = 0.54
    ob = bpy.data.objects.new(name, data)
    ob.location = loc
    ob.rotation_euler = (-Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    STAGE.objects.link(ob)
    return ob


CAM_FRONT = camera("Cam_Front", (0, -4, 0))
CAM_TILT = camera("Cam_Tilt", (-1.5, -3.5, 1.3))

scene.render.engine = "CYCLES"
scene.cycles.samples = 24 if PREVIEW else 160
scene.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "METAL"
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    scene.cycles.device = "GPU"
except Exception as e:
    print("GPU 설정 실패, CPU 사용:", e)
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.resolution_x = scene.render.resolution_y = SIZE
try:
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
except Exception as e:
    print("뷰 변환 설정 생략:", e)

for name in NAMES:
    for n in NAMES:
        COL[n].hide_render = n != name
    scene.camera = CAM_FRONT if name == "wheel" else CAM_TILT
    scene.render.filepath = os.path.join(RENDERS, f"icon_{name}.png")
    bpy.ops.render.render(write_still=True)

for n in NAMES:
    COL[n].hide_render = False
scene.camera = CAM_TILT
scene.render.filepath = "//renders/icon_all.png"
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "icons.blend"))
print("DONE")
