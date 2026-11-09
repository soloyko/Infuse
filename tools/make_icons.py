#!/usr/bin/env python3
"""Generates extra effect icons (Weaving, Oozing, Resistance) in the resource pack's style.

The frames are taken from the pack's own Heart icon (all 4 variants), the heart symbol is
removed, the frame is recoloured, and a new pixel-art symbol is drawn on top. Cooldown frames
are made exactly like the originals: frame f darkens rows 2..f+1 to half brightness.

Appends rows to effects.png / hit_tracker.png and the matching characters to the font JSONs.
Run from the repo root:  python3 tools/make_icons.py
Re-running is safe: it rebuilds from the first 15 effects each time.
"""
import colorsys
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(".")
ICONS = ROOT / "resourcepack/assets/infuse/textures/icons"
FONTS = ROOT / "resourcepack/assets/infuse/font"
BASE_EFFECTS = 15          # rows of effects in the original pack
TEMPLATE = 6               # Heart: magenta frame, red symbol
CELL, FRAMES = 24, 21
SUB = [0x00, 0x80, 0x40, 0xC0]

# name, frame hue (deg), saturation scale, value scale, accent hue for the hit tracker fill, symbol
NEW = [
    ("weaving", 28, 0.55, 0.62, 38, "web"),
    ("oozing", 150, 0.9, 0.62, 115, "slime"),
    ("resistance", 218, 0.5, 0.86, 210, "chestplate"),
]

SYMBOLS = {
    # . transparent, letters = palette
    "slime": ("""
....OOOOOO....
..OOGGGGGGOO..
.OGHHGGGGGGGO.
.OGHGGGGGGGGO.
OGGGGGGGGGGGGO
OGGKKGGGGKKGGO
OGGKKGGGGKKGGO
OGGGGGGGGGGGGO
OGGGGGKKGGGGGO
.OGGGGGGGGGGO.
.OCGGGGGGGGCO.
..OCCCCCCCCO..
...OOOOOOOO...
""", {"O": (22, 70, 26), "G": (111, 211, 106), "H": (205, 255, 196), "C": (66, 158, 62), "K": (18, 52, 20)}),
    "chestplate": ("""
.OOO......OOO.
OLLLO....OLLLO
OLMMLOOOOLMMLO
OLMMMLLLLMMMLO
.OOLMMMMMMLOO.
...OLMMMMDO...
...OLMMMMDO...
...OLMMMMDO...
...OLMMMMDO...
...OLMMMMDO...
...OLLMMDDO...
....OOOOOO....
""", {"O": (34, 38, 48), "L": (236, 240, 245), "M": (170, 180, 192), "D": (110, 120, 132)}),
}


def web_symbol():
    """A 14x14 cobweb: 8 spokes and two rings, with a dark drop shadow."""
    size = 14
    px = {}
    c = 6.5
    spokes = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)]
    for dx, dy in spokes:
        for t in range(0, 8):
            x, y = int(round(c + dx * t)), int(round(c + dy * t))
            if 0 <= x < size and 0 <= y < size:
                px[(x, y)] = "W"
    for r, col in ((3, "L"), (5.5, "L")):
        for i in range(64):
            import math
            a = i / 64 * math.tau
            # slightly squared ring like a hand-drawn web
            k = 1 + 0.18 * abs(math.sin(2 * a))
            x, y = int(round(c + math.cos(a) * r * k)), int(round(c + math.sin(a) * r * k))
            if 0 <= x < size and 0 <= y < size and px.get((x, y)) != "W":
                px[(x, y)] = col
    rows = []
    for y in range(size):
        rows.append("".join(px.get((x, y), ".") for x in range(size)))
    # drop shadow
    grid = [list(r) for r in rows]
    for y in range(size - 1, -1, -1):
        for x in range(size - 1, -1, -1):
            if rows[y][x] != "." and x + 1 < size and y + 1 < size and grid[y + 1][x + 1] == ".":
                grid[y + 1][x + 1] = "D"
    return "\n".join("".join(r) for r in grid), {"W": (245, 245, 240), "L": (200, 198, 190), "D": (60, 52, 44)}


WEB = """
W.....W.....W
.W....W....W.
..W..LWL..W..
...WL.W.LW...
..LW..W..WL..
.L..W.W.W..L.
WWWWWWWWWWWWW
.L..W.W.W..L.
..LW..W..WL..
...WL.W.LW...
..W..LWL..W..
.W....W....W.
W.....W.....W
"""


def shadowed(art):
    rows = [list(r) for r in art.strip("\n").split("\n")]
    h, w = len(rows), len(rows[0])
    grid = [[c for c in r] + ["."] for r in rows] + [["."] * (w + 1)]
    for y in range(h):
        for x in range(w):
            if rows[y][x] != "." and grid[y + 1][x + 1] == ".":
                grid[y + 1][x + 1] = "D"
    return "\n".join("".join(r) for r in grid)


SYMBOLS["web"] = (shadowed(WEB), {"W": (250, 250, 246), "L": (206, 204, 196), "D": (36, 28, 22)})


def hsv(p):
    r, g, b = (v / 255 for v in p[:3])
    return colorsys.rgb_to_hsv(r, g, b)


def is_glow(p):
    h, s, v = hsv(p)
    return p[3] > 0 and 0.45 < h < 0.56 and v > 0.7


def is_symbol(p, x, y):
    if p[3] == 0 or not (4 <= x <= 19 and 4 <= y <= 19):
        return False
    h, s, v = hsv(p)
    hue = h * 360
    return (hue < 40 or hue > 345) and s > 0.35


def recolour(p, hue, s_scale, v_scale):
    h, s, v = hsv(p)
    r, g, b = colorsys.hsv_to_rgb(hue / 360, min(1, s * s_scale), min(1, v * v_scale))
    return (round(r * 255), round(g * 255), round(b * 255), p[3])


def base_frame(sheet, variant, hue, s_scale, v_scale, rng):
    src = sheet.crop((0, (TEMPLATE * 4 + variant) * CELL, CELL, (TEMPLATE * 4 + variant + 1) * CELL)).convert("RGBA")
    out = src.copy()
    w, h = src.size
    # remove the heart: refill symbol pixels with background texture from the same row
    for y in range(h):
        bg = [src.getpixel((x, y)) for x in range(4, 20)
              if not is_symbol(src.getpixel((x, y)), x, y) and not is_glow(src.getpixel((x, y))) and src.getpixel((x, y))[3]]
        for x in range(w):
            if is_symbol(src.getpixel((x, y)), x, y):
                out.putpixel((x, y), rng.choice(bg) if bg else src.getpixel((x, y)))
    # recolour the frame (keep the cyan glow)
    for y in range(h):
        for x in range(w):
            p = out.getpixel((x, y))
            if p[3] and not is_glow(p):
                out.putpixel((x, y), recolour(p, hue, s_scale, v_scale))
    return out


def draw_symbol(img, symbol):
    art, palette = symbol
    rows = [r for r in art.strip("\n").split("\n")]
    sh, sw = len(rows), max(len(r) for r in rows)
    ox, oy = (CELL - sw) // 2, (CELL - sh) // 2
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((ox + x, oy + y), palette[ch] + (255,))


def cooldown(img, frame):
    out = img.copy()
    for y in range(2, min(CELL, frame + 2)):
        for x in range(CELL):
            r, g, b, a = out.getpixel((x, y))
            if a:
                out.putpixel((x, y), (r // 2, g // 2, b // 2, a))
    return out


def hit_row(sheet, hue, s_scale, accent_hue):
    """Recolour the Heart hit-tracker row: magenta frame -> frame hue, red fill -> accent hue."""
    row = sheet.crop((0, TEMPLATE * CELL, sheet.width, (TEMPLATE + 1) * CELL)).convert("RGBA")
    for y in range(row.height):
        for x in range(row.width):
            p = row.getpixel((x, y))
            if not p[3]:
                continue
            h, s, v = hsv(p)
            hue_deg = h * 360
            if s < 0.15:
                continue
            target = accent_hue if (hue_deg < 40 or hue_deg > 345) else hue
            row.putpixel((x, y), recolour(p, target, s_scale if target == hue else 0.9, 1))
    return row


def main():
    rng = random.Random(7)
    effects = Image.open(ICONS / "effects.png").convert("RGBA")
    hits = Image.open(ICONS / "hit_tracker.png").convert("RGBA")
    effects = effects.crop((0, 0, effects.width, BASE_EFFECTS * 4 * CELL))
    hits = hits.crop((0, 0, hits.width, BASE_EFFECTS * CELL))

    new_eff = Image.new("RGBA", (effects.width, effects.height + len(NEW) * 4 * CELL))
    new_eff.paste(effects, (0, 0))
    new_hit = Image.new("RGBA", (hits.width, hits.height + len(NEW) * CELL))
    new_hit.paste(hits, (0, 0))

    for i, (name, hue, s_scale, v_scale, accent, sym) in enumerate(NEW):
        index = BASE_EFFECTS + i
        for variant in range(4):
            base = base_frame(effects, variant, hue, s_scale, v_scale, rng)
            draw_symbol(base, SYMBOLS[sym])
            for f in range(FRAMES):
                new_eff.paste(cooldown(base, f), (f * CELL, (index * 4 + variant) * CELL))
        new_hit.paste(hit_row(hits, hue, s_scale, accent), (0, index * CELL))

    new_eff.save(ICONS / "effects.png")
    new_hit.save(ICONS / "hit_tracker.png")

    total = BASE_EFFECTS + len(NEW)
    eff_json = json.loads((FONTS / "effects.json").read_text())
    eff_json["providers"][0]["chars"] = [
        "".join(chr(((0xE0 + c) << 8) | (SUB[k % 4] + k // 4 + 1)) for c in range(FRAMES)) for k in range(total * 4)]
    (FONTS / "effects.json").write_text(json.dumps(eff_json, indent=2, ensure_ascii=True))
    hit_json = json.loads((FONTS / "hit_tracker.json").read_text())
    hit_json["providers"][0]["chars"] = ["".join(chr((c << 12) | (r + 1)) for c in range(14)) for r in range(total)]
    (FONTS / "hit_tracker.json").write_text(json.dumps(hit_json, indent=2, ensure_ascii=True))
    print(f"effects.png {new_eff.size}, hit_tracker.png {new_hit.size}, {total} effects")


if __name__ == "__main__":
    main()
