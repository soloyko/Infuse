"""Generates the Weapon Altar models and textures in resourcepack/.

    python3 tools/altar_model.py

The altar is one block: a weathered stone altar (altar_base) plus a separate layer of carved
runes, a sigil and candle flames (altar_glow) that the plugin renders at full brightness and tints
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

# vanilla textures for the stonework
T = {
    'bricks': 'minecraft:block/stone_bricks',
    'mossy': 'minecraft:block/mossy_stone_bricks',
    'cracked': 'minecraft:block/cracked_stone_bricks',
    'chiseled': 'minecraft:block/chiseled_stone_bricks',
    'smooth': 'minecraft:block/smooth_stone',
    'slab_side': 'minecraft:block/smooth_stone_slab_side',
    'andesite': 'minecraft:block/polished_andesite',
    'cobble': 'minecraft:block/cobblestone',
    'mossy_cobble': 'minecraft:block/mossy_cobblestone',
    'stone': 'minecraft:block/stone',
    'candle': 'minecraft:block/candle',
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


def faces(n, s_, e, w, u=None, d=None):
    """Per-face textures for an element."""
    out = {'north': n, 'south': s_, 'east': e, 'west': w}
    out['up'] = u or n
    out['down'] = d or u or n
    return out


CANDLES = ((2.6, 2.6, 3.4), (13.4, 2.6, 2.6), (2.6, 13.4, 2.2), (13.4, 13.4, 3.0))


def candle(x, z, height, top):
    """A white candle standing on the slab (vanilla candle texture, its own UVs)."""
    w = 0.65
    el = box([x - w, top, z - w], [x + w, top + height, z + w], 'candle', faces='nsewu')
    for name, face in el['faces'].items():
        face['uv'] = [0, 6, 2, 8] if name == 'up' else [0, 8, 2, 8 + min(6, height * 1.6)]
    return el


def base_elements():
    els = []
    # rough footing of cobblestone, a little uneven at the edges
    els.append(box([0, 0, 0], [16, 1.2, 16], faces('mossy_cobble', 'cobble', 'cobble', 'mossy_cobble', 'cobble', 'cobble')))
    els.append(box([0.5, 1.2, 0.5], [15.5, 1.6, 15.5], faces('mossy_cobble', 'cobble', 'mossy_cobble', 'cobble', 'cobble')))
    # the step: stone bricks, mossy on one side, cracked on another
    els.append(box([1.5, 1.6, 1.5], [14.5, 4.2, 14.5], faces('mossy', 'bricks', 'cracked', 'bricks', 'smooth', 'bricks')))
    # a chamfer of smooth stone on the step's edge
    els.append(box([1.2, 4.2, 1.2], [14.8, 4.7, 14.8], faces('slab_side', 'slab_side', 'slab_side', 'slab_side', 'smooth', 'smooth')))
    # the pillar: carved stone, narrower than the slab it holds
    els.append(box([3.6, 4.7, 3.6], [12.4, 10.6, 12.4], faces('chiseled', 'chiseled', 'chiseled', 'chiseled', 'smooth', 'smooth'),
                   faces='nsew'))
    # thick corner buttresses holding the slab up
    for x, z in ((3.0, 3.0), (13.0, 3.0), (3.0, 13.0), (13.0, 13.0)):
        tex = 'mossy' if (x + z) % 5 == 1 else 'bricks'
        els.append(box([x - 1.1, 4.7, z - 1.1], [x + 1.1, 10.6, z + 1.1], faces(tex, 'bricks', 'cracked', tex), faces='nsew'))
    # the capital and the slab: a heavy table of smooth stone
    els.append(box([2.4, 10.6, 2.4], [13.6, 11.4, 13.6], faces('bricks', 'bricks', 'bricks', 'bricks', 'smooth', 'smooth')))
    els.append(box([0.9, 11.4, 0.9], [15.1, 13.6, 15.1], faces('slab_side', 'slab_side', 'slab_side', 'slab_side', 'smooth', 'smooth')))
    # a worn lip around the top, slightly inset
    els.append(box([1.6, 13.6, 1.6], [14.4, 13.9, 14.4], faces('andesite', 'andesite', 'andesite', 'andesite', 'andesite', 'andesite'), faces='nsewu'))
    # a chipped corner of the slab, and rubble at the foot
    els.append(box([13.6, 12.8, 0.9], [15.1, 13.6, 2.6], 'cobble', faces='nsewu'))
    for x, z, sx, h in ((0.6, 6.0, 1.6, 1.0), (14.2, 10.5, 1.4, 0.8), (9.0, 0.4, 1.8, 0.7), (4.0, 14.6, 1.3, 0.9)):
        els.append(box([x, 1.2, z], [x + sx, 1.2 + h, z + sx * 0.8], 'cobble', faces='nsewu'))
    # candles on the slab corners
    for x, z, h in CANDLES:
        els.append(candle(x, z, h, 13.9))
    return els


def crystal(x, y, z, size):
    """A small flame: a prism turned 45 degrees with a pointed top."""
    rot = {"angle": 45, "axis": "y", "origin": [x, y, z]}
    h = size
    return [
        full_uv(box([x - h * 0.45, y - h * 0.6, z - h * 0.45], [x + h * 0.45, y + h * 0.6, z + h * 0.45], 'crystal',
                    faces='nsewud', tint=True, rotation=rot)),
        full_uv(box([x - h * 0.25, y + h * 0.6, z - h * 0.25], [x + h * 0.25, y + h * 1.3, z + h * 0.25], 'crystal',
                    faces='nsewu', tint=True, rotation=rot)),
    ]


def glow_elements():
    els = []
    # runes carved into the four faces of the pillar, glowing faintly
    els.append(full_uv(box([5.0, 5.6, 3.58], [11.0, 9.8, 3.59], 'rune', faces='n', tint=True)))
    els.append(full_uv(box([5.0, 5.6, 12.41], [11.0, 9.8, 12.42], 'rune', faces='s', tint=True)))
    els.append(full_uv(box([3.58, 5.6, 5.0], [3.59, 9.8, 11.0], 'rune', faces='w', tint=True)))
    els.append(full_uv(box([12.41, 5.6, 5.0], [12.42, 9.8, 11.0], 'rune', faces='e', tint=True)))
    # a line of glyphs cut into the front of the step
    els.append(full_uv(box([3.0, 2.4, 1.48], [13.0, 3.4, 1.49], 'band', faces='n', tint=True)))
    els.append(full_uv(box([3.0, 2.4, 14.51], [13.0, 3.4, 14.52], 'band', faces='s', tint=True)))
    els.append(full_uv(box([1.48, 2.4, 3.0], [1.49, 3.4, 13.0], 'band', faces='w', tint=True)))
    els.append(full_uv(box([14.51, 2.4, 3.0], [14.52, 3.4, 13.0], 'band', faces='e', tint=True)))
    # a circle carved into the slab
    els.append(full_uv(box([2.2, 13.92, 2.2], [13.8, 13.94, 13.8], 'sigil', faces='u', tint=True)))
    # candle flames in the weapon's colour
    for x, z, h in CANDLES:
        els.extend(crystal(x, 13.9 + h + 0.8, z, 1.15))
    return els


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
    tex["particle"] = T["bricks"]
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
    write('textures/item/altar/sigil.png', png(sigil_texture()))
    write('textures/item/altar/crystal.png', png(crystal_texture()))
    write('textures/item/altar/band.png', png(band_texture()))
    write('textures/item/altar/halo.png', png(halo_texture()))
    print(f'Altar: {len(base["elements"])} base elements, {len(glow["elements"])} glow elements')


if __name__ == '__main__':
    main()
