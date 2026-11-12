"""Procedural pixel art for the ten OP swords.

Every sword is painted on a 32x32 canvas, diagonally like vanilla swords (pommel bottom-left,
tip top-right), in "sword space":

    s = distance along the sword from the pommel end (towards the tip)
    w = signed distance across the sword (negative = upper-left side, which is lit)

Each pixel also gets a thickness (in model units) that the model builder extrudes, so the
blade is bevelled, the guard is chunky and gems stick out.
"""
import math
import random

SIZE = 32
ORIGIN = (3.0, 29.0)            # pommel end, in pixel coordinates
AXIS = (1 / math.sqrt(2), -1 / math.sqrt(2))
ACROSS = (1 / math.sqrt(2), 1 / math.sqrt(2))
LENGTH = 37.5                   # pommel end -> tip, along the axis


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    """f > 0 lightens towards white, f < 0 darkens towards black."""
    return mix(c, (255, 255, 255), f) if f >= 0 else mix(c, (0, 0, 0), -f)


class Canvas:
    def __init__(self):
        self.color = [[None] * SIZE for _ in range(SIZE)]
        self.depth = [[0.0] * SIZE for _ in range(SIZE)]
        self.part = [[None] * SIZE for _ in range(SIZE)]

    @staticmethod
    def sw(x, y):
        cx, cy = x + 0.5 - ORIGIN[0], y + 0.5 - ORIGIN[1]
        return cx * AXIS[0] + cy * AXIS[1], cx * ACROSS[0] + cy * ACROSS[1]

    def fill(self, test, paint, part, depth):
        """test(s, w) -> bool; paint(s, w, x, y) -> rgb; depth: float or fn(s, w)."""
        for y in range(SIZE):
            for x in range(SIZE):
                s, w = self.sw(x, y)
                if test(s, w):
                    self.color[y][x] = paint(s, w, x, y)
                    self.part[y][x] = part
                    self.depth[y][x] = depth(s, w) if callable(depth) else depth

    def put(self, x, y, rgb, part=None, depth=None):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            if self.color[y][x] is None and depth is None:
                return
            self.color[y][x] = rgb
            if part:
                self.part[y][x] = part
            if depth is not None:
                self.depth[y][x] = depth

    def outline(self, darken=0.7, parts=None):
        """Adds a darker 1px rim around the silhouette (classic item sprite look)."""
        add = []
        for y in range(SIZE):
            for x in range(SIZE):
                if self.color[y][x] is not None:
                    continue
                best = None
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < SIZE and 0 <= ny < SIZE and self.color[ny][nx] is not None:
                        if parts and self.part[ny][nx] not in parts:
                            continue
                        if best is None or self.depth[ny][nx] > self.depth[best[1]][best[0]]:
                            best = (nx, ny)
                if best:
                    bx, by = best
                    add.append((x, y, shade(self.color[by][bx], -darken), self.part[by][bx],
                                min(self.depth[by][bx], 1.0)))
        for x, y, c, p, d in add:
            self.color[y][x] = c
            self.part[y][x] = p + '_outline'
            self.depth[y][x] = d

    def copy(self):
        c = Canvas()
        c.color = [row[:] for row in self.color]
        c.depth = [row[:] for row in self.depth]
        c.part = [row[:] for row in self.part]
        return c


# ---------------------------------------------------------------------------------------------
# Shared building blocks
# ---------------------------------------------------------------------------------------------

def blade(cv, start, profile, body, edge, dark, ridge=None, depth_center=1.0, depth_edge=0.5, tip=LENGTH):
    """profile(t) -> half width, t in [0,1] from guard to tip."""
    def test(s, w):
        if s < start or s > tip:
            return False
        t = (s - start) / (tip - start)
        return abs(w) <= profile(t)

    def paint(s, w, x, y):
        t = (s - start) / (tip - start)
        hw = max(profile(t), 0.01)
        u = w / hw
        if u < -0.55:
            c = edge                                   # lit cutting edge
        elif u > 0.55:
            c = dark                                   # shadowed edge
        else:
            c = body
        if ridge is not None and -0.25 <= u <= 0.2 and hw > 1.2:
            c = ridge
        return c

    def depth(s, w):
        t = (s - start) / (tip - start)
        u = abs(w) / max(profile(t), 0.01)
        return depth_center if u < 0.5 else depth_edge

    cv.fill(test, paint, 'blade', depth)


def grip(cv, start, end, a, b, half=1.1, depth=1.0, wrap=2.0):
    def paint(s, w, x, y):
        return a if int((s - start) / wrap * 2) % 2 == 0 else b
    cv.fill(lambda s, w: start <= s <= end and abs(w) <= half, paint, 'grip', depth)


def disc(cv, cs, cw, r, color_fn, part, depth):
    cv.fill(lambda s, w: (s - cs) ** 2 + (w - cw) ** 2 <= r * r,
            lambda s, w, x, y: color_fn((s - cs) / r, (w - cw) / r), part, depth)


def gem_color(base):
    def f(ds, dw):
        if dw < -0.25 and ds > -0.2:
            return shade(base, 0.55)
        if dw > 0.3:
            return shade(base, -0.35)
        return base
    return f


def crossguard(cv, center, half_len, half_thick, a, b, depth=1.5, shape=None):
    """A guard across the sword; shape(w_norm) -> extra thickness along s (for flared ends)."""
    def test(s, w):
        if abs(w) > half_len:
            return False
        extra = shape(abs(w) / half_len) if shape else 0
        return abs(s - center) <= half_thick + extra

    def paint(s, w, x, y):
        return a if (s - center) >= -0.2 and w < half_len * 0.6 else b
    cv.fill(test, paint, 'guard', depth)


# ---------------------------------------------------------------------------------------------
# The swords. Each returns a list of animation frames (Canvas).
# ---------------------------------------------------------------------------------------------

def dragon(frames=8):
    g = {k: hexc(v) for k, v in dict(
        body='5FC460', edge='C6F5A0', dark='237A3A', ridge='E9FFD6', scale='3E9E4B',
        gold='E8B53C', gold_d='9C6A1A', grip='5A3420', grip2='3D2214', eye='FF4A2E').items()}
    cv = Canvas()
    # serrated blade: teeth on the shadow side
    def prof(t):
        base = 2.6 * (1 - t) ** 0.55 + 0.35
        return base
    blade(cv, 11.5, prof, g['body'], g['edge'], g['dark'], g['ridge'], 1.0, 0.5)
    def tooth(s, w):
        if not (13.5 <= s <= 31 and w > 0):
            return False
        edge = prof((s - 11.5) / (LENGTH - 11.5))
        return w <= edge + 1.6 * (1 - ((s - 13.5) % 3.0) / 3.0)
    cv.fill(tooth, lambda s, w, x, y: g['dark'] if w > prof((s - 11.5) / 26) + 0.6 else g['body'], 'blade', 0.5)
    # scale pattern on the blade body
    for y in range(SIZE):
        for x in range(SIZE):
            s, w = Canvas.sw(x, y)
            if cv.part[y][x] == 'blade' and 15 < s < 30 and -0.6 < w < 0.6 and int(s) % 4 == 0:
                cv.color[y][x] = g['scale']
    # wing guard: sweeps back towards the grip at the ends
    crossguard(cv, 10.0, 5.2, 0.9, g['gold'], g['gold_d'], 1.75,
               shape=lambda r: 0.0 if r < 0.55 else 1.6 * (r - 0.55) / 0.45)
    cv.fill(lambda s, w: 7.6 <= s <= 9.6 and 3.3 <= abs(w) <= 5.4, lambda s, w, x, y: g['gold_d'], 'guard', 1.5)
    grip(cv, 3.2, 8.6, g['grip'], g['grip2'])
    disc(cv, 2.2, 0, 1.9, gem_color(g['gold']), 'pommel', 1.5)
    disc(cv, 10.0, 0, 1.25, gem_color(g['eye']), 'gem', 2.25)
    cv.outline()
    return pulse_gem(cv, frames, g['eye'])


def void(frames=12):
    g = {k: hexc(v) for k, v in dict(
        body='2B1B4A', edge='C9A6FF', dark='160C2A', rim='845ECC', star='F2E6FF', core='5B2C9C',
        guard='3B2A63', guard_d='1E1435', grip='241A3A', grip2='130D21', orb='B983FF').items()}
    cv = Canvas()
    prof = lambda t: 2.4 * (1 - t) ** 0.7 + 0.3
    blade(cv, 11.5, prof, g['body'], g['edge'], g['rim'], None, 1.0, 0.5)
    # ring guard
    cv.fill(lambda s, w: 2.0 <= math.hypot(s - 10.0, w) <= 3.4,
            lambda s, w, x, y: g['guard'] if w < 0 else g['guard_d'], 'guard', 1.5)
    cv.fill(lambda s, w: math.hypot(s - 10.0, w) < 2.0, lambda s, w, x, y: g['core'], 'gem', 1.75)
    grip(cv, 3.2, 7.2, g['grip'], g['grip2'])
    disc(cv, 2.0, 0, 1.8, gem_color(g['orb']), 'pommel', 1.75)
    cv.outline()
    base = cv
    out = []
    rnd = random.Random(7)
    stars = [(rnd.uniform(14, 34), rnd.uniform(-1.2, 1.2)) for _ in range(14)]
    for f in range(frames):
        c = base.copy()
        ph = f / frames
        for y in range(SIZE):
            for x in range(SIZE):
                s, w = Canvas.sw(x, y)
                if c.part[y][x] == 'blade' and c.color[y][x] == g['body']:
                    # swirling nebula bands drifting towards the tip
                    v = math.sin((s * 0.55 - ph * 2 * math.pi) + w * 1.3)
                    if v > 0.55:
                        c.color[y][x] = g['core']
                if c.part[y][x] == 'gem':
                    c.color[y][x] = mix(g['core'], g['orb'], 0.5 + 0.5 * math.sin(ph * 2 * math.pi))
        for i, (ss, ww) in enumerate(stars):
            tw = (ph * 3 + i * 0.37) % 1.0
            if tw < 0.35:
                x, y = from_sw(ss, ww)
                if c.part[y][x] == 'blade':
                    c.color[y][x] = g['star']
        out.append(c)
    return out


def inferno(frames=8):
    g = {k: hexc(v) for k, v in dict(
        body='DC6A1D', edge='FFD24A', dark='8A2A0B', ridge='FFF1A8', hot='FF9A2E',
        horn='3A2420', horn_d='1E1210', grip='4A2A1E', grip2='2C160F', ember='FF5A1A').items()}
    cv = Canvas()
    # flamberge: wavy blade
    prof = lambda t: (2.3 * (1 - t) ** 0.6 + 0.4) * (1 + 0.38 * math.sin(t * 19))
    blade(cv, 11.5, prof, g['body'], g['edge'], g['dark'], g['ridge'], 1.0, 0.5)
    # horned guard curving towards the tip
    crossguard(cv, 10.0, 5.0, 0.85, g['horn'], g['horn_d'], 1.75,
               shape=lambda r: 0 if r < 0.6 else -0.0)
    cv.fill(lambda s, w: 10.5 <= s <= 13.2 and 3.6 <= abs(w) <= 5.2 and (s - 10.5) * 0.9 >= abs(abs(w) - 4.4) * 1.1 - 0.4,
            lambda s, w, x, y: g['horn'], 'guard', 1.5)
    grip(cv, 3.2, 8.8, g['grip'], g['grip2'])
    disc(cv, 2.2, 0, 1.8, gem_color(g['ember']), 'pommel', 1.75)
    disc(cv, 10.0, 0, 1.1, gem_color(g['ember']), 'gem', 2.0)
    cv.outline()
    base = cv
    out = []
    for f in range(frames):
        c = base.copy()
        ph = f / frames
        for y in range(SIZE):
            for x in range(SIZE):
                s, w = Canvas.sw(x, y)
                if c.part[y][x] == 'blade' and c.color[y][x] in (g['body'], g['ridge']):
                    v = math.sin(s * 0.9 - ph * 2 * math.pi * 2 + math.sin(w * 2.1 + ph * 6.28))
                    if v > 0.6:
                        c.color[y][x] = g['hot']
                    elif v < -0.85:
                        c.color[y][x] = shade(g['body'], -0.15)
                if c.part[y][x] in ('gem', 'pommel') and c.color[y][x] == g['ember']:
                    c.color[y][x] = mix(g['ember'], g['edge'], 0.5 + 0.5 * math.sin(ph * 4 * math.pi))
        out.append(c)
    return out


def frost(frames=10):
    g = {k: hexc(v) for k, v in dict(
        body='93D7EF', edge='F4FBFF', dark='3F7FA8', ridge='FFFFFF', crystal='BDEBFF',
        guard='6FB6DA', guard_d='2F6E96', grip='DCEFF7', grip2='7FB0C8', gem='4FD3FF').items()}
    cv = Canvas()
    # crystalline blade: faceted, widest a third of the way up
    def prof(t):
        return 2.2 + 1.0 * math.sin(min(t, 0.85) / 0.85 * math.pi) * 0.9 - 2.0 * max(0, t - 0.6) / 0.4 * 1.1
    blade(cv, 11.5, lambda t: max(0.3, prof(t)), g['body'], g['edge'], g['dark'], g['ridge'], 1.0, 0.5)
    # ice shards growing from the blade
    for (ss, side, ln) in ((16, -1, 2.6), (21, 1, 2.4), (25.5, -1, 2.0), (29, 1, 1.6)):
        cv.fill(lambda s, w, ss=ss, side=side, ln=ln: 0 <= (w * side) - 1.5 <= ln and abs(s - ss - ((w * side) - 1.5) * 0.9) <= 0.75 - ((w * side) - 1.5) / ln * 0.5,
                lambda s, w, x, y: g['crystal'], 'blade', 0.75)
    # crystal guard: three spikes on each side
    crossguard(cv, 10.0, 4.6, 0.8, g['guard'], g['guard_d'], 1.5)
    for side in (-1, 1):
        cv.fill(lambda s, w, side=side: 0 <= w * side - 3.2 <= 2.2 and abs(s - 10.0 - (w * side - 3.2) * 0.8) <= 0.9 - (w * side - 3.2) * 0.35,
                lambda s, w, x, y: g['crystal'], 'guard', 1.5)
    grip(cv, 3.2, 8.8, g['grip'], g['grip2'])
    disc(cv, 2.2, 0, 1.8, gem_color(g['gem']), 'pommel', 1.75)
    disc(cv, 10.0, 0, 1.15, gem_color(g['gem']), 'gem', 2.0)
    cv.outline()
    return shimmer(cv, frames, g['ridge'], parts=('blade',))


def storm(frames=10):
    g = {k: hexc(v) for k, v in dict(
        body='3F67B5', edge='C9E2FF', dark='1F3770', bolt='FFE45C', bolt2='FFFFFF',
        guard='E8C64A', guard_d='8F6E16', grip='2A3550', grip2='161D30', gem='8FD3FF').items()}
    cv = Canvas()
    prof = lambda t: 2.3 * (1 - t) ** 0.5 + 0.4
    blade(cv, 11.5, prof, g['body'], g['edge'], g['dark'], None, 1.0, 0.5)
    # spiked guard (lightning fork ends)
    crossguard(cv, 10.0, 5.4, 0.8, g['guard'], g['guard_d'], 1.75)
    for side in (-1, 1):
        cv.fill(lambda s, w, side=side: 3.8 <= w * side <= 5.6 and 10.0 <= s <= 12.6 and abs((s - 10.0) - (w * side - 3.8) * 1.4) <= 0.8,
                lambda s, w, x, y: g['guard'], 'guard', 1.5)
    grip(cv, 3.2, 8.8, g['grip'], g['grip2'])
    disc(cv, 2.2, 0, 1.8, gem_color(g['guard']), 'pommel', 1.75)
    disc(cv, 10.0, 0, 1.1, gem_color(g['gem']), 'gem', 2.0)
    cv.outline()
    # zigzag lightning inlay along the blade
    bolt = []
    for i in range(48):
        s = 13 + i * 0.45
        w = 0.9 * (1 if int(s / 2.6) % 2 == 0 else -1) * (((s % 2.6) / 2.6) * 2 - 1)
        bolt.append((s, w))
    out = []
    for f in range(frames):
        c = cv.copy()
        lit = f % 5 in (0, 1) or f == 6
        for s, w in bolt:
            x, y = from_sw(s, w)
            if c.part[y][x] == 'blade':
                c.color[y][x] = g['bolt2'] if (lit and f % 5 == 0) else (g['bolt'] if lit else shade(g['bolt'], -0.3))
        out.append(c)
    return out


def emerald(frames=10):
    g = {k: hexc(v) for k, v in dict(
        body='35B579', edge='B8F5D0', dark='166B43', ridge='E5FFF0', vein='1E8A55',
        gold='E8C64A', gold_d='8F6E16', grip='3E5A2A', grip2='24381A', gem='3DFF9A').items()}
    cv = Canvas()
    # leaf blade: widest in the middle
    prof = lambda t: 0.4 + 2.9 * math.sin(min(1.0, t * 1.05) * math.pi) ** 0.8 * (1 - 0.35 * t)
    blade(cv, 11.5, prof, g['body'], g['edge'], g['dark'], g['ridge'], 1.0, 0.5)
    # leaf veins
    for y in range(SIZE):
        for x in range(SIZE):
            s, w = Canvas.sw(x, y)
            if cv.part[y][x] == 'blade' and 14 < s < 32 and 0.7 < abs(w) < 2.3 and abs((s % 3.0) - abs(w) * 0.8) < 0.35:
                cv.color[y][x] = g['vein']
    # vine guard
    crossguard(cv, 10.0, 4.4, 0.85, g['gold'], g['gold_d'], 1.5,
               shape=lambda r: 0.5 if r > 0.8 else 0)
    grip(cv, 3.2, 8.8, g['grip'], g['grip2'], wrap=1.6)
    disc(cv, 2.2, 0, 2.0, gem_color(g['gem']), 'pommel', 2.0)
    disc(cv, 10.0, 0, 1.2, gem_color(g['gem']), 'gem', 2.25)
    cv.outline()
    return shimmer(cv, frames, g['ridge'], parts=('blade', 'gem', 'pommel'))


def crimson(frames=8):
    g = {k: hexc(v) for k, v in dict(
        body='B32E49', edge='FF8FA6', dark='5C0A1A', ridge='FFD0DA', blood='7A0F22',
        guard='2A1418', guard_d='12080A', silver='C9C2C6', grip='3A1A20', grip2='1F0C10', gem='FF2E4D').items()}
    cv = Canvas()
    # slightly curved (sabre) blade: the axis bends towards the shadow side
    def curved(s, w):
        return w - 0.012 * max(0, s - 11.5) ** 2 * 0.35
    def prof(t):
        return 2.1 * (1 - t) ** 0.45 + 0.35
    def test(s, w):
        if s < 11.5 or s > LENGTH:
            return False
        return abs(curved(s, w)) <= prof((s - 11.5) / (LENGTH - 11.5))
    def paint(s, w, x, y):
        u = curved(s, w) / max(prof((s - 11.5) / (LENGTH - 11.5)), 0.01)
        if u < -0.5:
            return g['edge']
        if u > 0.55:
            return g['dark']
        if -0.15 <= u <= 0.2 and s < 31:
            return g['blood']                           # blood channel
        return g['body']
    cv.fill(test, paint, 'blade', lambda s, w: 1.0 if abs(curved(s, w)) < prof((s - 11.5) / 26) * 0.5 else 0.5)
    # crescent guard opening towards the blade
    cv.fill(lambda s, w: 3.0 <= math.hypot(s - 7.4, w) <= 4.4 and s >= 8.6,
            lambda s, w, x, y: g['guard'] if w < 0 else g['guard_d'], 'guard', 1.5)
    cv.fill(lambda s, w: 3.3 <= math.hypot(s - 7.4, w) <= 3.8 and s >= 9.2 and w < 0,
            lambda s, w, x, y: g['silver'], 'guard', 1.5)
    grip(cv, 3.2, 8.6, g['grip'], g['grip2'])
    disc(cv, 2.2, 0, 1.8, gem_color(g['silver']), 'pommel', 1.5)
    disc(cv, 10.6, 0, 1.15, gem_color(g['gem']), 'gem', 2.0)
    cv.outline()
    return pulse_gem(cv, frames, g['gem'], channel=g['blood'], channel_hot=g['body'])


def solar(frames=10):
    g = {k: hexc(v) for k, v in dict(
        body='ECBA44', edge='FFF4C2', dark='A06A12', ridge='FFFFFF', sun='FFD84A', sun_d='E08A1E',
        ray='FFE98A', grip='F2E2B8', grip2='B89A5A', gem='FF8A1E').items()}
    cv = Canvas()
    prof = lambda t: 2.7 * (1 - t) ** 0.6 + 0.35
    blade(cv, 12.0, prof, g['body'], g['edge'], g['dark'], g['ridge'], 1.0, 0.5)
    # sun disc guard with rays
    for k in range(8):
        a = k * math.pi / 4
        cv.fill(lambda s, w, a=a: 2.4 <= math.hypot(s - 10.0, w) <= 4.6 and
                abs(math.atan2(w, s - 10.0) - a + (2 * math.pi if abs(math.atan2(w, s - 10.0) - a) > math.pi else 0)) < 0.22,
                lambda s, w, x, y: g['ray'], 'guard', 1.25)
    disc(cv, 10.0, 0, 2.6, lambda ds, dw: g['sun'] if dw < 0.2 else g['sun_d'], 'guard', 1.75)
    disc(cv, 10.0, 0, 1.2, gem_color(g['gem']), 'gem', 2.25)
    grip(cv, 3.2, 7.6, g['grip'], g['grip2'])
    disc(cv, 2.2, 0, 1.7, gem_color(g['sun']), 'pommel', 1.5)
    cv.outline()
    return shimmer(cv, frames, g['ridge'], parts=('blade', 'guard', 'gem'), width=3.0)


def shadow(frames=12):
    g = {k: hexc(v) for k, v in dict(
        body='3A384C', edge='B9B5D6', dark='15141D', ridge='6D6B84', rune='A86BFF',
        guard='25232F', guard_d='0E0D13', grip='2B2935', grip2='16151C', gem='9B5CFF').items()}
    cv = Canvas()
    # long, thin, wickedly tapered blade
    prof = lambda t: 1.75 * (1 - t) ** 0.35 + 0.15
    blade(cv, 11.0, prof, g['body'], g['edge'], g['dark'], None, 1.0, 0.5)
    # swept-back curved guard
    cv.fill(lambda s, w: abs(w) <= 4.8 and abs(s - (10.0 - 0.08 * w * w)) <= 0.8,
            lambda s, w, x, y: g['guard'] if w < 0 else g['guard_d'], 'guard', 1.5)
    grip(cv, 3.0, 8.8, g['grip'], g['grip2'])
    disc(cv, 2.0, 0, 1.6, gem_color(g['gem']), 'pommel', 1.5)
    disc(cv, 10.0, 0, 1.0, gem_color(g['gem']), 'gem', 2.0)
    cv.outline()
    runes = [(14 + i * 2.6, 0) for i in range(8)]
    out = []
    for f in range(frames):
        c = cv.copy()
        for i, (s, w) in enumerate(runes):
            x, y = from_sw(s, w)
            glow = (f - i) % frames
            if c.part[y][x] == 'blade':
                c.color[y][x] = g['rune'] if glow < 3 else shade(g['rune'], -0.55)
        if f % 6 < 3:
            for y in range(SIZE):
                for x in range(SIZE):
                    if c.part[y][x] in ('gem', 'pommel') and c.color[y][x] == g['gem']:
                        c.color[y][x] = shade(g['gem'], 0.25)
        out.append(c)
    return out


def cosmic(frames=12):
    g = {k: hexc(v) for k, v in dict(
        body='22195C', edge='C9B8FF', dark='0E0A2A', neb='4B3AA8', neb2='8A3FA8', star='FFFFFF',
        star2='FFE6FF', moon='E9E4FF', moon_d='9E93D8', grip='2A2450', grip2='15112E', gem='FF7AF0').items()}
    cv = Canvas()
    prof = lambda t: 2.5 * (1 - t) ** 0.6 + 0.35
    blade(cv, 11.5, prof, g['body'], g['edge'], g['neb'], None, 1.0, 0.5)
    # crescent-moon guard
    cv.fill(lambda s, w: math.hypot(s - 9.6, w) <= 4.3 and math.hypot(s - 11.0, w) >= 3.4 and abs(w) <= 4.6,
            lambda s, w, x, y: g['moon'] if w < 0.5 else g['moon_d'], 'guard', 1.5)
    grip(cv, 3.2, 8.4, g['grip'], g['grip2'])
    # four-point star pommel
    cv.fill(lambda s, w: abs(s - 2.2) + abs(w) * 2.2 <= 2.4 or abs(w) + abs(s - 2.2) * 2.2 <= 2.4,
            lambda s, w, x, y: g['star2'] if w < 0 else g['moon_d'], 'pommel', 1.75)
    disc(cv, 10.6, 0, 1.0, gem_color(g['gem']), 'gem', 2.0)
    cv.outline()
    rnd = random.Random(42)
    stars = [(rnd.uniform(13, 35), rnd.uniform(-1.6, 1.6), rnd.random()) for _ in range(16)]
    out = []
    for f in range(frames):
        c = cv.copy()
        ph = f / frames
        for y in range(SIZE):
            for x in range(SIZE):
                s, w = Canvas.sw(x, y)
                if c.part[y][x] == 'blade' and c.color[y][x] == g['body']:
                    v = math.sin(s * 0.45 + w * 0.9 + ph * 2 * math.pi) + math.sin(s * 0.21 - ph * 2 * math.pi)
                    if v > 1.1:
                        c.color[y][x] = g['neb2']
                    elif v > 0.45:
                        c.color[y][x] = g['neb']
        for s, w, off in stars:
            x, y = from_sw(s, w)
            tw = (ph + off) % 1.0
            if c.part[y][x] == 'blade' and tw < 0.5:
                c.color[y][x] = g['star'] if tw < 0.25 else g['star2']
        out.append(c)
    return out


# ---------------------------------------------------------------------------------------------
# Animation helpers
# ---------------------------------------------------------------------------------------------

def from_sw(s, w):
    x = ORIGIN[0] + s * AXIS[0] + w * ACROSS[0]
    y = ORIGIN[1] + s * AXIS[1] + w * ACROSS[1]
    return max(0, min(SIZE - 1, int(x))), max(0, min(SIZE - 1, int(y)))


def shimmer(base, frames, light, parts=('blade',), width=2.2):
    """A band of light sweeping from the guard to the tip, then a pause."""
    out = []
    span = LENGTH + 10
    for f in range(frames):
        c = base.copy()
        pos = -5 + span * f / (frames * 0.7)
        for y in range(SIZE):
            for x in range(SIZE):
                if c.part[y][x] in parts and c.color[y][x] is not None:
                    s, w = Canvas.sw(x, y)
                    d = abs((s + w) - pos)
                    if d < width:
                        c.color[y][x] = mix(c.color[y][x], light, 0.65 * (1 - d / width))
        out.append(c)
    return out


def pulse_gem(base, frames, gem, channel=None, channel_hot=None):
    out = []
    for f in range(frames):
        c = base.copy()
        k = 0.5 + 0.5 * math.sin(f / frames * 2 * math.pi)
        for y in range(SIZE):
            for x in range(SIZE):
                col = c.color[y][x]
                if col == gem:
                    c.color[y][x] = mix(gem, (255, 255, 255), 0.35 * k)
                elif channel and col == channel:
                    c.color[y][x] = mix(channel, channel_hot, 0.6 * k)
        out.append(c)
    return out


SWORDS = {
    'dragon': dragon, 'void': void, 'inferno': inferno, 'frost': frost, 'storm': storm,
    'emerald': emerald, 'crimson': crimson, 'solar': solar, 'shadow': shadow, 'cosmic': cosmic,
}
