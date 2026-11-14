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

# Materials for the pedestal: vanilla stone textures (referenced by name, not copied) so every
# surface has real stone detail; plus the plugin's own glow textures.
T = {
    'deep': 'minecraft:block/polished_deepslate',
    'deep_tiles': 'minecraft:block/deepslate_tiles',
    'stone': 'minecraft:block/smooth_stone',
    'calcite': 'minecraft:block/calcite',
    'window': 'customs:item/altar/ped_window',
    'rune': 'customs:item/altar/rune',
    'sigil': 'customs:item/altar/sigil',
    'crystal': 'customs:item/altar/crystal',
    'band': 'customs:item/altar/band',
    'halo': 'customs:item/altar/halo',
}


def positional_uv(face, frm, to):
    """UVs taken from the face's position in the block, so neighbouring faces line up like real blocks."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    uv = {
        'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1],
        'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        'up': [x1, z1, x2, z2], 'down': [x1, 16 - z2, x2, 16 - z1],
    }[face]
    return [round(max(0, min(16, v)), 3) for v in uv]


def box(frm, to, tex, faces='nsewud', uv_from_pos=True, tint=False, rotation=None):
    """An element; side faces use #tex, or per-face overrides when tex is a dict."""
    names = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}
    out = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        face = names[f]
        t = tex[face] if isinstance(tex, dict) else tex
        entry = {"texture": "#" + t}
        entry["uv"] = positional_uv(face, frm, to)
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


FACE_TURN = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}


def turn(element, quarter):
    """Rotates an element a quarter turn (or several) around the block's vertical centre line.

    Side faces move round with it (north -> east -> south -> west) and positional UVs are worked
    out again for the new position, so the stone texture lines up on every side."""
    out = json.loads(json.dumps(element))
    (x1, y1, z1), (x2, y2, z2) = element['from'], element['to']
    faces = dict(element['faces'])
    for _ in range(quarter % 4):
        x1, z1, x2, z2 = 16 - z1, x1, 16 - z2, x2
        faces = {FACE_TURN[name]: face for name, face in faces.items()}
    frm = [min(x1, x2), y1, min(z1, z2)]
    to = [max(x1, x2), y2, max(z1, z2)]
    out['from'], out['to'] = frm, to
    out['faces'] = {}
    for name, face in faces.items():
        face = dict(face)
        if face.get('uv') != [0, 0, 16, 16]:
            face['uv'] = positional_uv(name, frm, to)
        out['faces'][name] = face
    return out


def trim(frm, to):
    """A raised strip of polished deepslate on the north face of the pillar (turned for the others)."""
    return box(frm, to, 'deep', faces='nsewud')


# pillar: x 3..13, y 3..13, z 3..13. Trims stand 0.45 px proud of its north face (z 3).
PROUD = 0.45


def face_trims():
    z0, z1 = 3 - PROUD, 3.0
    t = []
    # frame lines running up the face, 1 px in from each corner
    t.append(trim([4, 3, z0], [5, 11, z1]))
    t.append(trim([11, 3, z0], [12, 11, z1]))
    # ... flaring out to the corners under the capital (two steps), like brackets
    t.append(trim([3.5, 11, z0], [5, 12, z1]))
    t.append(trim([11, 11, z0], [12.5, 12, z1]))
    t.append(trim([3, 12, z0], [5.5, 13, z1]))
    t.append(trim([10.5, 12, z0], [13, 13, z1]))
    # a frame line along the bottom
    t.append(trim([5, 3, z0], [11, 4, z1]))
    # the panel: a raised border around a recessed window
    t.append(trim([6, 5, z0], [10, 6, z1]))
    t.append(trim([6, 10, z0], [10, 11, z1]))
    t.append(trim([6, 6, z0], [7, 10, z1]))
    t.append(trim([9, 6, z0], [10, 10, z1]))
    return t


def base_elements():
    els = [
        # two-tier dark base
        box([0, 0, 0], [16, 2, 16], 'deep'),
        box([1, 2, 1], [15, 3, 15], 'deep_tiles'),
        # the pale pillar
        box([3, 3, 3], [13, 13, 13], 'stone', faces='nsew'),
        # capital and heavy top slab
        box([2, 13, 2], [14, 14, 14], 'deep', faces='nsewd'),
        box([1, 14, 1], [15, 16, 15], 'deep'),
        # the pale top, set into the slab
        box([2.5, 16, 2.5], [13.5, 16.25, 13.5], 'calcite', faces='nsewu'),
    ]
    for quarter in range(4):
        els.extend(turn(t, quarter) for t in face_trims())
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
    """The window in each panel glows in the weapon's colour: x 7..9, y 6..10, just off the pillar face."""
    pane = full_uv(box([7, 6, 3 - 0.05], [9, 10, 3 - 0.04], 'window', faces='n', tint=True))
    return [turn(pane, quarter) for quarter in range(4)]


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
    tex["particle"] = T["stone"]
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


WINDOW_RUNE = [
    "...##...",
    "..#..#..",
    ".#.##.#.",
    "#.#..#.#",
    ".#.##.#.",
    "..#..#..",
    "...##...",
    "...##...",
    ".#.##.#.",
    "..####..",
    "...##...",
    ".#.##.#.",
    "..####..",
    "...##...",
    "...##...",
    "..####..",
]


def window_texture():
    """The glowing window: a coloured crystal pane (the tint) with a bright rune carved down it.

    8 x 16 pixels drawn at double size, so it fills the 2 x 4 px window with crisp detail."""
    img = Image.new('RGBA', (16, 32))
    px = img.load()
    for y in range(16):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 15)
            base = 120 if edge else 150 + int(40 * (1 - abs(y - 7.5) / 7.5))
            v = 255 if WINDOW_RUNE[y][x] == '#' else base
            for dy in range(2):
                for dx in range(2):
                    px[x * 2 + dx, y * 2 + dy] = (v, v, v, 255)
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
    write('textures/item/altar/ped_window.png', png(window_texture()))
    write('textures/item/altar/sigil.png', png(sigil_texture()))
    write('textures/item/altar/crystal.png', png(crystal_texture()))
    write('textures/item/altar/band.png', png(band_texture()))
    write('textures/item/altar/halo.png', png(halo_texture()))
    print(f'Altar: {len(base["elements"])} base elements, {len(glow["elements"])} glow elements')


if __name__ == '__main__':
    main()
