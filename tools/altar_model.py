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
ASSETS = os.path.join(ROOT, 'resourcepack', 'assets', 'customs')
DEFAULT_TINT = 0xE8C35A  # gold, when nothing is on the altar

# vanilla textures for the stonework
T = {
    'tiles': 'minecraft:block/deepslate_tiles',
    'polished': 'minecraft:block/polished_deepslate',
    'bricks': 'minecraft:block/polished_blackstone_bricks',
    'chiseled': 'minecraft:block/chiseled_polished_blackstone',
    'top': 'minecraft:block/polished_blackstone',
    'gold': 'minecraft:block/gold_block',
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


OBELISKS = ((1.7, 1.7), (14.3, 1.7), (1.7, 14.3), (14.3, 14.3))
PILLARS = ((5.2, 5.2), (10.8, 5.2), (5.2, 10.8), (10.8, 10.8))


def diamond(half, y1, y2, tex, faces='nsewud', tint=False):
    """A square turned 45 degrees about the centre; {@code half} is half its side length."""
    rot = {"angle": 45, "axis": "y", "origin": [8, (y1 + y2) / 2, 8]}
    return box([8 - half, y1, 8 - half], [8 + half, y2, 8 + half], tex, faces=faces, tint=tint, rotation=rot)


def star(square_half, diamond_half, y1, y2, tex, faces='nsewud'):
    """An eight-pointed layer: a square and the same square turned 45 degrees."""
    return [box([8 - square_half, y1, 8 - square_half], [8 + square_half, y2, 8 + square_half], tex, faces=faces),
            diamond(diamond_half, y1, y2, tex, faces=faces)]


def base_elements():
    els = [box([0, 0, 0], [16, 1, 16], sides('tiles', 'polished'))]
    # an eight-pointed stepped plinth, trimmed with gold
    els += star(6.6, 5.3, 1, 2.2, sides('bricks', 'top'))
    els += star(6.1, 4.95, 2.2, 2.6, sides('polished'))
    els += star(5.1, 4.1, 2.6, 4.4, sides('bricks', 'top'))
    els += star(5.2, 4.18, 4.4, 4.65, 'gold')
    els += star(5.0, 4.0, 4.65, 4.8, sides('polished'))
    # four pillars around a glowing core (the core crystal is in the glow layer)
    for x, z in PILLARS:
        els.append(box([x - 0.8, 4.8, z - 0.8], [x + 0.8, 10.8, z + 0.8], 'polished', faces='nsew'))
        els.append(box([x - 1.0, 4.8, z - 1.0], [x + 1.0, 5.4, z + 1.0], sides('polished'), faces='nsewu'))
        els.append(box([x - 1.0, 10.2, z - 1.0], [x + 1.0, 10.8, z + 1.0], 'gold', faces='nsewd'))
    els.append(box([6.6, 4.8, 6.6], [9.4, 5.2, 9.4], 'chiseled', faces='u'))
    # capital: narrow, then flaring out into the table
    els += star(5.3, 4.25, 10.8, 11.2, 'gold')
    els += star(5.0, 4.0, 11.2, 12.4, sides('bricks', 'top'))
    els += star(6.7, 5.4, 12.4, 13.5, sides('chiseled', 'top'))
    els += star(6.9, 5.55, 13.5, 13.8, 'gold', faces='nsewd')
    els.append(box([1.4, 13.8, 1.4], [14.6, 13.85, 14.6], 'top', faces='u'))
    # obelisks on the corners of the foot; crystals float above them (glow layer)
    for x, z in OBELISKS:
        els.append(box([x - 0.9, 1, z - 0.9], [x + 0.9, 1.8, z + 0.9], sides('bricks', 'top')))
        els.append(box([x - 0.6, 1.8, z - 0.6], [x + 0.6, 6.6, z + 0.6], 'polished', faces='nsew'))
        els.append(box([x - 0.75, 6.6, z - 0.75], [x + 0.75, 7.0, z + 0.75], 'gold'))
        els.append(box([x - 0.4, 7.0, z - 0.4], [x + 0.4, 7.5, z + 0.4], 'gold', faces='nsewu'))
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
    # the core: a tall crystal standing between the pillars
    els += crystal(8, 7.9, 8, 2.6)
    # glyph bands on the four faces of the plinth body
    els.append(full_uv(box([3.4, 3.0, 2.88], [12.6, 4.0, 2.89], 'band', faces='n', tint=True)))
    els.append(full_uv(box([3.4, 3.0, 13.11], [12.6, 4.0, 13.12], 'band', faces='s', tint=True)))
    els.append(full_uv(box([2.88, 3.0, 3.4], [2.89, 4.0, 12.6], 'band', faces='w', tint=True)))
    els.append(full_uv(box([13.11, 3.0, 3.4], [13.12, 4.0, 12.6], 'band', faces='e', tint=True)))
    # rune plates on the capital's faces
    els.append(full_uv(box([5.2, 12.55, 1.28], [10.8, 13.35, 1.29], 'band', faces='n', tint=True)))
    els.append(full_uv(box([5.2, 12.55, 14.71], [10.8, 13.35, 14.72], 'band', faces='s', tint=True)))
    els.append(full_uv(box([1.28, 12.55, 5.2], [1.29, 13.35, 10.8], 'band', faces='w', tint=True)))
    els.append(full_uv(box([14.71, 12.55, 5.2], [14.72, 13.35, 10.8], 'band', faces='e', tint=True)))
    # sigil inlaid in the table top
    els.append(full_uv(box([2, 13.87, 2], [14, 13.89, 14], 'sigil', faces='u', tint=True)))
    # crystals floating over the obelisks
    for x, z in OBELISKS:
        els.extend(crystal(x, 9.4, z, 1.1))
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
