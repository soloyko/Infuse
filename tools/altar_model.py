"""Generates the Weapon Altar models and textures in resourcepack/.

    python3 tools/altar_model.py

The altar is one block: a stone and gold pedestal (altar_base) plus a separate layer of runes,
a sigil and floating crystals (altar_glow) that the plugin renders at full brightness and tints
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
ASSETS = os.path.join(ROOT, 'resourcepack', 'assets', 'op_swords')
DEFAULT_TINT = 0xE8C35A  # gold, when nothing is on the altar

# vanilla textures for the stonework
T = {
    'tiles': 'minecraft:block/deepslate_tiles',
    'polished': 'minecraft:block/polished_deepslate',
    'bricks': 'minecraft:block/polished_blackstone_bricks',
    'chiseled': 'minecraft:block/chiseled_polished_blackstone',
    'top': 'minecraft:block/polished_blackstone',
    'gold': 'minecraft:block/gold_block',
    'rune': 'op_swords:item/altar/rune',
    'sigil': 'op_swords:item/altar/sigil',
    'crystal': 'op_swords:item/altar/crystal',
    'band': 'op_swords:item/altar/band',
    'halo': 'op_swords:item/altar/halo',
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


SPIRES = ((1.6, 1.6), (14.4, 1.6), (1.6, 14.4), (14.4, 14.4))
PILLARS = ((4, 4), (12, 4), (4, 12), (12, 12))


def base_elements():
    els = [
        # stepped plinth
        box([0, 0, 0], [16, 1, 16], sides('tiles', 'polished')),
        box([0.75, 1, 0.75], [15.25, 2, 15.25], sides('polished')),
        box([1.25, 2, 1.25], [14.75, 2.5, 14.75], 'gold'),
        box([2, 2.5, 2], [14, 4.5, 14], sides('bricks', 'top')),
        box([2.75, 4.5, 2.75], [13.25, 5, 13.25], 'gold'),
        # the core
        box([4.5, 5, 4.5], [11.5, 11, 11.5], 'chiseled', faces='nsew'),
        # capital and table
        box([2.75, 11, 2.75], [13.25, 11.6, 13.25], 'gold'),
        box([2, 11.6, 2], [14, 13.4, 14], sides('bricks', 'top')),
        box([1.75, 13.4, 1.75], [14.25, 13.9, 14.25], 'gold'),
        box([2.5, 13.9, 2.5], [13.5, 14.0, 13.5], 'top', faces='u'),
    ]
    # four slim pillars around the core, with gold bases and caps
    for x, z in PILLARS:
        els.append(box([x - 0.75, 5.6, z - 0.75], [x + 0.75, 10.4, z + 0.75], 'polished', faces='nsew'))
        els.append(box([x - 1, 5, z - 1], [x + 1, 5.6, z + 1], 'gold', faces='nsewu'))
        els.append(box([x - 1, 10.4, z - 1], [x + 1, 11, z + 1], 'gold', faces='nsewd'))
    # gold claws on the table corners
    for x, z in ((2.5, 2.5), (13.5, 2.5), (2.5, 13.5), (13.5, 13.5)):
        els.append(box([x - 0.5, 13.9, z - 0.5], [x + 0.5, 14.9, z + 0.5], 'gold', faces='nsewu'))
    # corner spires on the plinth; their crystals float above them (glow layer)
    for x, z in SPIRES:
        els.append(box([x - 1, 2, z - 1], [x + 1, 3, z + 1], sides('bricks', 'top')))
        els.append(box([x - 0.7, 3, z - 0.7], [x + 0.7, 7.4, z + 0.7], 'polished', faces='nsew'))
        els.append(box([x - 0.9, 7.4, z - 0.9], [x + 0.9, 7.9, z + 0.9], 'gold'))
        els.append(box([x - 0.55, 7.9, z - 0.55], [x + 0.55, 8.3, z + 0.55], 'gold', faces='nsewu'))
    return els


def crystal(x, y, z, size):
    """A floating crystal: a prism with a short tip below and a longer one above, turned 45 degrees."""
    rot = {"angle": 45, "axis": "y", "origin": [x, y, z]}
    h = size
    return [
        full_uv(box([x - h * 0.5, y - h * 0.9, z - h * 0.5], [x + h * 0.5, y + h * 0.9, z + h * 0.5], 'crystal',
                    faces='nsew', tint=True, rotation=rot)),
        full_uv(box([x - h * 0.28, y - h * 1.35, z - h * 0.28], [x + h * 0.28, y - h * 0.9, z + h * 0.28], 'crystal',
                    faces='nsewd', tint=True, rotation=rot)),
        full_uv(box([x - h * 0.28, y + h * 0.9, z - h * 0.28], [x + h * 0.28, y + h * 1.6, z + h * 0.28], 'crystal',
                    faces='nsewu', tint=True, rotation=rot)),
    ]


def glow_elements():
    els = []
    # rune panels on the core, between the pillars
    els.append(full_uv(box([4.75, 5.9, 4.47], [11.25, 10.1, 4.49], 'rune', faces='n', tint=True)))
    els.append(full_uv(box([4.75, 5.9, 11.51], [11.25, 10.1, 11.53], 'rune', faces='s', tint=True)))
    els.append(full_uv(box([4.47, 5.9, 4.75], [4.49, 10.1, 11.25], 'rune', faces='w', tint=True)))
    els.append(full_uv(box([11.51, 5.9, 4.75], [11.53, 10.1, 11.25], 'rune', faces='e', tint=True)))
    # a band of glyphs around the lower body
    els.append(full_uv(box([3, 3.0, 1.98], [13, 4.0, 1.99], 'band', faces='n', tint=True)))
    els.append(full_uv(box([3, 3.0, 14.01], [13, 4.0, 14.02], 'band', faces='s', tint=True)))
    els.append(full_uv(box([1.98, 3.0, 3], [1.99, 4.0, 13], 'band', faces='w', tint=True)))
    els.append(full_uv(box([14.01, 3.0, 3], [14.02, 4.0, 13], 'band', faces='e', tint=True)))
    # sigil inlaid in the table top
    els.append(full_uv(box([3, 14.02, 3], [13, 14.04, 13], 'sigil', faces='u', tint=True)))
    # crystals floating over the spires
    for x, z in SPIRES:
        els.extend(crystal(x, 10.4, z, 1.25))
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
    tex["particle"] = T["polished"]
    return {"credit": "OP Swords altar - generated by tools/altar_model.py", "textures": tex,
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
        'altar_base': {"model": {"type": "minecraft:model", "model": "op_swords:item/altar_base"}},
        'altar_glow': {"model": {"type": "minecraft:model", "model": "op_swords:item/altar_glow", "tints": tint}},
        'altar_halo': {"model": {"type": "minecraft:model", "model": "op_swords:item/altar_halo", "tints": tint}},
        'altar': {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": "op_swords:item/altar_base"},
            {"type": "minecraft:model", "model": "op_swords:item/altar_glow", "tints": tint}]}},
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
