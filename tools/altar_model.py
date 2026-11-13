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

# textures for the pedestal (drawn pixel by pixel below) and the glow layer
T = {
    'side': 'customs:item/altar/ped_side',
    'top_cap': 'customs:item/altar/ped_top',
    'base_top': 'customs:item/altar/ped_base_top',
    'dark': 'customs:item/altar/ped_dark',
    'window': 'customs:item/altar/ped_window',
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


# The pedestal is built on whole pixels, and every side face samples the same front-view drawing
# (SIDE below) at its own position, so one texel is one model pixel everywhere: crisp like a
# vanilla block. Rows are listed top (y 16) to bottom (y 0).
#   D dark  d dark band  r rim  m mid  f frame line  q bevel  p pillar  h highlight  w window  . unused
SIDE = [
    ".rrrrrrrrrrrrrr.",  # top slab: lit rim
    ".DDDDDDDDDDDDDD.",  # top slab
    "..dddddddddddd..",  # capital
    "...fffppppfff...",  # brackets flare into the capital
    "...qffppppffq...",
    "...qfpffffpfq...",  # frame, panel top
    "...qfpfhwfpfq...",  # panel window
    "...qfpfhwfpfq...",
    "...qfpfhwfpfq...",
    "...qfpfhwfpfq...",
    "...qfpffffpfq...",  # panel bottom
    "...qfppppppfq...",
    "...qffffffffq...",  # frame bottom
    ".mmmmmmmmmmmmmm.",  # upper base step
    "rrrrrrrrrrrrrrrr",  # base: lit rim
    "DDDDDDDDDDDDDDDD",  # base
]


def base_elements():
    sides = lambda up, down='dark': {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side',
                                     'up': up, 'down': down}
    return [
        box([0, 0, 0], [16, 2, 16], sides('base_top')),
        box([1, 2, 1], [15, 3, 15], sides('base_top')),
        box([3, 3, 3], [13, 13, 13], 'side', faces='nsew'),
        box([2, 13, 2], [14, 14, 14], sides('dark'), faces='nsewd'),
        box([1, 14, 1], [15, 16, 15], sides('top_cap')),
    ]


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
    """The panel windows glow in the weapon's colour (columns 7-8, rows 6-9 of SIDE: x 7..9, y 6..10)."""
    e = 0.02
    return [
        full_uv(box([7, 6, 3 - e], [9, 10, 3 - e + 0.01], 'window', faces='n', tint=True)),
        full_uv(box([7, 6, 13 + e], [9, 10, 13 + e + 0.01], 'window', faces='s', tint=True)),
        full_uv(box([3 - e, 6, 7], [3 - e + 0.01, 10, 9], 'window', faces='w', tint=True)),
        full_uv(box([13 + e, 6, 7], [13 + e + 0.01, 10, 9], 'window', faces='e', tint=True)),
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
    tex["particle"] = T["side"]
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


PALETTE = {
    'D': 58, 'd': 78, 'r': 156, 'm': 112, 'f': 120, 'q': 228, 'p': 206, 'h': 246, 'w': 232,
}


def _pixel(v, x, y, seed, amount=3):
    g = int(max(0, min(255, v + _noise(x, y, seed) * amount)))
    return (g, g, min(255, g + 5), 255)


def side_texture():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y, row in enumerate(SIDE):
        for x, ch in enumerate(row):
            v = PALETTE.get(ch, PALETTE['D'])
            px[x, y] = _pixel(v, x, y, 11, 4 if ch in 'pqf' else 2)
    return img


def dark_texture():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = _pixel(PALETTE['D'], x, y, 1, 2)
    return img


def ring(x, y):
    """How far a texel is from the edge of the 16x16 tile (0 = outermost)."""
    return min(x, y, 15 - x, 15 - y)


def base_top_texture():
    """Tops of the two base steps: a lit rim on each step's edge (rings 0 and 1), dark inside."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            k = ring(x, y)
            v = PALETTE['r'] if k <= 1 else PALETTE['d'] if k == 2 else PALETTE['D']
            px[x, y] = _pixel(v, x, y, 3, 2)
    return img


def top_texture():
    """Top of the pedestal: lit rim, dark frame, a pale stone face with a soft bevel."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            k = ring(x, y)
            if k <= 1:
                v = PALETTE['r']
            elif k == 2:
                v = PALETTE['D']
            elif k == 3:
                v = 222 if (x <= 3 or y <= 3) else 176   # lit on two sides, shaded on the others
            else:
                v = 200 + int(_noise(x // 2, y // 2, 9) * 8)
            px[x, y] = _pixel(v, x, y, 5, 3)
    return img


def window_texture():
    """The glowing window: bright, with a lighter streak on the left like the stone's highlight."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (255, 255, 255, 255) if x < 8 else (215, 215, 215, 255)
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
    write('textures/item/altar/ped_side.png', png(side_texture()))
    write('textures/item/altar/ped_dark.png', png(dark_texture()))
    write('textures/item/altar/ped_base_top.png', png(base_top_texture()))
    write('textures/item/altar/ped_top.png', png(top_texture()))
    write('textures/item/altar/ped_window.png', png(window_texture()))
    write('textures/item/altar/sigil.png', png(sigil_texture()))
    write('textures/item/altar/crystal.png', png(crystal_texture()))
    write('textures/item/altar/band.png', png(band_texture()))
    write('textures/item/altar/halo.png', png(halo_texture()))
    print(f'Altar: {len(base["elements"])} base elements, {len(glow["elements"])} glow elements')


if __name__ == '__main__':
    main()
