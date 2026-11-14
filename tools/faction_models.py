"""Generates the three faction weapons' models and textures in resourcepack/.

    python3 tools/faction_models.py [--preview out.png]

Each weapon is painted as pixel art on a square canvas (one pixel = a quarter of a model unit),
together with a depth for every pixel and a mask of the pixels that glow. The model is built from
that relief: every run of pixels with the same depth becomes a box whose front and back show the
painting and whose sides show the pixels at its edge, so blades get bevels, guards and gems stand
proud of the blade, and the glowing pixels become their own boxes with full light emission (they
glow in the dark). The texture is animated: only the glowing pixels change between frames
(a shimmer, a flicker, a pulse).

    Hero Sword          Order of the Blade      steel, gold and sapphire; an azure inlay
    Mjolnir             House of the Hammer     dark iron and gold; a lightning rune
    Fallen God's Spear  Covenant of the Spear   crimson steel on a black shaft; a burning core

Needs Pillow.
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
ASSETS = os.path.join(ROOT, 'resourcepack', 'assets', 'customs')
FRAMES = 12


def hexc(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def scale(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


class Ramp:
    """A shading ramp from darkest to brightest; ramp(t) picks a colour for 0..1."""

    def __init__(self, *colours):
        self.c = [hexc(c) for c in colours]

    def __call__(self, t):
        t = max(0.0, min(1.0, t))
        f = t * (len(self.c) - 1)
        i = min(int(f), len(self.c) - 2)
        # stepped, not smooth: pixel art reads better with clean bands
        return self.c[i] if f - i < 0.5 else self.c[i + 1]

    def smooth(self, t):
        t = max(0.0, min(1.0, t))
        f = t * (len(self.c) - 1)
        i = min(int(f), len(self.c) - 2)
        return mix(self.c[i], self.c[i + 1], f - i)


class Canvas:
    """Pixels with a colour, a depth (in pixels of thickness) and whether they glow."""

    def __init__(self, n, seed):
        self.n = n
        self.px = {}
        self.rng = random.Random(seed)
        # glow pixels: (x, y) -> function(frame) -> colour
        self.glow = {}
        # texture-only pixels (used by hand-made boxes), possibly animated
        self.decal = {}

    def put(self, x, y, colour, depth, glow=None):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.px[(x, y)] = [colour, depth]
            if glow is not None:
                self.glow[(x, y)] = glow
            else:
                self.glow.pop((x, y), None)

    def has(self, x, y):
        return (x, y) in self.px

    def get(self, x, y):
        return self.px.get((x, y))

    def noise(self, amount):
        for k, v in self.px.items():
            if k in self.glow:
                continue
            f = 1 + self.rng.uniform(-amount, amount)
            v[0] = scale(v[0], f)

    def silhouette(self, factor=0.72):
        """Darkens the outermost pixels so the shape reads at a distance."""
        edge = []
        for (x, y) in self.px:
            if (x, y) in self.glow:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in self.px:
                    edge.append((x, y))
                    break
        for k in edge:
            self.px[k][0] = scale(self.px[k][0], factor)

    def image(self, frame):
        img = Image.new('RGBA', (self.n, self.n), (0, 0, 0, 0))
        for (x, y), (c, _) in self.px.items():
            g = self.glow.get((x, y))
            img.putpixel((x, y), g(frame) if g else c)
        for (x, y), c in self.decal.items():
            img.putpixel((x, y), c(frame) if callable(c) else c)
        return img


# ------------------------------------------------------------------ model building

def build_model(canvas, name, display, inset_sides=True, extra=()):
    """Boxes from runs of same-depth pixels; glowing pixels get their own boxes with light."""
    n = canvas.n
    unit = 0.25
    ox = 8 - n * unit / 2
    tex = 16.0 / n

    def key(x, y):
        p = canvas.get(x, y)
        if p is None:
            return None
        return (p[1], (x, y) in canvas.glow)

    # greedy rectangles of equal key
    done = set()
    rects = []
    for y in range(n):
        for x in range(n):
            if (x, y) in done:
                continue
            k = key(x, y)
            if k is None:
                continue
            w = 1
            while x + w < n and (x + w, y) not in done and key(x + w, y) == k:
                w += 1
            h = 1
            while y + h < n and all((x + i, y + h) not in done and key(x + i, y + h) == k for i in range(w)):
                h += 1
            for j in range(h):
                for i in range(w):
                    done.add((x + i, y + j))
            rects.append((x, y, w, h, k))

    def depth_at(x, y):
        p = canvas.get(x, y)
        return p[1] if p else 0

    elements = []
    for (x, y, w, h, (d, glow)) in rects:
        x0, x1 = x, x + w
        y0, y1 = y, y + h
        frm = [ox + x0 * unit, 16 - (oy_top(n) + y1 * unit), 8 - d * unit / 2]
        to = [ox + x1 * unit, 16 - (oy_top(n) + y0 * unit), 8 + d * unit / 2]
        faces = {}
        u = lambda v: round(v * tex, 4)
        faces['south'] = {'uv': [u(x0), u(y0), u(x1), u(y1)], 'texture': '#0'}
        faces['north'] = {'uv': [u(x1), u(y0), u(x0), u(y1)], 'texture': '#0'}
        # sides: only where the neighbour is thinner, sampled one pixel in so they show the
        # surface colour rather than the darkened outline
        def deeper_all(cells):
            return all(depth_at(cx, cy) >= d for cx, cy in cells)
        top = [(x0 + i, y0 - 1) for i in range(w)]
        bottom = [(x0 + i, y1) for i in range(w)]
        left = [(x0 - 1, y0 + j) for j in range(h)]
        right = [(x1, y0 + j) for j in range(h)]
        # thick boxes sample further in, past the shaded rim, so their sides show the surface
        kin = 3 if d >= 10 else 1
        ky = min(kin, (h - 1) // 2) if inset_sides else 0
        kx = min(kin, (w - 1) // 2) if inset_sides else 0
        sy_top = y0 + ky
        sy_bot = y1 - 1 - ky
        sx_l = x0 + kx
        sx_r = x1 - 1 - kx
        if not deeper_all(top):
            faces['up'] = {'uv': [u(x0), u(sy_top), u(x1), u(sy_top + 1)], 'texture': '#0'}
        if not deeper_all(bottom):
            faces['down'] = {'uv': [u(x0), u(sy_bot), u(x1), u(sy_bot + 1)], 'texture': '#0'}
        if not deeper_all(left):
            faces['west'] = {'uv': [u(sx_l), u(y0), u(sx_l + 1), u(y1)], 'texture': '#0'}
        if not deeper_all(right):
            faces['east'] = {'uv': [u(sx_r), u(y0), u(sx_r + 1), u(y1)], 'texture': '#0'}
        el = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'faces': faces}
        if glow:
            el['light_emission'] = 15
            el['shade'] = False
        elements.append(el)

    for el in extra:
        elements.append(el(ox, oy_top(n), tex))
    return {
        'credit': 'Customs faction weapon (tools/faction_models.py)',
        'textures': {'0': 'customs:item/%s/%s' % (name, name), 'particle': 'customs:item/%s/%s' % (name, name)},
        'elements': elements,
        'display': display,
    }


def oy_top(n):
    """Model units between the top of the model space and the top of the canvas."""
    return -(n * 0.25 - 16) / 2


def write(name, canvas, model):
    folder = os.path.join(ASSETS, 'textures', 'item', name)
    os.makedirs(folder, exist_ok=True)
    for f in os.listdir(folder):
        os.remove(os.path.join(folder, f))
    sheet = Image.new('RGBA', (canvas.n, canvas.n * FRAMES))
    for i in range(FRAMES):
        sheet.paste(canvas.image(i), (0, i * canvas.n))
    sheet.save(os.path.join(folder, name + '.png'))
    with open(os.path.join(folder, name + '.png.mcmeta'), 'w') as f:
        json.dump({'animation': {'frametime': 2, 'interpolate': True}}, f)
    with open(os.path.join(ASSETS, 'models', 'item', name + '.json'), 'w') as f:
        json.dump(model, f, separators=(',', ':'))
    with open(os.path.join(ASSETS, 'items', name + '.json'), 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'customs:item/' + name}}, f, indent=2)
    print('%-10s %3d elements, %d glowing pixels' % (name, len(model['elements']), len(canvas.glow)))


# ------------------------------------------------------------------ painting helpers

def fill_rows(c, rows, colour_at, depth_at, glow_at=None):
    """rows: {y: (x_left, x_right_inclusive)}; colour_at(x, y, t) with t = 0..1 across the row."""
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            t = 0.5 if b == a else (x - a) / (b - a)
            g = glow_at(x, y, t) if glow_at else None
            c.put(x, y, colour_at(x, y, t), depth_at(x, y, t), g)


def disc(c, cx, cy, r, colour_at, depth, glow_at=None):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            dist = math.hypot(dx, dy)
            if dist <= r:
                g = glow_at(x, y, dx, dy, dist) if glow_at else None
                c.put(x, y, colour_at(dx, dy, dist), depth(dx, dy, dist) if callable(depth) else depth, g)


def gem(c, cx, cy, r, ramp, depth, glow=None):
    """A faceted round gem: light from the top left, a white glint."""
    def col(dx, dy, dist):
        light = 0.55 - (dx + dy) / (2.4 * r) - dist / (3 * r)
        return ramp(light)
    disc(c, cx, cy, r, col, depth, glow)
    c.put(int(cx - r * 0.45), int(cy - r * 0.45), hexc(0xFFFFFF), depth + (0 if callable(depth) else 0))


# ------------------------------------------------------------------ Hero Sword

def hero_sword():
    n = 64
    c = Canvas(n, 11)
    cx = 32  # blade centre column
    steel = Ramp(0x243246, 0x3F5571, 0x67819F, 0x98B0CA, 0xC8DAEB, 0xF2F8FF)
    gold = Ramp(0x5A3608, 0x9A6414, 0xD49B2A, 0xF2C94C, 0xFFF0B0)
    leather = Ramp(0x0C1A36, 0x183062, 0x2A4F98, 0x3F6CC0)
    sapphire = Ramp(0x0A1A5E, 0x153CB8, 0x2F6BFF, 0x8CBBFF, 0xE6F2FF)
    enamel = Ramp(0x0E2A78, 0x1E4FC8, 0x4D86F0)
    azure = [hexc(0x1C4FD0), hexc(0x3F8CFF), hexc(0x8CC8FF), hexc(0xE8F7FF)]

    # blade: nine pixels at the guard narrowing to seven, then a long point
    blade_top, blade_bottom = 1, 42
    point = 8
    for y in range(blade_top, blade_bottom + 1):
        if y < blade_top + point:
            half = round((y - blade_top) / point * 4.2)
        else:
            half = 4
        half = min(half, 4)
        for x in range(cx - half, cx + half + 1):
            off = x - cx
            if half and abs(off) == half:            # sharpened edge
                col, d = (steel(1.0) if off < 0 else steel(0.4)), 2
            elif half > 1 and abs(off) == half - 1:   # bevel
                col, d = (steel(0.8) if off < 0 else steel(0.45)), 3
            elif off == -1:                           # ridge beside the fuller
                col, d = steel(0.9), 4
            elif off == 1:
                col, d = steel(0.5), 4
            else:
                col, d = steel(0.62), 4
            # the blade darkens a little towards the guard, as if it reflects the gold
            col = mix(col, gold(0.35), max(0.0, (y - 34) / 30))
            c.put(x, y, col, d)
    # the azure inlay down the fuller: a glowing line with a rune diamond every few pixels,
    # and a shimmer that climbs from the guard to the point
    inlay_top, inlay_bottom = blade_top + point + 1, blade_bottom - 3

    def shimmer(y, strong):
        def g(frame):
            pos = inlay_bottom + 4 - (frame / FRAMES) * (inlay_bottom - inlay_top + 12)
            near = abs(y - pos)
            if near < 1.5:
                return azure[3]
            if near < 3.5:
                return azure[2]
            return azure[2] if strong else azure[1]
        return g
    for y in range(inlay_top, inlay_bottom + 1):
        rune = (y - inlay_top) % 6 == 3
        c.put(cx, y, azure[1], 4, shimmer(y, rune))
        if rune:
            for x in (cx - 1, cx + 1):
                c.put(x, y, azure[1], 4, shimmer(y, False))
    # ricasso collar
    for y in (blade_bottom - 1, blade_bottom):
        for x in range(cx - 5, cx + 6):
            c.put(x, y, gold(0.95 - (x - cx + 5) / 10 * 0.55), 5)

    # crossguard: a heavy centre with swept wings rising at the tips, a blue enamel line along it
    gy = blade_bottom + 1
    for off in range(0, 16):
        lift = 0 if off < 6 else (off - 6) // 3
        top = gy - lift + (1 if off > 11 else 0)
        bottom = gy + 3 - lift - (1 if off > 9 else 0) - (1 if off > 13 else 0)
        for side in (-1, 1):
            if off == 0 and side == 1:
                continue
            x = cx + side * off
            for y in range(top, bottom + 1):
                light = 0.95 - (y - top) / max(1, bottom - top) * 0.6
                col = gold(light)
                if 5 < off < 13 and y == top + 1 and bottom - top >= 2:
                    col = enamel(0.8 if side < 0 else 0.5)
                c.put(x, y, col, 5 if off < 6 else 4)
            if off == 15:
                c.put(x, top - 1, gold(0.85), 4)
                c.put(x - side, top - 1, gold(0.7), 4)
    # a gold boss holding the sapphire
    for y in range(gy - 2, gy + 6):
        for x in range(cx - 3, cx + 4):
            if abs(x - cx) + abs(y - (gy + 1.5)) <= 4.5:
                c.put(x, y, gold(0.85 if x < cx else 0.55), 6)
    gem(c, cx + 0.5, gy + 2.0, 2.4, sapphire, 7)

    # grip: blue leather with a diagonal wrap, gold rings at both ends
    grip_top, grip_bottom = gy + 6, gy + 16
    for y in range(grip_top, grip_bottom + 1):
        for x in range(cx - 2, cx + 3):
            wrap = (x + y) % 4
            light = [0.95, 0.7, 0.45, 0.25][wrap] - (0.15 if x == cx + 2 else 0) + (0.1 if x == cx - 2 else 0)
            c.put(x, y, leather(light), 4)
    for y in (grip_top - 1, grip_bottom + 1):
        for x in range(cx - 3, cx + 4):
            c.put(x, y, gold(0.9 - (x - cx + 3) / 7 * 0.6), 5)
    # pommel: a gold diamond with a sapphire
    py = grip_bottom + 2
    for y in range(py, py + 5):
        half = [2, 3, 3, 2, 1][y - py]
        for x in range(cx - half, cx + half + 1):
            c.put(x, y, gold(0.9 - (y - py) * 0.12 - (x - cx) * 0.06), 5)
    gem(c, cx + 0.5, py + 2.0, 1.4, sapphire, 6)

    c.noise(0.04)
    c.silhouette()
    return c, build_model(c, 'herosword', sword_display())


def sword_display():
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 8.5, 1.5], "scale": [1.45, 1.45, 1.45]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 8.5, 1.5], "scale": [1.45, 1.45, 1.45]},
        "firstperson_righthand": {"rotation": [0, -90, 15], "translation": [1.5, 4.8, 1.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [0, 90, -15], "translation": [1.5, 4.8, 1.5], "scale": [0.85, 0.85, 0.85]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 45], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    }


# ------------------------------------------------------------------ Mjolnir

def mjolnir():
    n = 64
    c = Canvas(n, 23)
    cx = 32
    iron = Ramp(0x17191E, 0x2C3038, 0x484E5A, 0x6C7482, 0x99A1AE, 0xC8CED8)
    gold = Ramp(0x5A3608, 0x9A6414, 0xD49B2A, 0xF0B840, 0xFFE9A0)
    leather = Ramp(0x24140A, 0x3E2614, 0x5E3D22, 0x83603A)
    storm = [hexc(0x2F5FD0), hexc(0x6FC4FF), hexc(0xBFE8FF), hexc(0xFFFFFF)]

    def flicker(i):
        def g(frame):
            if frame in (3, 4, 9):   # a strike runs through the rune
                return storm[3] if i % 2 == 0 else storm[2]
            return storm[2] if i % 5 == 0 else storm[1]
        return g

    # the head: flared striking ends, a slimmer waist, gold bands where they meet
    hx0, hx1 = cx - 16, cx + 15
    for x in range(hx0, hx1 + 1):
        off = abs(x - cx + 0.5)
        if off > 11.5:          # the flared ends
            top, bottom, d = 5, 28, 20
        elif off > 9.5:         # the bands
            top, bottom, d = 6, 27, 21
        else:                   # the waist
            top, bottom, d = 8, 25, 17
        for y in range(top, bottom + 1):
            ey = min(y - top, bottom - y)
            ex = min(x - hx0, hx1 - x)
            if ex + ey < 2:
                continue  # chamfered corners
            light = 0.85 - (y - top) / (bottom - top) * 0.55
            if 9.5 < off <= 11.5:
                col, dd = gold(light + 0.05), d
            elif off > 11.5 and ex < 2:
                col, dd = gold(light - 0.05), d       # gold rim round the striking face
            else:
                # knotwork: a faint diamond lattice engraved in the iron
                lattice = (x + y) % 6 == 0 or (x - y) % 6 == 0
                col = iron(light - (0.12 if lattice else 0))
                dd = d
            if ey == 0:
                col = scale(col, 0.9)
                dd = d - 1
            c.put(x, y, col, dd)
    # rivets on the bands
    for bx in (cx - 11, cx + 10):
        for ry in range(9, 26, 4):
            c.put(bx, ry, gold(1.0), 22)
    # the lightning rune, glowing, in a thin gold ring
    rcx, rcy = cx - 0.5, 16.5
    for a in range(0, 360, 8):
        x = int(round(rcx + math.cos(math.radians(a)) * 6.2))
        y = int(round(rcy + math.sin(math.radians(a)) * 6.2))
        c.put(x, y, gold(0.9 if a > 180 else 0.6), 18)
    bolt = [(1, -5), (2, -5), (1, -4), (0, -3), (0, -2), (-1, -1), (0, -1), (1, -1), (2, -1), (1, 0), (0, 1),
            (-1, 2), (0, 2), (1, 2), (0, 3), (-1, 4), (-1, 5)]
    for i, (dx, dy) in enumerate(bolt):
        c.put(cx + dx - 1, 16 + dy, storm[1], 18, flicker(i))

    # the haft
    collar_y = 29
    for y in range(collar_y, collar_y + 3):
        half = [5, 4, 3][y - collar_y]
        for x in range(cx - half, cx + half + 1):
            c.put(x, y, gold(0.95 - (x - cx + half) / (2 * half) * 0.6), 8)
    shaft_top, shaft_bottom = collar_y + 3, 55
    for y in range(shaft_top, shaft_bottom + 1):
        for x in range(cx - 2, cx + 3):
            wrap = (y - x) % 3
            light = [0.9, 0.6, 0.35][wrap] + (0.1 if x == cx - 2 else 0) - (0.2 if x == cx + 2 else 0)
            c.put(x, y, leather(light), 5)
        if (y - shaft_top) % 8 == 7:
            for x in range(cx - 3, cx + 4):
                c.put(x, y, gold(0.9 - (x - cx + 3) / 6 * 0.55), 6)
    for y in range(shaft_bottom + 1, shaft_bottom + 5):
        half = [3, 4, 4, 3][y - shaft_bottom - 1]
        for x in range(cx - half, cx + half + 1):
            c.put(x, y, gold(0.95 - (y - shaft_bottom) * 0.12 - (x - cx) * 0.05), 7)
    c.put(cx, shaft_bottom + 2, storm[1], 8, flicker(0))
    c.put(cx - 1, shaft_bottom + 2, storm[1], 8, flicker(1))
    sy = shaft_bottom + 5
    for dx, dy in [(-1, 0), (1, 0), (-2, 1), (2, 1), (-2, 2), (2, 2), (-2, 3), (2, 3), (-1, 4), (0, 4), (1, 4)]:
        c.put(cx + dx, sy + dy, leather(0.6 - dy * 0.06), 2)

    # striking faces (texture-only, used by two hand-made plates): a gold ring round dark iron
    # with a glowing rune in the middle
    fx0, fy0, fw, fh = 0, 34, 20, 24
    for y in range(fh):
        for x in range(fw):
            dx, dy = x - (fw - 1) / 2, y - (fh - 1) / 2
            r = math.hypot(dx / (fw / 2), dy / (fh / 2))
            if r > 0.86:
                col = gold(0.7 - dy / fh)
            elif r > 0.74:
                col = iron(0.25)
            elif r > 0.62:
                col = gold(0.85 - dy / fh * 0.6)
            else:
                col = iron(0.55 - dy / fh * 0.5 - (0.1 if (x + y) % 4 == 0 else 0))
            c.decal[(fx0 + x, fy0 + y)] = col
    gx0, gy0 = 22, 34  # the glowing rune for the plates' centres (8x8)
    for y in range(8):
        for x in range(8):
            on = (x, y) in {(4, 0), (3, 1), (4, 1), (2, 2), (3, 2), (3, 3), (4, 3), (5, 3), (4, 4), (3, 5), (4, 5), (2, 6), (3, 6), (2, 7)}
            c.decal[(gx0 + x, gy0 + y)] = flicker(x + y) if on else hexc(0x1F2A3A)

    c.noise(0.05)
    c.silhouette()
    unit = 0.25

    def plate(side):
        def make(ox, oy, tex):
            # the flared end spans pixel columns hx0..hx0+3 (or hx1-3..hx1), rows 5..28, depth 20
            x = ox + (hx1 + 1) * unit if side > 0 else ox + hx0 * unit
            y0 = 16 - (oy + 28 * unit) + 0.25
            y1 = 16 - (oy + 5 * unit) - 0.25
            z0, z1 = 8 - 20 * unit / 2 + 0.25, 8 + 20 * unit / 2 - 0.25
            face = 'east' if side > 0 else 'west'
            u = lambda v: round(v * tex, 4)
            return {'from': [round(x if side < 0 else x - 0.01, 4) - (0.2 if side < 0 else 0), round(y0, 4), round(z0, 4)],
                    'to': [round(x + (0.2 if side > 0 else 0.0), 4), round(y1, 4), round(z1, 4)],
                    'faces': {face: {'uv': [u(fx0), u(fy0), u(fx0 + fw), u(fy0 + fh)], 'texture': '#0'}}}
        return make

    def rune(side):
        def make(ox, oy, tex):
            x = ox + (hx1 + 1) * unit + 0.21 if side > 0 else ox + hx0 * unit - 0.21
            yc = 16 - (oy + 16.5 * unit)
            face = 'east' if side > 0 else 'west'
            u = lambda v: round(v * tex, 4)
            return {'from': [round(x - (0.02 if side > 0 else 0.0), 4), round(yc - 1.0, 4), 7.0],
                    'to': [round(x + (0.0 if side > 0 else 0.02), 4), round(yc + 1.0, 4), 9.0],
                    'light_emission': 15, 'shade': False,
                    'faces': {face: {'uv': [u(gx0), u(gy0), u(gx0 + 8), u(gy0 + 8)], 'texture': '#0'}}}
        return make

    display = {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 7, 1.5], "scale": [1.2, 1.2, 1.2]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 7, 1.5], "scale": [1.2, 1.2, 1.2]},
        "firstperson_righthand": {"rotation": [0, -90, 12], "translation": [1.5, 6.5, 1.0], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -12], "translation": [1.5, 6.5, 1.0], "scale": [0.68, 0.68, 0.68]},
        "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 45], "scale": [1.0, 1.0, 1.0]},
    }
    return c, build_model(c, 'mjolnir', display, extra=[plate(1), plate(-1), rune(1), rune(-1)])


# ------------------------------------------------------------------ Fallen God's Spear

def god_spear():
    n = 96
    c = Canvas(n, 37)
    cx = 48
    crimson = Ramp(0x2E0608, 0x62100F, 0xA01C1E, 0xD83A3A, 0xFF8A78)
    black = Ramp(0x0C0608, 0x1C0C10, 0x33161C, 0x4E2028)
    gold = Ramp(0x4A2A08, 0x8A5A14, 0xC88E28, 0xE8B848, 0xFFE2A0)
    cloth = Ramp(0x3E0A0E, 0x6E1418, 0xA82228, 0xD83A3A)
    ember = [hexc(0x8A0A0A), hexc(0xFF3A2A), hexc(0xFF8A5A), hexc(0xFFE0C0)]

    def pulse(y):
        def g(frame):
            p = (math.sin((frame / FRAMES) * math.pi * 2 + y * 0.3) + 1) / 2
            return ember[3] if p > 0.86 else ember[2] if p > 0.45 else ember[1]
        return g

    # the head: a long leaf blade with a burning core
    head_top, head_bottom = 3, 34
    for y in range(head_top, head_bottom + 1):
        t = (y - head_top) / (head_bottom - head_top)
        if t < 0.86:
            half = int(round(7.2 * math.sin(min(1.0, t * 1.25) * math.pi * 0.62) ** 1.2))
        else:
            half = int(round(2 + (1 - t) * 8))
        for x in range(cx - half, cx + half + 1):
            off = x - cx
            if half and abs(off) == half:
                col, d = (crimson(1.0) if off < 0 else crimson(0.35)), 2
            elif half > 1 and abs(off) == half - 1:
                col, d = (crimson(0.8) if off < 0 else crimson(0.5)), 3
            elif off == -1:
                col, d = crimson(0.75), 4
            else:
                col, d = crimson(0.58), 4
            c.put(x, y, col, d)
    core_top, core_bottom = head_top + 5, head_bottom - 3
    for y in range(core_top, core_bottom + 1):
        c.put(cx, y, ember[1], 4, pulse(y))
    # the core flares where the blade is widest
    for y in range(head_top + 14, head_top + 21):
        for x in (cx - 1, cx + 1):
            c.put(x, y, ember[1], 4, pulse(y + 1))
    # wings: two swept lugs below the blade, hooked at the tips
    wy = head_bottom - 1
    for s in (-1, 1):
        for i in range(8):
            x = cx + s * (3 + i)
            y0 = wy + i // 2
            for y in range(y0, y0 + 3 - (1 if i > 5 else 0)):
                light = (0.75 if s < 0 else 0.45) - i * 0.04 - (y - y0) * 0.15
                c.put(x, y, crimson(light), 3)
        c.put(cx + s * 11, wy + 2, crimson(0.55), 3)
        c.put(cx + s * 11, wy + 1, crimson(0.65), 3)
        c.put(cx + s * 11, wy, crimson(0.7), 3)
    # the socket: a gold crown of three spikes over a black collar with a glowing eye
    sy0 = head_bottom + 2
    for dx, h in ((-3, 3), (0, 4), (3, 3)):
        for k in range(h):
            c.put(cx + dx, sy0 - k, gold(0.9 - k * 0.1), 5)
    for y in range(sy0, sy0 + 7):
        ring = y in (sy0, sy0 + 6)
        half = 3 if ring else 2
        for x in range(cx - half, cx + half + 1):
            tt = (x - cx + half) / (2 * half)
            col = gold(0.95 - tt * 0.6) if ring else black(0.75 - tt * 0.5)
            c.put(x, y, col, 6 if ring else 5)
    disc(c, cx + 0.5, sy0 + 3.5, 1.6, lambda dx, dy, d: ember[2], 7, lambda x, y, dx, dy, d: pulse(0))

    # the shaft: black wood bound with thin crimson cord, a pennant, a butt spike
    shaft_top, shaft_bottom = sy0 + 7, 90
    for y in range(shaft_top, shaft_bottom + 1):
        for x in range(cx - 1, cx + 2):
            light = [0.85, 0.55, 0.25][x - cx + 1]
            c.put(x, y, black(light), 4)
        if (y - shaft_top) % 10 in (4, 5):
            for x in range(cx - 2, cx + 3):
                c.put(x, y, cloth(0.85 - (x - cx + 2) / 4 * 0.55 - ((y - shaft_top) % 10 - 4) * 0.15), 5)
    # pennant: crimson cloth with a gold spear emblem, rippling, tattered at the tail
    pen_top = shaft_top + 1
    emblem = {(4, 3), (4, 4), (3, 5), (4, 5), (5, 5), (4, 6), (4, 7), (4, 8), (4, 9), (4, 10), (3, 4), (5, 4)}
    for y in range(pen_top, pen_top + 17):
        row = y - pen_top
        length = 9 - max(0, row - 10)
        for i in range(1, length + 1):
            x = cx + 1 + i
            yy = y + (1 if i > 5 and math.sin(row * 0.5) > 0.3 else 0)
            light = 0.78 - i * 0.04 + (0.12 if (row + i) % 5 == 0 else 0) - (0.25 if row > 13 else 0)
            col = gold(0.85 - i * 0.04) if (i, row) in emblem else cloth(light)
            c.put(x, yy, col, 1)
    for (dx, dy) in ((3, 17), (4, 18), (6, 17), (7, 16), (8, 15)):
        c.put(cx + 1 + dx, pen_top + dy, cloth(0.4), 1)
    for y in range(shaft_bottom + 1, shaft_bottom + 5):
        half = 1 if y < shaft_bottom + 3 else 0
        for x in range(cx - half, cx + half + 1):
            c.put(x, y, gold(0.7 - (y - shaft_bottom) * 0.1), 3)

    c.noise(0.05)
    c.silhouette()
    display = {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 4, 0.75], "scale": [1.5, 1.5, 1.5]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 4, 0.75], "scale": [1.5, 1.5, 1.5]},
        "firstperson_righthand": {"rotation": [0, -90, 12], "translation": [2.5, 3.5, -1.5], "scale": [0.66, 0.66, 0.66]},
        "firstperson_lefthand": {"rotation": [0, 90, -12], "translation": [2.5, 3.5, -1.5], "scale": [0.66, 0.66, 0.66]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 45], "scale": [0.7, 0.7, 0.7]},
    }
    return c, build_model(c, 'godspear', display)


def main():
    built = [('herosword',) + hero_sword(), ('mjolnir',) + mjolnir(), ('godspear',) + god_spear()]
    for name, canvas, model in built:
        write(name, canvas, model)
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        tiles = []
        for name, canvas, _ in built:
            tiles.append(canvas.image(0).resize((canvas.n * 4, canvas.n * 4), Image.NEAREST))
            tiles.append(canvas.image(4).resize((canvas.n * 4, canvas.n * 4), Image.NEAREST))
        w = sum(t.width for t in tiles)
        h = max(t.height for t in tiles)
        sheet = Image.new('RGBA', (w, h), (40, 42, 48, 255))
        xo = 0
        for t in tiles:
            sheet.alpha_composite(t, (xo, 0))
            xo += t.width
        sheet.save(out)
        print('preview', out)


if __name__ == '__main__':
    main()
