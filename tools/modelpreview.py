"""Minimal Minecraft item-model preview renderer (orthographic, textured, shaded).

    python3 tools/modelpreview.py resourcepack/assets out.png op_swords:item/dragon [...]

Renders each model in its GUI pose, from the side and edge-on. UVs that fall outside a texture
(which would sample other textures in-game) are painted magenta so they stand out.
Needs Pillow and numpy. Face rotations are ignored, so a few faces may look slightly off.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def load_model(assets, mid):
    ns, path = mid.split(":") if ":" in mid else ("minecraft", mid)
    with open(os.path.join(assets, ns, "models", path + ".json")) as f:
        m = json.load(f)
    if "parent" in m and "elements" not in m:
        parent = load_model(assets, m["parent"])
        parent.setdefault("textures", {}).update(m.get("textures", {}))
        if "display" in m:
            parent["display"] = m["display"]
        return parent
    return m


def resolve(textures, ref):
    seen = 0
    while ref.startswith("#") and seen < 10:
        ref = textures.get(ref[1:], "missing")
        seen += 1
    return ref


def load_tex(assets, tid, cache):
    if tid in cache:
        return cache[tid]
    ns, path = tid.split(":") if ":" in tid else ("minecraft", tid)
    p = os.path.join(assets, ns, "textures", path + ".png")
    img = np.array(Image.open(p).convert("RGBA")).astype(np.float32)
    mcmeta = p + ".mcmeta"
    if os.path.exists(mcmeta):  # animated strip: use first frame
        w = img.shape[1]
        img = img[:w]
    cache[tid] = img
    return img


def auto_uv(face, f, t):
    return {
        "down": [f[0], 16 - t[2], t[0], 16 - f[2]],
        "up": [f[0], f[2], t[0], t[2]],
        "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
        "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
        "west": [f[2], 16 - t[1], t[2], 16 - f[1]],
        "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    }[face]


def point(face, f, t, u, v):
    if face == "south":
        return (u, 16 - v, t[2])
    if face == "north":
        return (16 - u, 16 - v, f[2])
    if face == "east":
        return (t[0], 16 - v, 16 - u)
    if face == "west":
        return (f[0], 16 - v, u)
    if face == "up":
        return (u, t[1], v)
    return (u, f[1], 16 - v)  # down


def rot_matrix(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return X @ Y @ Z


def element_rotation(el):
    r = el.get("rotation")
    if not r:
        return lambda p: np.array(p, dtype=float)
    o = np.array(r["origin"], dtype=float)
    a = math.radians(r["angle"])
    axis = r["axis"]
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        M = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    elif axis == "y":
        M = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    else:
        M = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return lambda p: M @ (np.array(p, dtype=float) - o) + o


def render(assets, mid, size=320, view=(20, 225, 0), scale=1.0, cache=None):
    cache = {} if cache is None else cache
    m = load_model(assets, mid)
    textures = m.get("textures", {})
    R = rot_matrix(*view)
    px = size / 32.0 * scale
    faces = []
    for el in m.get("elements", []):
        f, t = el["from"], el["to"]
        rot = element_rotation(el)
        for face, fd in el.get("faces", {}).items():
            au = auto_uv(face, f, t)
            uv = fd.get("uv", au)
            p0 = rot(point(face, f, t, au[0], au[1]))
            p1 = rot(point(face, f, t, au[2], au[1]))
            p2 = rot(point(face, f, t, au[0], au[3]))
            corners = [p0, p1, rot(point(face, f, t, au[2], au[3])), p2]
            cs = [R @ (c - 8) for c in corners]
            normal = np.cross(cs[1] - cs[0], cs[3] - cs[0])
            depth = sum(c[2] for c in cs) / 4
            faces.append((depth, face, cs, uv, resolve(textures, fd["texture"])))
    faces.sort(key=lambda x: x[0])
    out = np.zeros((size, size, 4), dtype=np.float32)
    ys, xs = np.mgrid[0:size, 0:size]
    for depth, face, cs, uv, tid in faces:
        scr = [np.array([size / 2 + c[0] * px, size / 2 - c[1] * px]) for c in cs]
        e1, e2 = scr[1] - scr[0], scr[3] - scr[0]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        dx, dy = xs - scr[0][0], ys - scr[0][1]
        a = (dx * e2[1] - dy * e2[0]) / det
        b = (e1[0] * dy - e1[1] * dx) / det
        mask = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not mask.any():
            continue
        try:
            tex = load_tex(assets, tid, cache)
        except FileNotFoundError:
            tex = np.full((16, 16, 4), [255, 0, 255, 255], dtype=np.float32)
        th, tw = tex.shape[:2]
        u = uv[0] + a * (uv[2] - uv[0])
        v = uv[1] + b * (uv[3] - uv[1])
        oob = (u < -1e-3) | (u > 16.001) | (v < -1e-3) | (v > 16.001)
        tx = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
        ty = np.clip((v / 16 * th).astype(int), 0, th - 1)
        col = tex[ty, tx].copy()
        col[oob] = [255, 0, 255, 255]
        col[..., :3] *= SHADE[face]
        sel = mask & (col[..., 3] > 8)
        out[sel] = col[sel]
    return Image.fromarray(out.clip(0, 255).astype(np.uint8))


if __name__ == "__main__":
    assets, outp, *ids = sys.argv[1:]
    views = [("gui", None), ("side", (0, 0, 0)), ("edge", (0, 90, 0))]
    tiles = []
    cache = {}
    for mid in ids:
        m = load_model(assets, mid)
        gui = m.get("display", {}).get("gui", {}).get("rotation", [20, 225, 0])
        row = [render(assets, mid, view=tuple(gui), cache=cache),
               render(assets, mid, view=(0, 0, 0), cache=cache),
               render(assets, mid, view=(0, 90, 0), cache=cache)]
        tiles.append(row)
    W = 320
    sheet = Image.new("RGBA", (W * 3, W * len(tiles)), (48, 48, 56, 255))
    for r, row in enumerate(tiles):
        for c, img in enumerate(row):
            sheet.alpha_composite(img, (c * W, r * W))
    sheet.save(outp)
    print("wrote", outp)
