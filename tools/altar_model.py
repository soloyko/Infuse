"""Generates the Weapon Altar models and textures in resourcepack/.

    python3 tools/altar_model.py

The altar is one block: a stone pedestal (altar_base) - a dark two-tier base, a pale pillar with
framed panels and corner brackets, and a heavy top slab - plus a separate layer of runes in the
panel windows and a ring on top (altar_glow) that the plugin renders at full brightness and tints
with the colour of the weapon on display, and a magic circle (altar_halo) that floats and spins
just above it. The inventory item (altar) shows the pedestal and the glow layer.
Needs Pillow.
"""
import io
import json
import math
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
ASSETS = os.path.join(ROOT, 'resourcepack', 'assets', 'customs')
DEFAULT_TINT = 0x9FD8FF  # pale blue, when nothing is on the altar

# textures for the pedestal (drawn by this script) and the glow layer
T = {
    'dark_band': 'customs:item/altar/ped_dark_band',
    'dark_top': 'customs:item/altar/ped_dark_top',
    'dark': 'customs:item/altar/ped_dark',
    'pillar': 'customs:item/altar/ped_pillar',
    'top': 'customs:item/altar/ped_top',
    'rune': 'customs:item/altar/rune',
    'sigil': 'customs:item/altar/sigil',
    'crystal': 'customs:item/altar/crystal',
    'band': 'customs:item/altar/band',
    'halo': 'customs:item/altar/halo',
}


def box(frm, to, tex, faces='nsewud', uv_from_pos=True, tint=False, rotation=None):
    """An element; side faces use #tex, or per-face overrides when tex is a dict."""
    names = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}
    out = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        face = names[f]
        t = tex[face] if isinstance(tex, dict) else tex
        entry = {"texture": "#" + t}
        x1, y1, z1 = frm
        x2, y2, z2 = to
        uv = {
            'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1],
            'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1],
            'up': [x1, z1, x2, z2], 'down': [x1, 16 - z2, x2, 16 - z1],
        }[face]
        entry["uv"] = [round(max(0, min(16, v)), 3) for v in uv]
        if tint:
            entry["tintindex"] = 0
        out["faces"][face] = entry
    if rotation:
        out["rotation"] = rotation
    return out


def full_uv(element):
    for face in element["faces"].values():
        face["uv"] = [0, 0, 16, 16]
    return element


def sides(side, top=None, bottom=None):
    """Per-face textures: one texture on the four sides, optional top and bottom."""
    out = {'north': side, 'south': side, 'east': side, 'west': side}
    out['up'] = top or side
    out['down'] = bottom or top or side
    return out


def slab(frm, to, top='dark_top'):
    """A dark slab: banded sides (light edge on top, shadow below) and a rimmed top."""
    el = box(frm, to, {'north': 'dark_band', 'south': 'dark_band', 'east': 'dark_band', 'west': 'dark_band',
                       'up': top, 'down': 'dark'})
    return full_uv(el)


PILLAR = (3.0, 2.6, 13.0, 12.9)  # x1, y1, x2, y2 of the pillar (it is square: z matches x)


def base_elements():
    x1, y1, x2, y2 = PILLAR
    els = [
        # two-tier dark base
        slab([0, 0, 0], [16, 1.4, 16]),
        slab([1.2, 1.4, 1.2], [14.8, 2.6, 14.8]),
        # the pale pillar, with a framed panel on every face
        full_uv(box([x1, y1, x1], [x2, y2, x2], 'pillar', faces='nsew')),
        # dark capital and the heavy top slab, its top face pale
        slab([2.2, 12.9, 2.2], [13.8, 13.6, 13.8]),
        slab([0.6, 13.6, 0.6], [15.4, 16.0, 15.4], top='top'),
    ]
    # corner brackets: dark strips running up the pillar's edges and flaring out under the capital
    for cx, cz in ((x1, x1), (x2, x1), (x1, x2), (x2, x2)):
        sx = -1 if cx == x1 else 1
        sz = -1 if cz == x1 else 1
        w = 0.55
        els.append(box([min(cx, cx - sx * w), y1 + 0.6, min(cz, cz - sz * w)],
                       [max(cx, cx - sx * w), 10.6, max(cz, cz - sz * w)], 'dark', faces='nsewu'))
        for step, (out, y_lo, y_hi) in enumerate(((0.35, 10.6, 11.8), (0.75, 11.8, 12.9))):
            ox, oz = cx + sx * out, cz + sz * out
            els.append(box([min(cx - sx * w, ox), y_lo, min(cz - sz * w, oz)],
                           [max(cx - sx * w, ox), y_hi, max(cz - sz * w, oz)], 'dark', faces='nsewud'))
    return els


def crystal(x, y, z, size):
    """A small pointed crystal turned 45 degrees (kept for other tools)."""
    rot = {"angle": 45, "axis": "y", "origin": [x, y, z]}
    h = size
    return [
        full_uv(box([x - h * 0.45, y - h * 0.6, z - h * 0.45], [x + h * 0.45, y + h * 0.6, z + h * 0.45], 'crystal',
                    faces='nsewud', tint=True, rotation=rot)),
        full_uv(box([x - h * 0.25, y + h * 0.6, z - h * 0.25], [x + h * 0.25, y + h * 1.3, z + h * 0.25], 'crystal',
                    faces='nsewu', tint=True, rotation=rot)),
    ]


def glow_elements():
    """Runes glowing in the window of each pillar panel, and a faint ring on the top slab."""
    x1, y1, x2, y2 = PILLAR
    # the window of the panel texture is u 5..11, v 6..13 of the pillar face
    def u(px):
        return x1 + px * (x2 - x1) / 16

    def v(px):
        return y2 - px * (y2 - y1) / 16
    a, b = u(5.2), u(10.8)
    top, bottom = v(6.2), v(12.8)
    e = 0.02
    return [
        full_uv(box([a, bottom, x1 - e], [b, top, x1 - e + 0.01], 'rune', faces='n', tint=True)),
        full_uv(box([a, bottom, x2 + e], [b, top, x2 + e + 0.01], 'rune', faces='s', tint=True)),
        full_uv(box([x1 - e, bottom, a], [x1 - e + 0.01, top, b], 'rune', faces='w', tint=True)),
        full_uv(box([x2 + e, bottom, a], [x2 + e + 0.01, top, b], 'rune', faces='e', tint=True)),
        full_uv(box([2.0, 16.02, 2.0], [14.0, 16.04, 14.0], 'sigil', faces='u', tint=True)),
    ]


def halo_elements():
    """A magic circle that floats just above the altar (the plugin spins it). Model y 8 = its plane."""
    return [
        {"from": [-2, 8, -2], "to": [18, 8.02, 18], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#halo", "tintindex": 0},
            "down": {"uv": [0, 16, 16, 0], "texture": "#halo", "tintindex": 0}}},
    ]


BLOCK_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
}


def model(elements, textures):
    used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
    tex = {k: T[k] for k in sorted(used)}
    tex["particle"] = T["pillar"]
    return {"credit": "Customs altar - generated by tools/altar_model.py", "textures": tex,
            "elements": elements, "display": BLOCK_DISPLAY}


# ---- textures (white so the tint gives them their colour) ----

RUNES = [
    ["  #  ", " ### ", "# # #", "  #  ", "  #  ", " # # ", "#   #"],
    ["#   #", "## ##", "# # #", "#   #", "#   #", "#   #", "#####"],
    ["#####", "  #  ", " # # ", "#   #", " # # ", "  #  ", "#####"],
]


def rune_texture():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    # border frame
    for i in range(32):
        for j in (1, 30):
            px[i, j] = (255, 255, 255, 255)
            px[j, i] = (255, 255, 255, 255)
    # three runes side by side
    for n, rune in enumerate(RUNES):
        ox, oy = 3 + n * 9, 12
        for y, row in enumerate(rune):
            for x, ch in enumerate(row):
                if ch == '#':
                    px[ox + x, oy + y] = (255, 255, 255, 255)
    # dots above and below
    for x in range(5, 28, 4):
        px[x, 5] = (220, 220, 220, 255)
        px[x, 26] = (220, 220, 220, 255)
    return img


def sigil_texture():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    c = 15.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - c, y - c)
            if 13.2 <= d <= 14.6 or 9.2 <= d <= 10.1:
                px[x, y] = (255, 255, 255, 255)
    # an eight point star
    for k in range(8):
        a = k * math.pi / 4
        for r in range(0, 10):
            x, y = int(round(c + math.cos(a) * r)), int(round(c + math.sin(a) * r))
            px[x, y] = (255, 255, 255, 255)
    # small marks between the rings
    for k in range(12):
        a = k * math.pi / 6
        x, y = int(round(c + math.cos(a) * 11.7)), int(round(c + math.sin(a) * 11.7))
        px[x, y] = (230, 230, 230, 255)
    return img


def crystal_texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 150 + int(90 * (1 - y / 15)) + (25 if (x + y) % 5 == 0 else 0) - (35 if x in (0, 15) else 0)
            if (x - 7) ** 2 + (y - 4) ** 2 < 5:
                v = 255
            v = max(90, min(255, v))
            px[x, y] = (v, v, v, 255)
    return img


GLYPHS = [
    ["###", "# #", "###", "#  ", "#  "], ["# #", "###", "# #", "# #", "###"], ["###", " # ", "###", " # ", "# #"],
    ["#  ", "## ", "# #", "## ", "#  "], [" # ", "# #", " # ", "# #", " # "], ["###", "#  ", " # ", "  #", "###"],
]


def band_texture():
    img = Image.new('RGBA', (64, 8), (0, 0, 0, 0))
    px = img.load()
    for n in range(12):
        glyph = GLYPHS[n % len(GLYPHS)]
        ox = 2 + n * 5
        for y, row in enumerate(glyph):
            for x, ch in enumerate(row):
                if ch == '#' and ox + x < 64:
                    px[ox + x, 1 + y] = (255, 255, 255, 255)
    for x in range(0, 64, 2):
        px[x, 7] = (200, 200, 200, 200)
    return img


def halo_texture():
    size = 96
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if 45.5 <= d <= 47 or 35 <= d <= 36:
                px[x, y] = (255, 255, 255, 235)
            elif 46.9 < d < 47.6:
                px[x, y] = (255, 255, 255, 110)
    # glyphs between the two rings
    for k in range(16):
        a = k * math.pi * 2 / 16
        glyph = GLYPHS[k % len(GLYPHS)]
        gx, gy = c + math.cos(a) * 40.5, c + math.sin(a) * 40.5
        for yy, row in enumerate(glyph):
            for xx, ch in enumerate(row):
                if ch != '#':
                    continue
                # turn each glyph to face outwards
                lx, ly = xx - 1, yy - 2
                ca, sa = math.cos(a + math.pi / 2), math.sin(a + math.pi / 2)
                px_, py_ = int(round(gx + lx * ca - ly * sa)), int(round(gy + lx * sa + ly * ca))
                if 0 <= px_ < size and 0 <= py_ < size:
                    px[px_, py_] = (255, 255, 255, 255)
    # four small triangles pointing inwards on the inner ring
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        for t in range(5):
            for w in range(-t // 2, t // 2 + 1):
                r = 34 - t
                x = int(round(c + math.cos(a) * r - math.sin(a) * w))
                y = int(round(c + math.sin(a) * r + math.cos(a) * w))
                px[x, y] = (255, 255, 255, 200)
    return img


def _noise(x, y, seed):
    """Deterministic per-pixel noise in -1..1."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 32767.5 - 1


def _shade(v, x, y, seed, amount=7):
    g = int(max(0, min(255, v + _noise(x, y, seed) * amount)))
    return (g, g, min(255, g + 4), 255)


DARK = 74
DARK_HI = 118
DARK_LO = 52
LIGHT = 206


def dark_texture():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = _shade(DARK, x, y, 1)
    return img


def dark_band_texture():
    """Slab sides: a light edge on top, the body, and a shadow line at the bottom."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = DARK_HI if y < 3 else (DARK_LO if y >= 14 else DARK)
            if y in (3, 4):
                v = (DARK_HI + DARK) // 2
            px[x, y] = _shade(v, x, y, 2, 5)
    return img


def dark_top_texture():
    """Slab tops: dark with a light rim."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = x == 0 or y == 0 or x == 15 or y == 15
            px[x, y] = _shade(DARK_HI if edge else DARK + 6, x, y, 3, 6)
    return img


def top_texture():
    """The pale, mottled top of the pedestal with a dark rim."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0 or x == 15 or y == 15:
                px[x, y] = _shade(DARK, x, y, 4, 4)
            elif x == 1 or y == 1 or x == 14 or y == 14:
                px[x, y] = _shade(176, x, y, 4, 5)
            else:
                spot = _noise(x // 2, y // 2, 9)
                px[x, y] = _shade(210 + int(spot * 14), x, y, 5, 8)
    return img


def pillar_texture():
    """A pillar face: pale stone, dark frame lines branching up to the corners, an inset window."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = LIGHT
            if x in (0, 15):
                v = 186                     # rounded edge
            if y <= 1:
                v = DARK                    # shadow under the capital
            px[x, y] = _shade(v, x, y, 6, 6)
    dark = []
    # the frame: two verticals that branch out diagonally to the top corners
    for y in range(5, 16):
        dark += [(3, y), (12, y)]
    for i in range(4):
        dark += [(3 - i, 5 - i), (12 + i, 5 - i), (3 - i + 1, 5 - i), (12 + i - 1, 5 - i)]
    dark += [(x, 15) for x in range(3, 13)]
    for x, y in dark:
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = _shade(DARK, x, y, 7, 4)
    # the inset window: dark outline, pale glassy inside with a highlight
    for y in range(6, 14):
        for x in range(5, 11):
            outline = x in (5, 10) or y in (6, 13)
            v = DARK + 10 if outline else (240 if x == 6 else 224)
            px[x, y] = _shade(v, x, y, 8, 3 if not outline else 4)
    # the band between frame and window is a touch darker
    for y in range(5, 15):
        for x in (4, 11):
            px[x, y] = _shade(184, x, y, 10, 4)
    return img


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)


def png(img):
    buf = io.BytesIO()
    img.save(buf, format='PNG', optimize=True)
    return buf.getvalue()


def main():
    base = model(base_elements(), T)
    glow = model(glow_elements(), T)
    halo = model(halo_elements(), T)
    write('models/item/altar_base.json', json.dumps(base, separators=(',', ':')).encode() + b'\n')
    write('models/item/altar_glow.json', json.dumps(glow, separators=(',', ':')).encode() + b'\n')
    write('models/item/altar_halo.json', json.dumps(halo, separators=(',', ':')).encode() + b'\n')
    tint = [{"type": "minecraft:custom_model_data", "index": 0, "default": DEFAULT_TINT}]
    items = {
        'altar_base': {"model": {"type": "minecraft:model", "model": "customs:item/altar_base"}},
        'altar_glow': {"model": {"type": "minecraft:model", "model": "customs:item/altar_glow", "tints": tint}},
        'altar_halo': {"model": {"type": "minecraft:model", "model": "customs:item/altar_halo", "tints": tint}},
        'altar': {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": "customs:item/altar_base"},
            {"type": "minecraft:model", "model": "customs:item/altar_glow", "tints": tint}]}},
    }
    for name, item in items.items():
        write(f'items/{name}.json', json.dumps(item, indent=2).encode() + b'\n')
    write('textures/item/altar/rune.png', png(rune_texture()))
    write('textures/item/altar/ped_dark.png', png(dark_texture()))
    write('textures/item/altar/ped_dark_band.png', png(dark_band_texture()))
    write('textures/item/altar/ped_dark_top.png', png(dark_top_texture()))
    write('textures/item/altar/ped_top.png', png(top_texture()))
    write('textures/item/altar/ped_pillar.png', png(pillar_texture()))
    write('textures/item/altar/sigil.png', png(sigil_texture()))
    write('textures/item/altar/crystal.png', png(crystal_texture()))
    write('textures/item/altar/band.png', png(band_texture()))
    write('textures/item/altar/halo.png', png(halo_texture()))
    print(f'Altar: {len(base["elements"])} base elements, {len(glow["elements"])} glow elements')


if __name__ == '__main__':
    main()
