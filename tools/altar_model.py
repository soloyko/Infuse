"""Generates the Weapon Altar models and textures in resourcepack/.

    python3 tools/altar_model.py

The altar is one block: a stone and gold pedestal (altar_base) plus a separate layer of runes,
a sigil and crystals (altar_glow) that the plugin renders at full brightness and tints with the
colour of the weapon on display. The inventory item (altar) shows both layers.
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


def base_elements():
    els = [
        # foot and step
        box([0, 0, 0], [16, 2, 16], {'north': 'tiles', 'south': 'tiles', 'east': 'tiles', 'west': 'tiles', 'up': 'polished', 'down': 'polished'}),
        box([1.5, 2, 1.5], [14.5, 3.6, 14.5], {'north': 'bricks', 'south': 'bricks', 'east': 'bricks', 'west': 'bricks', 'up': 'top', 'down': 'top'}),
        box([1.25, 3.6, 1.25], [14.75, 4.1, 14.75], 'gold'),
        # the column
        box([3.6, 4.1, 3.6], [12.4, 5, 12.4], 'gold'),
        box([4, 5, 4], [12, 10.2, 12], 'chiseled', faces='nsew'),
        box([3.6, 10.2, 3.6], [12.4, 11, 12.4], 'gold'),
        # the table
        box([3, 11, 3], [13, 13.2, 13], {'north': 'bricks', 'south': 'bricks', 'east': 'bricks', 'west': 'bricks', 'up': 'top', 'down': 'top'}),
        box([2.75, 13.2, 2.75], [13.25, 13.7, 13.25], 'gold', faces='nsewd'),
        box([4, 13.2, 4], [12, 13.7, 12], 'top', faces='u'),
    ]
    # corner posts that hold the crystals
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        els.append(box([x, 4.1, z], [x + 2, 8.5, z + 2], 'bricks', faces='nsewu'))
        els.append(box([x - 0.2, 8.5, z - 0.2], [x + 2.2, 8.9, z + 2.2], 'gold'))
    # gold claws over the table corners
    for x, z in ((3, 3), (12, 3), (3, 12), (12, 12)):
        els.append(box([x, 13.7, z], [x + 1, 14.4, z + 1], 'gold', faces='nsewu'))
    return els


def glow_elements():
    els = []
    # rune plates on the four faces of the column
    els.append(full_uv(box([5, 5.8, 3.95], [11, 9.4, 3.98], 'rune', faces='n', tint=True)))
    els.append(full_uv(box([5, 5.8, 12.02], [11, 9.4, 12.05], 'rune', faces='s', tint=True)))
    els.append(full_uv(box([3.95, 5.8, 5], [3.98, 9.4, 11], 'rune', faces='w', tint=True)))
    els.append(full_uv(box([12.02, 5.8, 5], [12.05, 9.4, 11], 'rune', faces='e', tint=True)))
    # sigil inlaid in the table top
    els.append(full_uv(box([4.25, 13.72, 4.25], [11.75, 13.75, 11.75], 'sigil', faces='u', tint=True)))
    # crystals on the posts, turned 45 degrees
    for x, z in ((2, 2), (14, 2), (2, 14), (14, 14)):
        els.append(full_uv(box([x - 0.7, 8.9, z - 0.7], [x + 0.7, 11.6, z + 0.7], 'crystal', faces='nsewu', tint=True,
                               rotation={"angle": 45, "axis": "y", "origin": [x, 10, z]})))
        els.append(full_uv(box([x - 0.35, 11.6, z - 0.35], [x + 0.35, 12.4, z + 0.35], 'crystal', faces='nsewu', tint=True,
                               rotation={"angle": 45, "axis": "y", "origin": [x, 10, z]})))
    return els


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
    write('models/item/altar_base.json', json.dumps(base, separators=(',', ':')).encode() + b'\n')
    write('models/item/altar_glow.json', json.dumps(glow, separators=(',', ':')).encode() + b'\n')
    tint = [{"type": "minecraft:custom_model_data", "index": 0, "default": DEFAULT_TINT}]
    items = {
        'altar_base': {"model": {"type": "minecraft:model", "model": "op_swords:item/altar_base"}},
        'altar_glow': {"model": {"type": "minecraft:model", "model": "op_swords:item/altar_glow", "tints": tint}},
        'altar': {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": "op_swords:item/altar_base"},
            {"type": "minecraft:model", "model": "op_swords:item/altar_glow", "tints": tint}]}},
    }
    for name, item in items.items():
        write(f'items/{name}.json', json.dumps(item, indent=2).encode() + b'\n')
    write('textures/item/altar/rune.png', png(rune_texture()))
    write('textures/item/altar/sigil.png', png(sigil_texture()))
    write('textures/item/altar/crystal.png', png(crystal_texture()))
    print(f'Altar: {len(base["elements"])} base elements, {len(glow["elements"])} glow elements')


if __name__ == '__main__':
    main()
