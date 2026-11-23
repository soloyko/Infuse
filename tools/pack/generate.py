#!/usr/bin/env python3
"""Generates the BlackCloverSMP resource pack (textures, 3D models, item definitions).

Run from the repository root:  python3 tools/pack/generate.py
Output: src/main/resources/resourcepack/  (bundled into the plugin jar and served to players)

All art is drawn procedurally here, so the pack is reproducible and easy to tweak.
"""
import json
import math
import random
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "resourcepack"
NS = "blackclover"
PACK_FORMAT = 75  # Minecraft 1.21.11

# ----------------------------------------------------------------------------- grimoire palette
# key, leather colour, emblem style, metal, rune
GRIMOIRES = [
    ("fire", (150, 34, 22), "gold", "gold", "flame"),
    ("water", (28, 70, 150), "gold", "silver", "drop"),
    ("wind", (36, 120, 92), "gold", "silver", "swirl"),
    ("lightning", (176, 140, 24), "gold", "gold", "bolt"),
    ("earth", (110, 72, 38), "gold", "gold", "mountain"),
    ("plant", (40, 112, 40), "gold", "gold", "leaf"),
    ("light", (226, 214, 172), "gold", "gold", "sun"),
    ("dark", (52, 26, 82), "gold", "silver", "moon"),
    ("spatial", (20, 112, 120), "gold", "silver", "portal"),
    ("gravity", (46, 44, 120), "gold", "silver", "gravity"),
    ("blood", (120, 12, 28), "gold", "gold", "blood"),
    ("poison", (96, 120, 20), "gold", "silver", "skull"),
    ("time", (150, 120, 70), "gold", "gold", "hourglass"),
    ("anti_magic", (30, 30, 32), "tattered", "iron", "sword"),
    ("five_leaf", (18, 14, 16), "devil", "devil", "horns"),
]

GOLD = [(255, 236, 140), (240, 196, 72), (196, 140, 36), (120, 80, 20)]
SILVER = [(250, 250, 255), (206, 210, 222), (150, 156, 172), (88, 92, 108)]
IRON = [(150, 150, 156), (104, 104, 110), (70, 70, 76), (40, 40, 44)]
DEVIL = [(255, 96, 96), (200, 30, 40), (120, 10, 20), (50, 4, 10)]
METALS = {"gold": GOLD, "silver": SILVER, "iron": IRON, "devil": DEVIL}


def clamp(v):
    return max(0, min(255, int(v)))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noisy(rng, rgb, amount):
    n = rng.randint(-amount, amount)
    return tuple(clamp(c + n) for c in rgb) + (255,)


def save(img, rel):
    path = OUT / "assets" / NS / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write_json(rel, data):
    path = OUT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


# ----------------------------------------------------------------------------- rune icons (7x6)
RUNES = {
    "flame": ["...#...", "..##...", "..###..", ".#####.", ".##.##.", "..###.."],
    "drop": ["...#...", "..###..", ".#####.", ".#####.", ".#####.", "..###.."],
    "swirl": [".####..", "#....#.", "#..#.#.", "#.##..#", ".#...#.", "..###.."],
    "bolt": ["...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
    "mountain": ["...#...", "..###..", "..#.#..", ".#...#.", "#..#..#", "#######"],
    "leaf": ["....##.", "..####.", ".#####.", ".####..", ".##....", "#......"],
    "sun": ["#..#..#", ".#.#.#.", "..###..", "###.###", "..###..", ".#.#.#."],
    "moon": ["..###..", ".##....", "##.....", "##.....", ".##....", "..###.."],
    "portal": ["..###..", ".#...#.", "#..#..#", "#.#.#.#", ".#...#.", "..###.."],
    "gravity": ["...#...", "...#...", ".#.#.#.", "..###..", "...#...", "#######"],
    "blood": ["...#...", "..###..", ".##.##.", ".#####.", ".#####.", "..###.."],
    "skull": [".#####.", "#######", "#.#.#.#", "#######", ".#.#.#.", ".#####."],
    "hourglass": ["#######", ".#...#.", "..#.#..", "..#.#..", ".#.#.#.", "#######"],
    "sword": ["......#", ".....#.", "#..##..", ".###...", "..#....", ".#.#..."],
    "horns": ["#.....#", "#.....#", ".#...#.", ".##.##.", "..###..", "...#..."],
}


def draw_rune(img, name, x0, y0, color, shadow):
    for y, row in enumerate(RUNES[name]):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x0 + x + 1, y0 + y + 1), shadow + (255,))
    for y, row in enumerate(RUNES[name]):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x0 + x, y0 + y), color + (255,))


# ----------------------------------------------------------------------------- cover texture (32x32)
# Layout (pixels): front/back cover art 0..20 x 0..28 (10x14 model units at 2px/unit),
# spine 20..28 x 0..28, edge strip 28..32.
def cover_texture(key, leather, metal, rune, tattered):
    rng = random.Random("cover-" + key)
    img = Image.new("RGBA", (32, 32))
    dark = shade(leather, 0.62)
    darker = shade(leather, 0.45)
    light = shade(leather, 1.18) if sum(leather) < 600 else shade(leather, 1.04)
    m = METALS[metal]
    for y in range(32):
        for x in range(32):
            img.putpixel((x, y), noisy(rng, leather, 7))
    # front panel tooling: dark outer edge, metal inner frame line, light bevel
    for x in range(20):
        for y in range(28):
            if x in (0, 19) or y in (0, 27):
                img.putpixel((x, y), darker + (255,))
            elif x in (1, 18) or y in (1, 26):
                img.putpixel((x, y), noisy(rng, light, 4))
            elif (x in (3, 16) and 3 <= y <= 24) or (y in (3, 24) and 3 <= x <= 16):
                img.putpixel((x, y), m[2] + (255,))
            elif (x in (4, 15) and 4 <= y <= 23) or (y in (4, 23) and 4 <= x <= 15):
                img.putpixel((x, y), noisy(rng, dark, 5))
    # little diamond studs at the frame corners
    for (cx, cy) in ((3, 3), (16, 3), (3, 24), (16, 24)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            img.putpixel((cx + dx, cy + dy), m[1] + (255,))
        img.putpixel((cx, cy), m[0] + (255,))
    # rune below the emblem area
    draw_rune(img, rune, 6, 18, m[1], shade(m[3], 0.8))  # rows 18-23 = 3.5..6 units, under the emblem
    # spine with raised bands
    for x in range(20, 28):
        for y in range(28):
            base = mix(leather, dark, 0.25 + 0.15 * math.sin((x - 20) / 7 * math.pi))
            img.putpixel((x, y), noisy(rng, base, 6))
    for band in (3, 9, 18, 24):
        for x in range(20, 28):
            img.putpixel((x, band), m[2] + (255,))
            img.putpixel((x, band + 1), m[3] + (255,))
            img.putpixel((x, band - 1), m[1] + (255,))
    # rune on the spine too
    for y, row in enumerate(RUNES[rune]):
        for x, ch in enumerate(row):
            if ch == "#" and x < 7:
                img.putpixel((20 + min(x, 6) + 0, 11 + y), m[1] + (255,))
    if tattered:
        # scratches, tears and worn corners
        for _ in range(26):
            x, y = rng.randint(0, 19), rng.randint(0, 27)
            length = rng.randint(2, 5)
            for i in range(length):
                px, py = x + i, y + i // 2
                if 0 <= px < 20 and 0 <= py < 28:
                    img.putpixel((px, py), (70, 66, 64, 255))
        for (cx, cy) in ((0, 0), (19, 0), (0, 27), (19, 27)):
            for i in range(4):
                for j in range(4 - i):
                    px = cx + (i if cx == 0 else -i)
                    py = cy + (j if cy == 0 else -j)
                    img.putpixel((px, py), (90, 84, 78, 255))
    return img


def pages_texture():
    rng = random.Random("pages")
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (236, 224, 190) if y % 2 == 0 else (214, 200, 162)
            img.putpixel((x, y), noisy(rng, base, 6))
    return img


def metal_texture(metal):
    m = METALS[metal]
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            c = m[0] if (x + y) % 7 == 0 else mix(m[1], m[2], t)
            img.putpixel((x, y), c + (255,))
    for i in range(16):
        img.putpixel((i, 0), m[0] + (255,))
        img.putpixel((0, i), m[0] + (255,))
        img.putpixel((i, 15), m[3] + (255,))
        img.putpixel((15, i), m[3] + (255,))
    return img


# ----------------------------------------------------------------------------- emblems (32x32, transparent)
# Hand-placed 16x16 clover sprites.  O outline, H highlight, F fill, M shadow, S stem, R glowing core
CLOVER_3 = [
    "....OOO..OOO....",
    "...OHHFOOHFFO...",
    "...OHFFFFFFMO...",
    "....OFFFFFFO....",
    ".....OFFFFO.....",
    ".OO...OFFO...OO.",
    "OHHO...OO...OHFO",
    "OHFFO..OO..OHFMO",
    ".OFFFOOFFOOFFFO.",
    "OHFFO..OO..OFFMO",
    "OFFMO..SS..OFMMO",
    ".OOO...SS...OOO.",
    ".......SS.......",
    "........SS......",
    "........SS......",
    "................",
]
CLOVER_5 = [
    "......OOOO......",
    ".....OHFFMO.....",
    ".OOO.OFFFFO.OOO.",
    "OHFFO.OFFO.OHFMO",
    "OFFFFO.OO.OFFFMO",
    ".OFFFMOFFOFFFMO.",
    "..OOOOFRRFOOOO..",
    "..OOOOFRRFOOOO..",
    ".OHFFFOFFOHFFFO.",
    "OFFFMO.OO.OFFFMO",
    "OFFMO..SS..OFMMO",
    ".OOO...SS...OOO.",
    ".......SS.......",
    "........SS......",
    "........SS......",
    "................",
]


def emblem_texture(style):
    grid = CLOVER_5 if style == "devil" else CLOVER_3
    if style == "gold":
        pal = {"O": (66, 40, 8), "H": GOLD[0], "F": GOLD[1], "M": GOLD[2], "S": GOLD[2], "R": GOLD[0]}
    elif style == "tattered":
        pal = {"O": (20, 20, 24), "H": (178, 176, 180), "F": (122, 120, 126), "M": (86, 84, 90), "S": (86, 84, 90), "R": (122, 120, 126)}
    else:
        pal = {"O": DEVIL[1], "H": (80, 30, 36), "F": (30, 16, 20), "M": (18, 8, 12), "S": DEVIL[2], "R": DEVIL[0]}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random("emblem-" + style)
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = pal[ch]
            if style == "tattered" and ch in "FH" and rng.random() < 0.2:
                c = (62, 60, 66)
            img.putpixel((x, y), c + (255,))
    if style == "tattered":
        for (x, y) in ((5, 1), (6, 2), (6, 3), (7, 4), (7, 5), (8, 6), (8, 8), (9, 9)):
            if img.getpixel((x, y))[3]:
                img.putpixel((x, y), (10, 10, 12, 255))
    return img


# ----------------------------------------------------------------------------- grimoire model
def face(uv, tex, **extra):
    d = {"uv": uv, "texture": tex}
    d.update(extra)
    return d


def box(frm, to, faces, **extra):
    d = {"from": frm, "to": to, "faces": faces}
    d.update(extra)
    return d


def grimoire_model(key, emblem_style, metal):
    cover = f"{NS}:item/grimoire/{key}_cover"
    # cover faces: front art uv [0,0,10,14]; spine uv [10,0,14,14]; edges uv [14,0,15,14]
    covers = []
    for (z0, z1, front) in ((6.0, 6.75, "north"), (9.25, 10.0, "south")):
        faces = {
            "north": face([10, 0, 0, 14] if front == "north" else [0, 0, 10, 14], "#cover"),
            "south": face([0, 0, 10, 14] if front == "south" else [10, 0, 0, 14], "#cover"),
            "east": face([14, 0, 14.75, 14], "#cover"),
            "west": face([14, 0, 14.75, 14], "#cover"),
            "up": face([0, 0, 10, 0.75], "#cover"),
            "down": face([0, 13.25, 10, 14], "#cover"),
        }
        covers.append(box([3, 1, z0], [13, 15, z1], faces))
    spine = box([2.4, 1, 6], [3.2, 15, 10], {
        "west": face([10, 0, 14, 14], "#cover"),
        "east": face([10, 0, 14, 14], "#cover"),
        "north": face([14, 0, 14.75, 14], "#cover"),
        "south": face([14, 0, 14.75, 14], "#cover"),
        "up": face([10, 0, 14, 0.8], "#cover"),
        "down": face([10, 13.2, 14, 14], "#cover"),
    })
    pages = box([3.2, 1.5, 6.75], [12.6, 14.5, 9.25], {
        "east": face([0, 0, 16, 16], "#pages", rotation=90),
        "up": face([0, 0, 16, 16], "#pages"),
        "down": face([0, 0, 16, 16], "#pages"),
        "north": face([0, 0, 16, 16], "#pages"),
        "south": face([0, 0, 16, 16], "#pages"),
    })
    corners = []
    for (x0, x1) in ((11.6, 13.2), (2.8, 4.4)):
        for (y0, y1) in ((1, 2.6), (13.4, 15)):
            for (z0, z1) in ((5.85, 6.25), (9.75, 10.15)):
                corners.append(box([x0, y0, z0], [x1, y1, z1], {
                    d: face([0, 0, 4, 4], "#metal") for d in ("north", "south", "east", "west", "up", "down")
                }))
    clasp = box([12.8, 6.8, 6.3], [13.6, 9.2, 9.7], {
        d: face([0, 0, 4, 6], "#metal") for d in ("north", "south", "east", "west", "up", "down")
    })
    emblem = box([4.25, 6.3, 10.0], [11.75, 13.8, 10.35], {
        "south": face([0, 0, 16, 16], "#emblem"),
    }, **({"light_emission": 6} if emblem_style == "devil" else {}))
    elements = covers + [spine, pages] + corners + [clasp, emblem]
    return {
        "credit": "BlackCloverSMP",
        "textures": {
            "cover": cover,
            "pages": f"{NS}:item/grimoire/pages",
            "metal": f"{NS}:item/grimoire/metal_{metal}",
            "emblem": f"{NS}:item/grimoire/emblem_{emblem_style}",
            "particle": cover,
        },
        "gui_light": "side",
        "elements": elements,
        "display": {
            "gui": {"rotation": [12, -28, 0], "translation": [0.6, 0, 0], "scale": [0.92, 0.92, 0.92]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 2.5, 1.5], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [-8, -110, 12], "translation": [1.5, 3.4, 1.2], "scale": [0.62, 0.62, 0.62]},
            "firstperson_lefthand": {"rotation": [-8, 110, -12], "translation": [1.5, 3.4, 1.2], "scale": [0.62, 0.62, 0.62]},
        },
    }


# ----------------------------------------------------------------------------- open grimoire (hovering)
def open_page_texture(key, ink):
    """Cream page with fine lines of glowing magic script in the grimoire's colour (32px for detail)."""
    rng = random.Random("page-" + key)
    img = Image.new("RGBA", (32, 32))
    glow = tuple(min(255, int(c * 0.55 + 255 * 0.45)) for c in ink)
    for y in range(32):
        for x in range(32):
            img.putpixel((x, y), noisy(rng, (240, 231, 202), 4))
    for y in range(4, 29, 3):
        x = 4
        while x < 28:
            length = rng.randint(1, 4)
            for i in range(length):
                if x + i < 28:
                    img.putpixel((x + i, y), (ink if rng.random() < 0.7 else glow) + (255,))
            if rng.random() < 0.3 and x < 27:  # occasional rune stroke
                img.putpixel((x, y - 1), ink + (255,))
            x += length + rng.randint(1, 2)
    # a small clover sigil at the foot of the page
    for (dx, dy) in ((15, 28), (16, 28), (14, 29), (17, 29), (15, 29), (16, 29), (15, 30), (16, 30)):
        img.putpixel((dx, dy), ink + (255,))
    return img


def open_grimoire_model(key):
    cover = f"{NS}:item/grimoire/{key}_cover"
    left = {"origin": [8, 7.4, 8], "axis": "z", "angle": -22.5}
    right = {"origin": [8, 7.4, 8], "axis": "z", "angle": 22.5}
    turning = {"origin": [8, 9, 8], "axis": "z", "angle": 45}
    pages_glow = dict(light_emission=7)
    leather = {"north": face([14, 0, 14.75, 14], "#cover"), "south": face([14, 0, 14.75, 14], "#cover"),
               "east": face([14, 0, 14.75, 14], "#cover"), "west": face([14, 0, 14.75, 14], "#cover")}
    def cover_board(x0, x1, rot):
        faces = dict(leather)
        faces["down"] = face([0, 0, 10, 14], "#cover")
        faces["up"] = face([0, 0, 10, 14], "#cover")
        return box([x0, 7, 2.5], [x1, 7.6, 13.5], faces, rotation=rot)
    def page_block(x0, x1, rot):
        side = face([0, 0, 16, 16], "#edge")
        return box([x0, 7.6, 3], [x1, 8.8, 13], {"up": face([0, 0, 16, 16], "#page"), "north": side, "south": side,
                                                  "east": side, "west": side}, rotation=rot, **pages_glow)
    return {
        "credit": "BlackCloverSMP",
        "textures": {"cover": cover, "page": f"{NS}:item/grimoire/{key}_page", "edge": f"{NS}:item/grimoire/pages",
                     "particle": cover},
        "gui_light": "front",
        "elements": [
            cover_board(1, 8, left),
            cover_board(8, 15, right),
            page_block(1.6, 7.9, left),
            page_block(8.1, 14.4, right),
            box([7.4, 6.6, 2.5], [8.6, 7.4, 13.5], {"down": face([10, 0, 14, 14], "#cover"),
                                                     "north": face([10, 0, 11, 1], "#cover"), "south": face([10, 0, 11, 1], "#cover")}),
            box([8.2, 8.8, 3.4], [14, 8.9, 12.6], {"up": face([0, 0, 16, 16], "#page"), "down": face([0, 0, 16, 16], "#page")},
                rotation=turning, **pages_glow),
        ],
        "display": {
            # the floating ItemDisplay orients the book itself
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            # first person: the open book hovers off to the side, tilted toward you
            "firstperson_righthand": {"rotation": [-55, -20, 10], "translation": [3.5, 4.5, -2], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [-55, 20, -10], "translation": [3.5, 4.5, -2], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [40, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        },
    }


# ----------------------------------------------------------------------------- spell models
def all_faces(uv, tex):
    return {d: face(uv, tex) for d in ("north", "south", "east", "west", "up", "down")}


def light_sword_texture():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = min(x, 15 - x)
            c = (255, 255, 250) if edge >= 5 else (255, 244, 186) if edge >= 2 else (255, 214, 96)
            if y >= 12:
                c = (230, 176, 60) if (x + y) % 3 else (255, 220, 120)
            img.putpixel((x, y), c + (255,))
    for x in range(6, 10):
        for y in range(12, 16):
            img.putpixel((x, y), (120, 220, 255, 255))  # guard gem
    return img


def light_sword_model():
    t = "#blade"
    glow = dict(shade=False, light_emission=15)
    return {
        "textures": {"blade": f"{NS}:item/spell/light_sword", "particle": f"{NS}:item/spell/light_sword"},
        "gui_light": "front",
        "elements": [
            box([6.5, 4, 7.5], [9.5, 15, 8.5], all_faces([0, 0, 16, 11], t), **glow),
            box([7, 15, 7.6], [9, 17, 8.4], all_faces([3, 0, 13, 4], t), **glow),
            box([7.5, 17, 7.7], [8.5, 18.5, 8.3], all_faces([5, 0, 11, 3], t), **glow),
            box([4, 3, 7.2], [12, 4.2, 8.8], all_faces([0, 12, 16, 16], t), **glow),
            box([7.3, 3.2, 6.9], [8.7, 4.4, 9.1], all_faces([6, 12, 10, 16], t), **glow),
            box([7.4, 0, 7.4], [8.6, 3, 8.6], all_faces([2, 12, 6, 16], t), **glow),
        ],
        "display": {
            "fixed": {"rotation": [0, 0, 0], "scale": [1.4, 1.4, 1.4]},
            "gui": {"rotation": [0, 0, -45], "translation": [0, -1, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        },
    }


def rock_texture():
    rng = random.Random("rock")
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = rng.choice(((112, 104, 96), (96, 90, 84), (128, 120, 108), (84, 78, 74)))
            img.putpixel((x, y), noisy(rng, base, 8))
    for _ in range(5):
        x, y = rng.randint(0, 15), rng.randint(0, 15)
        for i in range(rng.randint(3, 6)):
            img.putpixel(((x + i) % 16, (y + i // 2) % 16), (56, 52, 50, 255))
    return img


def rock_model():
    t = "#rock"
    return {
        "textures": {"rock": f"{NS}:item/spell/rock", "particle": f"{NS}:item/spell/rock"},
        "elements": [
            box([3, 3, 3], [13, 12, 13], all_faces([0, 0, 16, 16], t)),
            box([2, 5, 5], [14, 10, 11], all_faces([2, 2, 14, 14], t)),
            box([5, 2, 4], [11, 14, 12], all_faces([1, 1, 15, 15], t)),
            box([4, 6, 2], [12, 11, 14], all_faces([3, 3, 13, 13], t)),
        ],
        "display": {
            "fixed": {"scale": [1.2, 1.2, 1.2]},
            "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def dark_orb_texture():
    rng = random.Random("orb")
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 10.6
            c = mix((12, 4, 22), (170, 80, 255), d ** 1.6)
            if rng.random() < 0.08:
                c = (210, 150, 255)
            img.putpixel((x, y), c + (255,))
    return img


def dark_orb_model():
    t = "#orb"
    e = dict(shade=False, light_emission=12)
    return {
        "textures": {"orb": f"{NS}:item/spell/dark_orb", "particle": f"{NS}:item/spell/dark_orb"},
        "elements": [
            box([4.5, 4.5, 4.5], [11.5, 11.5, 11.5], all_faces([0, 0, 16, 16], t), **e),
            box([3.5, 5.5, 5.5], [12.5, 10.5, 10.5], all_faces([2, 2, 14, 14], t), **e),
            box([5.5, 3.5, 5.5], [10.5, 12.5, 10.5], all_faces([2, 2, 14, 14], t), **e),
            box([5.5, 5.5, 3.5], [10.5, 10.5, 12.5], all_faces([2, 2, 14, 14], t), **e),
            box([4, 6.5, 6.5], [12, 9.5, 9.5], all_faces([4, 4, 12, 12], t), **e),
            box([6.5, 4, 6.5], [9.5, 12, 9.5], all_faces([4, 4, 12, 12], t), **e),
            box([6.5, 6.5, 4], [9.5, 9.5, 12], all_faces([4, 4, 12, 12], t), **e),
        ],
        "display": {
            "fixed": {"scale": [1.0, 1.0, 1.0]},
            "gui": {"rotation": [30, 225, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def demon_sword_texture():
    rng = random.Random("demon-slayer")
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            base = (34, 32, 36) if x < 24 else (60, 44, 34)
            img.putpixel((x, y), noisy(rng, base, 6))
    for _ in range(40):  # rust and chips
        x, y = rng.randint(0, 23), rng.randint(0, 31)
        img.putpixel((x, y), rng.choice(((92, 46, 26, 255), (120, 66, 34, 255), (70, 70, 76, 255))))
    for y in range(32):  # bright cutting edges and a dark fuller down the middle
        for x in (0, 1, 22, 23):
            img.putpixel((x, y), (150, 150, 160, 255) if x in (0, 23) else (104, 104, 112, 255))
        for x in (11, 12):
            img.putpixel((x, y), (18, 16, 20, 255))
    for y in range(0, 32, 3):  # grip wrap
        for x in range(24, 32):
            img.putpixel((x, y), (30, 22, 18, 255))
    return img


def demon_sword_model():
    t = "#blade"
    return {
        "textures": {"blade": f"{NS}:item/spell/demon_slayer", "particle": f"{NS}:item/spell/demon_slayer"},
        "elements": [
            # massive blade with a chipped, squared tip
            box([5, 6, 7.3], [11, 27, 8.7], {
                "north": face([0, 0, 12, 16], t), "south": face([0, 0, 12, 16], t),
                "east": face([0, 0, 1, 16], t), "west": face([11, 0, 12, 16], t),
                "up": face([0, 0, 12, 1], t), "down": face([0, 15, 12, 16], t)}),
            box([5, 27, 7.4], [9.5, 29, 8.6], all_faces([2, 0, 10, 2], t)),
            box([5, 29, 7.5], [7.5, 30, 8.5], all_faces([2, 0, 6, 2], t)),
            box([4.6, 6, 7.6], [5, 27, 8.4], all_faces([0, 0, 1, 16], t)),
            # chunky crossguard
            box([3.5, 5, 7], [12.5, 6.5, 9], all_faces([12, 0, 16, 4], t)),
            # grip and pommel
            box([7.2, -1, 7.3], [8.8, 5, 8.7], all_faces([12, 4, 16, 16], t)),
            box([6.8, -2.2, 6.9], [9.2, -1, 9.1], all_faces([12, 0, 16, 4], t)),
        ],
        "display": {
            "fixed": {"rotation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
            "gui": {"rotation": [0, 0, -45], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        },
    }


# ----------------------------------------------------------------------------- item definitions (vanilla fallback)
def dispatch(base_model, cases):
    return {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": when, "model": {"type": "minecraft:model", "model": model}} for when, model in cases],
            "fallback": {"type": "minecraft:model", "model": base_model},
        }
    }


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    write_json("pack.mcmeta", {
        "pack": {
            "description": "§2Black Clover §7SMP grimoires & magic",
            "pack_format": PACK_FORMAT,
            "min_format": PACK_FORMAT,
            "max_format": PACK_FORMAT,
        }
    })
    icon = Image.new("RGBA", (64, 64), (20, 40, 24, 255))
    emb = emblem_texture("gold").resize((48, 48), Image.NEAREST)
    icon.alpha_composite(emb, (8, 8))
    icon.save(OUT / "pack.png")

    save(pages_texture(), "item/grimoire/pages.png")
    for metal in METALS:
        save(metal_texture(metal), f"item/grimoire/metal_{metal}.png")
    for style in ("gold", "tattered", "devil"):
        save(emblem_texture(style), f"item/grimoire/emblem_{style}.png")

    book_cases = []
    for key, leather, emblem, metal, rune in GRIMOIRES:
        save(cover_texture(key, leather, metal, rune, emblem == "tattered"), f"item/grimoire/{key}_cover.png")
        # magic script colour: the leather colour, except pale covers (gold ink) and the two black grimoires
        ink = {"light": GOLD[2], "lightning": GOLD[2], "time": GOLD[3], "anti_magic": (60, 60, 64),
               "five_leaf": DEVIL[1]}.get(key, leather)
        save(open_page_texture(key, ink), f"item/grimoire/{key}_page.png")
        write_json(f"assets/{NS}/models/item/grimoire/{key}.json", grimoire_model(key, emblem, metal))
        write_json(f"assets/{NS}/models/item/grimoire/{key}_open.json", open_grimoire_model(key))
        closed = {"type": "minecraft:model", "model": f"{NS}:item/grimoire/{key}"}
        opened = {"type": "minecraft:model", "model": f"{NS}:item/grimoire/{key}_open"}
        book_cases.append({"when": f"{NS}:{key}", "model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [
                {"when": ["firstperson_righthand", "firstperson_lefthand"], "model": opened},
                {"when": ["thirdperson_righthand", "thirdperson_lefthand"], "model": {"type": "minecraft:empty"}},
            ],
            "fallback": closed,
        }})
        book_cases.append({"when": f"{NS}:{key}_open", "model": opened})
    write_json("assets/minecraft/items/book.json", {"model": {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "cases": book_cases,
        "fallback": {"type": "minecraft:model", "model": "minecraft:item/book"},
    }})

    spells = {
        "light_sword": (light_sword_texture, light_sword_model, "golden_sword", "minecraft:item/golden_sword"),
        "rock": (rock_texture, rock_model, "cobblestone", "minecraft:block/cobblestone"),
        "dark_orb": (dark_orb_texture, dark_orb_model, "ender_eye", "minecraft:item/ender_eye"),
        "demon_slayer": (demon_sword_texture, demon_sword_model, "netherite_sword", "minecraft:item/netherite_sword"),
    }
    for name, (tex, model, item, base) in spells.items():
        save(tex(), f"item/spell/{name}.png")
        write_json(f"assets/{NS}/models/item/spell/{name}.json", model())
        write_json(f"assets/minecraft/items/{item}.json", dispatch(base, [(f"{NS}:{name}", f"{NS}:item/spell/{name}")]))

    files = sorted(p.relative_to(OUT).as_posix() for p in OUT.rglob("*") if p.is_file())
    (OUT / "files.txt").write_text("\n".join(f for f in files if f != "files.txt") + "\n")
    print(f"wrote {len(files)} files to {OUT}")


if __name__ == "__main__":
    main()
