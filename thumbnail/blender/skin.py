"""Standard 64x64 Minecraft-layout skin rebuilt from the reference render:
black hoodie, messy black hair covering most of the face, dark-brown skin,
one visible grey/white eye. Returns float RGBA (sRGB), row 0 = top."""
import random
import numpy as np


def _hex(c):
    c = c.lstrip('#')
    return [int(c[i:i + 2], 16) / 255 for i in (0, 2, 4)] + [1.0]


def build_skin_pixels():
    rng = random.Random(7)
    px = np.zeros((64, 64, 4), np.float32)

    def put(x, y, col):
        px[y, x] = _hex(col)

    def noisy(x, y, w, h, cols):
        for j in range(h):
            for i in range(w):
                put(x + i, y + j, rng.choice(cols))

    HAIR = ['#151515', '#1c1c1c', '#242424', '#191919', '#2c2c2c']
    CLOTH = ['#1b1b1b', '#1f1f1f', '#232323', '#1d1d1d']
    SKIN, SKIN2 = '#3a2414', '#2e1c10'

    noisy(0, 0, 32, 16, HAIR)       # head
    noisy(16, 16, 24, 16, CLOTH)    # body
    noisy(40, 16, 16, 16, CLOTH)    # right arm
    noisy(32, 48, 16, 16, CLOTH)    # left arm
    noisy(0, 16, 16, 16, CLOTH)     # right leg
    noisy(16, 48, 16, 16, CLOTH)    # left leg

    face = ['HHHHHHHH', 'HHHHHHHH', 'HHHHHHHH', 'HHdgHHHH',
            'HHWLBHHH', 'HHBBBBHH', 'HHHbBBHH', 'HHHHbHHH']
    cmap = {'d': '#3c3c40', 'g': '#5d5d63', 'W': '#ece8e8', 'L': '#a9a9ae', 'B': SKIN, 'b': SKIN2}
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            if ch in cmap:
                put(8 + x, 8 + y, cmap[ch])

    # hat overlay: hair volume
    noisy(40, 0, 8, 8, HAIR)        # overlay top (bottom stays transparent)
    noisy(32, 8, 8, 8, HAIR)        # right side
    noisy(48, 8, 16, 8, HAIR)       # left side + back
    fringe = ['HHHHHHHH', 'HHHHHHHH', 'HH.HHHHH', 'H...HHHH',
              'H....HHH', '......HH', '......HH', '.......H']
    for y, row in enumerate(fringe):
        for x, ch in enumerate(row):
            if ch == 'H':
                put(40 + x, 8 + y, rng.choice(HAIR))
    for x in range(32, 64):
        if not (40 <= x < 48) and rng.random() < 0.45:
            px[15, x] = 0
    for x in range(20, 28):
        put(x, 36, '#2a2a2a')       # hood seam on jacket layer
    return px
