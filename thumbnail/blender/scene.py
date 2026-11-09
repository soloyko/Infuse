"""Blender (bpy) recreation of the reference thumbnail with the custom skin.

Usage:  python scene.py [--preview] [--skin path/to/skin.png] [--out out.png]
Requires the `bpy` package (pip install bpy) and numpy.
"""
import argparse, math, os, random, sys
import numpy as np
import bpy
from mathutils import Vector, Euler

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from skin import build_skin_pixels          # noqa: E402
from sprites import SWORD, PICKAXE, APPLE, ARROW, BOW  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--preview', action='store_true')
ap.add_argument('--skin')
ap.add_argument('--out', default=os.path.join(HERE, '..', 'thumbnail.png'))
args = ap.parse_args()

rng = random.Random(1234)
R = rng.uniform

# ---------------------------------------------------------------- scene setup
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
W, H = 1920, 1080
scene.render.resolution_x, scene.render.resolution_y = W, H
scene.render.resolution_percentage = 50 if args.preview else 100
scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.samples = 24 if args.preview else 128
scene.cycles.use_denoising = True
scene.cycles.max_bounces = 4
scene.render.film_transparent = True
scene.view_settings.view_transform = 'Standard'

# Everything lives under a root that converts y-up (layout space) to Blender z-up.
root = bpy.data.objects.new('root', None)
scene.collection.objects.link(root)
root.rotation_euler = (math.radians(90), 0, 0)


def link(obj, parent=root):
    scene.collection.objects.link(obj)
    obj.parent = parent
    return obj


def place(obj, pos=(0, 0, 0), rot=(0, 0, 0), scale=1.0):
    obj.location = pos
    obj.rotation_mode = 'ZYX'   # same as three.js 'XYZ' order
    obj.rotation_euler = rot
    obj.scale = (scale,) * 3 if np.isscalar(scale) else scale
    return obj


# Frame mapping: at depth z=0 the frame spans x [-20.9, 20.9], y [-11.75, 11.75].
def fx(f): return (f - 0.5) * 41.8
def fy(f): return (0.5 - f) * 23.5


# ------------------------------------------------------------------- materials
def mat_emit(name, color, strength):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*color, 1)
    em.inputs['Strength'].default_value = strength
    nt.links.new(em.outputs[0], out.inputs[0])
    return m


def mat_basic(name, color, rough=0.8, emit=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*color, 1)
    p.inputs['Roughness'].default_value = rough
    if emit:
        p.inputs['Emission Color'].default_value = (*color, 1)
        p.inputs['Emission Strength'].default_value = emit
    return m


def mat_vcol(name, emit=0.0, rough=0.7):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    a = nt.nodes.new('ShaderNodeVertexColor')
    a.layer_name = 'Col'
    nt.links.new(a.outputs['Color'], p.inputs['Base Color'])
    p.inputs['Roughness'].default_value = rough
    if emit:
        nt.links.new(a.outputs['Color'], p.inputs['Emission Color'])
        p.inputs['Emission Strength'].default_value = emit
    return m


def srgb(hexstr):
    h = hexstr.lstrip('#')
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c)


# ------------------------------------------------------------------------ skin
if args.skin:
    skin_img = bpy.data.images.load(os.path.abspath(args.skin))
else:
    skin_img = bpy.data.images.new('skin', 64, 64, alpha=True)
    px = build_skin_pixels()                      # 64x64x4, row 0 = top, sRGB 0..1
    skin_img.pixels = np.flipud(px).ravel().tolist()
    skin_img.filepath_raw = os.path.join(HERE, '..', 'skin.png')
    skin_img.file_format = 'PNG'
    skin_img.save()

skin_mat = bpy.data.materials.new('skin')
skin_mat.use_nodes = True
nt = skin_mat.node_tree
p = nt.nodes['Principled BSDF']
tex = nt.nodes.new('ShaderNodeTexImage')
tex.image = skin_img
tex.interpolation = 'Closest'
nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
nt.links.new(tex.outputs['Alpha'], p.inputs['Alpha'])
p.inputs['Roughness'].default_value = 0.55
p.inputs['Specular IOR Level'].default_value = 0.6


def skin_box(name, w, h, d, u, v, inflate=0.0, pivot_y=0.0):
    hw, hh, hd = w / 2 + inflate, h / 2 + inflate, d / 2 + inflate
    rects = {
        'left':   (u + d + w, v + d, d, h),
        'right':  (u, v + d, d, h),
        'top':    (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'front':  (u + d, v + d, w, h),
        'back':   (u + 2 * d + w, v + d, w, h),
    }
    # corners as seen from outside: TL, TR, BL, BR
    faces = {
        'front':  [(-hw, hh, hd), (hw, hh, hd), (-hw, -hh, hd), (hw, -hh, hd)],
        'back':   [(hw, hh, -hd), (-hw, hh, -hd), (hw, -hh, -hd), (-hw, -hh, -hd)],
        'left':   [(hw, hh, hd), (hw, hh, -hd), (hw, -hh, hd), (hw, -hh, -hd)],
        'right':  [(-hw, hh, -hd), (-hw, hh, hd), (-hw, -hh, -hd), (-hw, -hh, hd)],
        'top':    [(-hw, hh, -hd), (hw, hh, -hd), (-hw, hh, hd), (hw, hh, hd)],
        'bottom': [(-hw, -hh, hd), (hw, -hh, hd), (-hw, -hh, -hd), (hw, -hh, -hd)],
    }
    verts, polys, uvs = [], [], []
    for k, (tl, tr, bl, br) in faces.items():
        x, y, rw, rh = rects[k]
        x0, x1, y0, y1 = x / 64, (x + rw) / 64, 1 - y / 64, 1 - (y + rh) / 64
        i = len(verts)
        verts += [tl, bl, br, tr]
        polys.append((i, i + 1, i + 2, i + 3))
        uvs += [(x0, y0), (x0, y1), (x1, y1), (x1, y0)]
    verts = [(a, b + pivot_y, c) for a, b, c in verts]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    uvl = me.uv_layers.new()
    for li, loop in enumerate(me.loops):
        uvl.data[li].uv = uvs[loop.vertex_index]
    me.materials.append(skin_mat)
    return bpy.data.objects.new(name, me)


def part(name, w, h, d, uv, ov, inflate, pivot_y=0.0):
    holder = link(bpy.data.objects.new(name, None))
    link(skin_box(name + '_base', w, h, d, *uv, 0, pivot_y), holder)
    link(skin_box(name + '_layer', w, h, d, *ov, inflate, pivot_y), holder)
    return holder


# ---------------------------------------------------------------- voxel sprites
def voxel_sprite(name, sprite, emit=0.0, emit_chars=None, emit_strength=2.0):
    verts, polys, cols, flags = [], [], [], []
    cube = [(-.5, -.5, -.5), (.5, -.5, -.5), (.5, .5, -.5), (-.5, .5, -.5),
            (-.5, -.5, .5), (.5, -.5, .5), (.5, .5, .5), (-.5, .5, .5)]
    cf = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (2, 3, 7, 6), (1, 2, 6, 5), (0, 4, 7, 3)]
    for y, row in enumerate(sprite['rows']):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            i = len(verts)
            verts += [(a + x - 7.5, b + 7.5 - y, c) for a, b, c in cube]
            col = srgb(sprite['pal'][ch])
            glow = emit_chars is not None and ch in emit_chars
            for f in cf:
                polys.append(tuple(i + j for j in f))
                cols.append(col)
                flags.append(glow)
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    ca = me.color_attributes.new('Col', 'FLOAT_COLOR', 'CORNER')
    for pi, poly in enumerate(me.polygons):
        for li in poly.loop_indices:
            ca.data[li].color = (*cols[pi], 1)
    me.materials.append(mat_vcol(name + '_m', emit))
    if emit_chars:
        me.materials.append(mat_vcol(name + '_glow', emit_strength))
        for pi, poly in enumerate(me.polygons):
            poly.material_index = 1 if flags[pi] else 0
    return link(bpy.data.objects.new(name, me))


# ------------------------------------------------------------------- character
head = part('head', 8, 8, 8, (0, 0), (32, 0), 0.5)
place(head, (fx(0.475), fy(0.29), 0), (0.1, -0.6, 0.03))

body = part('body', 8, 12, 4, (16, 16), (16, 32), 0.25)
place(body, (fx(0.53), fy(0.29) - 10.1, -1.5), (0.05, -0.75, 0))


def limb(name, uv, ov, shoulder, direction):
    a = part(name, 4, 12, 4, uv, ov, 0.25, -6)
    a.location = shoulder
    a.rotation_mode = 'QUATERNION'
    a.rotation_quaternion = Vector((0, -1, 0)).rotation_difference(Vector(direction).normalized())
    return a


limb('arm_r', (40, 16), (40, 32), (-2.5, 0.3, -5), (-2.7, -2.4, 12))
limb('arm_l', (32, 48), (48, 48), (fx(0.64), fy(0.47), -3.5), (0.25, -1, -0.15))

# --------------------------------------------------------------------- items
apple = voxel_sprite('apple', APPLE, emit=0.15, emit_chars='kKl', emit_strength=6)
place(apple, (fx(0.315) * 31 / 40, fy(0.42) * 31 / 40, 9), (0, 0.35, 0.05), 0.27)

sword = voxel_sprite('sword', SWORD, emit=0.0, emit_chars='LW', emit_strength=0.8)
place(sword, (fx(0.35) * 1.15, fy(0.25) * 1.15, -6), (0, 0, math.pi / 2 + 0.12), 0.78)

pick = voxel_sprite('pickaxe', PICKAXE, emit=0.0, emit_chars='LW', emit_strength=0.8)
place(pick, (fx(0.745) * 1.16, fy(0.22) * 1.16, -6.5), (0, 0, math.pi / 2 - 0.08), 0.72)

bow = voxel_sprite('bow', BOW)
place(bow, (fx(0.46), fy(0.95), 3), (-0.3, 0.1, -0.55), 0.7)

arrow1 = voxel_sprite('arrow1', ARROW, emit=0.15)
place(arrow1, (fx(0.2), fy(0.54), 1), (0, 0, 1.97), 0.5)
arrow2 = voxel_sprite('arrow2', ARROW, emit=0.15)
place(arrow2, (fx(0.75), fy(0.68), 1), (0, 0, -0.87), 0.42)

# --------------------------------------------------------------- white slashes
slash_mat = mat_emit('slash', (1, 1, 1), 2.4)


def slash(pts, thick, z, step=0.38):
    verts, polys = [], []
    cube = [(-.5, -.5, -.15), (.5, -.5, -.15), (.5, .5, -.15), (-.5, .5, -.15),
            (-.5, -.5, .15), (.5, -.5, .15), (.5, .5, .15), (-.5, .5, .15)]
    cf = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (2, 3, 7, 6), (1, 2, 6, 5), (0, 4, 7, 3)]
    P = [(fx(a), fy(b)) for a, b in pts]
    total = len(P) - 1
    seen = set()
    for s in range(total):
        (ax, ay), (bx, by) = P[s], P[s + 1]
        n = max(1, math.ceil(math.hypot(bx - ax, by - ay) / step))
        for i in range(n + 1):
            t = i / n
            u = (s + t) / total
            th = max(1, round(thick * (0.25 + 0.75 * math.sin(math.pi * u))))
            gx = round((ax + (bx - ax) * t) / step)
            gy = round((ay + (by - ay) * t) / step)
            for k in range(th):
                key = (gx, gy - k)
                if key in seen:
                    continue
                seen.add(key)
                j = len(verts)
                verts += [(a * step + gx * step, b * step + (gy - k) * step, c + z) for a, b, c in cube]
                polys += [tuple(j + q for q in f) for f in cf]
    me = bpy.data.meshes.new('slash')
    me.from_pydata(verts, [], polys)
    me.materials.append(slash_mat)
    link(bpy.data.objects.new('slash', me))


slash([[0.25, 0.975], [0.4, 0.985], [0.55, 0.95], [0.68, 0.86], [0.8, 0.76], [0.87, 0.7]], 3, 4)
slash([[0.56, 0.88], [0.64, 0.84], [0.72, 0.77]], 2, 4)
slash([[0.13, 0.66], [0.19, 0.68], [0.215, 0.72], [0.235, 0.74], [0.26, 0.72]], 3, 2)
slash([[0.12, 0.69], [0.17, 0.73], [0.2, 0.78]], 2, 2)
slash([[0.64, 0.43], [0.69, 0.44], [0.73, 0.44], [0.76, 0.42]], 2, 0)
slash([[0.6, 0.92], [0.67, 0.92], [0.74, 0.88]], 2, 4.5)


# ------------------------------------------------------------- particle helper
def cubes(name, items, mat):
    """items: list of (x, y, z, size, rot)"""
    verts, polys = [], []
    cf = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (2, 3, 7, 6), (1, 2, 6, 5), (0, 4, 7, 3)]
    for (x, y, z, s, r) in items:
        e = Euler(r, 'XYZ')
        j = len(verts)
        for c in [(-.5, -.5, -.5), (.5, -.5, -.5), (.5, .5, -.5), (-.5, .5, -.5),
                  (-.5, -.5, .5), (.5, -.5, .5), (.5, .5, .5), (-.5, .5, .5)]:
            v = Vector(c) * s
            v.rotate(e)
            verts.append((v.x + x, v.y + y, v.z + z))
        polys += [tuple(j + q for q in f) for f in cf]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    me.materials.append(mat)
    return link(bpy.data.objects.new(name, me))


# blood splatter
blood_mats = [mat_basic('blood%d' % i, srgb(c), 0.3, e) for i, (c, e) in
              enumerate([('#ff1a1a', 1.2), ('#d40d14', 0.5), ('#8a050a', 0.0), ('#ff3b30', 1.8)])]
buckets = [[] for _ in blood_mats]
for cx, cy, r, n in [(0.36, 0.18, 3.5, 260), (0.33, 0.36, 2.5, 110), (0.63, 0.15, 3.5, 220),
                     (0.61, 0.33, 2.5, 110), (0.47, 0.06, 3, 90), (0.24, 0.66, 2.5, 60),
                     (0.36, 0.72, 2.0, 40), (0.71, 0.08, 2, 40)]:
    for _ in range(n):
        a = R(0, 2 * math.pi)
        d = (rng.random() ** 1.5) * r
        s = max(0.06, R(0.08, 0.32) * (1 - d / (r * 1.3)))
        buckets[rng.randrange(len(blood_mats))].append(
            (fx(cx) + math.cos(a) * d * 1.3, fy(cy) + math.sin(a) * d * 0.8, R(-4.5, 4.5), s,
             (R(0, 3), R(0, 3), R(0, 3))))
for i, b in enumerate(buckets):
    cubes('blood%d' % i, b, blood_mats[i])

# dust specks
dust = []
for _ in range(45):
    z = R(-60, -5)
    sp = (50 - z) * 0.45
    dust.append((R(-sp, sp), R(-sp * 0.5, sp * 0.55), z, R(0.15, 0.4), (0, 0, 0)))
cubes('dust', dust, mat_emit('dust', srgb('#cbbcc0'), 0.6))

# blocky dark clouds
cloud_mats = [mat_basic('cloud1', srgb('#3a2632'), 0.9, 0.35), mat_basic('cloud2', srgb('#4a2e3a'), 0.9, 0.3)]
for i in range(22):
    z = R(-170, -60)
    spread = -z * 0.75
    x, y = R(-spread, spread), R(-spread * 0.35, spread * 0.55)
    if abs(x) < -z * 0.35 and abs(y) < -z * 0.3:
        continue
    for j in range(rng.randint(1, 3)):
        w, h, d = R(12, 30), R(2, 4.5), R(5, 10)
        bpy.ops.mesh.primitive_cube_add(size=1)
        c = bpy.context.active_object
        bpy.context.scene.collection.objects.unlink(c) if c.name in scene.collection.objects else None
        for col in c.users_collection:
            col.objects.unlink(c)
        link(c)
        place(c, (x + R(-12, 12), y + R(-4, 4), z + R(-3, 3)), (R(-.15, .15), R(-.3, .3), R(-.12, .12)), (w, h, d))
        c.data.materials.append(cloud_mats[rng.randrange(2)])

# grass in the corners
grass_mats = [mat_basic('grass%d' % i, srgb(c), 0.8, 0.08) for i, c in
              enumerate(['#3fae2a', '#5fd13a', '#2f8a20', '#7ae84a', '#4cc232'])]
gb = [[] for _ in grass_mats]
for x0, x1, n in [(-40, -22, 60), (24, 40, 55)]:
    for _ in range(n):
        xx, h, z = round(R(x0, x1)), rng.randint(2, 6), R(8, 12)
        for k in range(h):
            if rng.random() < 0.25:
                xx += rng.choice((-1, 1))
            gb[rng.randrange(len(gb))].append((xx * 0.45, -10.3 + k * 0.45, z, 0.45, (0, 0, 0)))
for i, b in enumerate(gb):
    cubes('grass%d' % i, b, grass_mats[i])

# ----------------------------------------------------------------- lighting
target = link(bpy.data.objects.new('target', None))
target.location = (fx(0.5), fy(0.4), 0)


def area(name, pos, color, power, size):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.color = color
    ld.energy = power
    ld.size = size
    o = link(bpy.data.objects.new(name, ld))
    o.location = pos
    tc = o.constraints.new('TRACK_TO')
    tc.target = target
    tc.track_axis = 'TRACK_NEGATIVE_Z'
    tc.up_axis = 'UP_Y'
    return o


area('red_rim_right', (28, 10, -14), (1.0, 0.06, 0.06), 3500, 6)
area('red_back', (4, 14, -30), (1.0, 0.05, 0.05), 30000, 10)
area('red_rim_left', (-24, 18, -14), (1.0, 0.1, 0.08), 30000, 10)
area('red_under', (6, -16, -10), (1.0, 0.05, 0.05), 20000, 10)
area('key', (-26, 16, 34), (1.0, 0.86, 0.86), 4500, 16)
area('fill', (20, -6, 30), (0.5, 0.6, 1.0), 900, 16)

world = bpy.data.worlds.new('world')
scene.world = world
world.use_nodes = True
world.node_tree.nodes['Background'].inputs['Color'].default_value = (*srgb('#1a0508'), 1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.6

# ------------------------------------------------------------------- camera
cd = bpy.data.cameras.new('cam')
cd.sensor_fit = 'VERTICAL'
cd.sensor_height = 24
cd.lens = 12 / math.tan(math.radians(16.5))
cd.clip_end = 2000
cd.dof.use_dof = True
cd.dof.focus_distance = 38
cd.dof.aperture_fstop = 0.2
cam = link(bpy.data.objects.new('cam', cd))
cam.location = (0, 0, 40)
scene.camera = cam

# ------------------------------------------------------------------- render
exr = os.path.join(HERE, 'render.exr')
scene.render.image_settings.file_format = 'OPEN_EXR'
scene.render.image_settings.color_depth = '32'
scene.render.filepath = exr
bpy.ops.render.render(write_still=True)

# ------------------------------------------------------- post: bg, bloom, tone
img = bpy.data.images.load(exr)
w, h = img.size
rgba = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]  # top row first
rgb, alpha = rgba[..., :3], rgba[..., 3:4]

# background radial glow (linear)
yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
cx, cy = 0.56 * w, 0.42 * h
d = np.sqrt(((xx - cx) / (0.7 * w)) ** 2 + ((yy - cy) / (0.6 * w)) ** 2)
stops = [(0.0, '#c0161e'), (0.22, '#7a0d15'), (0.5, '#33070d'), (1.0, '#0a0306')]
bg = np.zeros((h, w, 3), np.float32)
for (d0, c0), (d1, c1) in zip(stops, stops[1:]):
    m = (d >= d0) & (d < d1)
    t = ((d - d0) / (d1 - d0))[m][:, None]
    bg[m] = np.array(srgb(c0)) * (1 - t) + np.array(srgb(c1)) * t
bg[d >= 1.0] = srgb(stops[-1][1])
img_lin = rgb + bg * (1 - alpha)          # rgb is premultiplied


def blur(a, radius):
    # separable gaussian via FFT-free cumulative box blurs (3 passes)
    r = max(1, int(radius))
    out = a
    for _ in range(3):
        for axis in (0, 1):
            pad = [(0, 0)] * out.ndim
            pad[axis] = (r + 1, r)
            c = np.cumsum(np.pad(out, pad, mode='edge'), axis=axis, dtype=np.float64)
            hi = np.take(c, np.arange(2 * r + 1, c.shape[axis]), axis=axis)
            lo = np.take(c, np.arange(0, c.shape[axis] - 2 * r - 1), axis=axis)
            out = ((hi - lo) / (2 * r + 1)).astype(np.float32)
    return out


lum = img_lin.max(axis=2, keepdims=True)
bright = img_lin * np.clip((lum - 1.0) / 2.0, 0, 1)
scale = w / 1920
bloom = (blur(bright, 5 * scale) * 0.45 + blur(bright, 16 * scale) * 0.3 + blur(bright, 44 * scale) * 0.25)
img_lin = img_lin + bloom

# vignette
vig = 1 - 0.4 * np.clip(d - 0.5, 0, 1) ** 1.2
img_lin *= vig[..., None]

# ACES-ish tonemap + sRGB encode
x = img_lin * 1.1
tm = np.clip((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0, 1)
out = np.where(tm <= 0.0031308, tm * 12.92, 1.055 * np.power(tm, 1 / 2.4) - 0.055)

o = bpy.data.images.new('final', w, h, alpha=False)
o.pixels = np.concatenate([out[::-1], np.ones((h, w, 1), np.float32)], axis=2).ravel().tolist()
o.filepath_raw = os.path.abspath(args.out)
o.file_format = 'PNG'
o.save()
print('wrote', os.path.abspath(args.out))
