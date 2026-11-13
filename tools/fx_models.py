"""Generates the 3D effect models used by abilities (slashes, shockwaves, bursts, beams...).

    python3 tools/fx_models.py

Every effect is a flat, full-bright model with a soft white texture that the plugin tints with
the weapon's colour (custom_model_data colour 0), plus an untinted white-hot core where it makes
sense. Each comes in four fade levels (fx/<shape>_0 .. fx/<shape>_3) so the plugin can fade an
effect out by swapping the item. Needs Pillow.
"""
import io
import json
import math
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
ASSETS = os.path.join(ROOT, 'resourcepack', 'assets', 'op_swords')
FADES = [1.0, 0.62, 0.36, 0.15]
TINT_DEFAULT = 0xFFFFFF


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def image(size, alpha_fn):
    """A white texture whose alpha comes from alpha_fn(x, y) in 0..1 (pixel centre, 0..1 coords)."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            a = clamp(alpha_fn((x + 0.5) / size, (y + 0.5) / size))
            if a > 0.004:
                px[x, y] = (255, 255, 255, int(round(a * 255)))
    return img


def faded(img, factor):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, int(round(a * factor)))
    return out


# ---------------------------------------------------------------- textures

ARC = math.radians(80)  # half-width of the crescent


def crescent(core):
    """A crescent opening towards -x: bright leading edge on the outside, a soft trail inside."""
    def f(u, v):
        dx, dy = u - 0.5, v - 0.5
        r = math.hypot(dx, dy) * 2  # 0 at centre, 1 at the texture edge
        ang = abs(math.atan2(dy, dx))  # 0 = +x (forward)
        if ang > ARC:
            return 0
        along = 1 - ang / ARC  # 1 in the middle of the arc, 0 at its tips
        outer = 0.94
        width = (0.06 if core else 0.30) * (0.25 + 0.75 * math.sin(along * math.pi / 2))
        inner = outer - width
        if r > outer or r < inner:
            return 0
        t = (r - inner) / max(1e-6, width)  # 0 at inner edge, 1 at the leading edge
        if core:
            return smooth(1 - abs(t - 0.7) / 0.7) * smooth(along * 2.2)
        return min(1.0, (t ** 1.5) * smooth(along * 1.3) * 1.35)
    return f


def ring(core):
    def f(u, v):
        r = math.hypot(u - 0.5, v - 0.5) * 2
        if core:
            return smooth(1 - abs(r - 0.93) / 0.035)
        if r > 0.97:
            return smooth((1.0 - r) / 0.03)
        inner = 0.74
        if r < inner:
            return 0
        return ((r - inner) / (0.97 - inner)) ** 2.4
    return f


def burst(core):
    def f(u, v):
        dx, dy = u - 0.5, v - 0.5
        r = math.hypot(dx, dy) * 2
        ang = math.atan2(dy, dx)
        if core:
            return smooth(1 - r / 0.28)
        long_rays = abs(math.cos(2 * ang)) ** 7
        short_rays = abs(math.cos(2 * ang + math.pi / 2)) ** 12 * 0.8
        rays = max(long_rays * smooth(1 - r) ** 0.8, short_rays * smooth(1 - r / 0.62))
        halo = smooth(1 - r / 0.6) * 0.8
        return max(rays, halo)
    return f


def glow(core):
    def f(u, v):
        r = math.hypot(u - 0.5, v - 0.5) * 2
        if core:
            return smooth(1 - r / 0.35)
        return math.exp(-(r * r) * 4.2) * smooth(1 - r)
    return f


def beam(core):
    def f(u, v):
        d = abs(u - 0.5) * 2  # 0 in the middle of the beam, 1 at its sides
        if core:
            return smooth(1 - d / 0.22)
        return (1 - d) ** 1.8
    return f


def rune_texture():
    """The altar's magic circle, reused as the cast circle."""
    path = os.path.join(ASSETS, 'textures', 'item', 'altar', 'halo.png')
    return Image.open(path).convert('RGBA')


# ---------------------------------------------------------------- models

def face(tex, uv=(0, 0, 16, 16), tint=True):
    out = {"uv": list(uv), "texture": "#" + tex}
    if tint:
        out["tintindex"] = 0
    return out


def flat(tex, y, tint, size=32):
    """A horizontal square plane, `size` px wide, centred on the model."""
    lo, hi = 8 - size / 2, 8 + size / 2
    return {"from": [lo, y, lo], "to": [hi, y + 0.01, hi], "faces": {
        "up": face(tex, tint=tint), "down": face(tex, (0, 16, 16, 0), tint=tint)}}


def upright(tex, tint, size=32, z=8):
    """A vertical square plane facing north/south (for billboards)."""
    lo, hi = 8 - size / 2, 8 + size / 2
    return {"from": [lo, lo, z], "to": [hi, hi, z + 0.01], "faces": {
        "north": face(tex, tint=tint), "south": face(tex, tint=tint)}}


def cross(tex, tint, width):
    """Two crossed vertical planes along the Y axis, one block long: a beam segment."""
    lo, hi = 8 - width / 2, 8 + width / 2
    return [
        {"from": [lo, 0, 8], "to": [hi, 16, 8.01], "faces": {
            "north": face(tex, tint=tint), "south": face(tex, tint=tint)}},
        {"from": [8, 0, lo], "to": [8.01, 16, hi], "faces": {
            "east": face(tex, tint=tint), "west": face(tex, tint=tint)}},
    ]


SHAPES = {
    # name: (texture size, glow alpha fn, core alpha fn or None, elements(glow_tex, core_tex))
    'slash': (128, crescent(False), crescent(True),
              lambda g, c: [flat(g, 8, True), flat(c, 8.05, False)]),
    'ring': (128, ring(False), ring(True),
             lambda g, c: [flat(g, 8, True), flat(c, 8.05, False)]),
    'burst': (64, burst(False), burst(True),
              lambda g, c: [upright(g, True), upright(c, False, z=8.05)]),
    'glow': (64, glow(False), glow(True),
             lambda g, c: [upright(g, True), upright(c, False, z=8.05)]),
    'beam': (32, beam(False), beam(True),
             lambda g, c: cross(g, True, 6) + cross(c, False, 2)),
    'rune': (None, None, None,
             lambda g, c: [flat(g, 8, True)]),
}

DISPLAY = {"gui": {"rotation": [30, 225, 0], "scale": [0.5, 0.5, 0.5]}}


def png(img):
    buf = io.BytesIO()
    img.save(buf, format='PNG', optimize=True)
    return buf.getvalue()


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)


def main():
    count = 0
    for name, (size, glow_fn, core_fn, elements) in SHAPES.items():
        base_glow = rune_texture() if name == 'rune' else image(size, glow_fn)
        base_core = image(size, core_fn) if core_fn else None
        for level, factor in enumerate(FADES):
            glow_tex = f'op_swords:item/fx/{name}_{level}'
            core_tex = f'op_swords:item/fx/{name}_core_{level}'
            write(f'textures/item/fx/{name}_{level}.png', png(faded(base_glow, factor)))
            textures = {"glow": glow_tex, "particle": glow_tex}
            if base_core is not None:
                write(f'textures/item/fx/{name}_core_{level}.png', png(faded(base_core, factor)))
                textures["core"] = core_tex
            model = {"credit": "OP Swords effect - generated by tools/fx_models.py", "textures": textures,
                     "elements": elements("glow", "core"), "display": DISPLAY}
            write(f'models/item/fx/{name}_{level}.json', json.dumps(model, separators=(',', ':')).encode() + b'\n')
            item = {"model": {"type": "minecraft:model", "model": f"op_swords:item/fx/{name}_{level}",
                              "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": TINT_DEFAULT}]}}
            write(f'items/fx/{name}_{level}.json', json.dumps(item, indent=2).encode() + b'\n')
            count += 1
    print(f'Effects: {count} models ({len(SHAPES)} shapes x {len(FADES)} fade levels)')


if __name__ == '__main__':
    main()
