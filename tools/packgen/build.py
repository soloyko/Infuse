"""Builds the OP Swords resource pack models and textures from swordart.py.

    python3 tools/packgen/build.py            # writes into resourcepack/
    python3 tools/packgen/build.py --check    # fails if resourcepack/ is out of date

Each 32x32 sprite is extruded into a 3D model: pixels are merged into boxes of equal
thickness, every face gets an explicit UV inside the texture (no atlas bleeding), and faces
hidden by thicker neighbours are dropped. Display transforms match vanilla handheld items,
so the swords are held exactly like vanilla swords.
"""
import io
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import swordart  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PACK = os.path.join(ROOT, 'resourcepack')
NS = 'op_swords'
SIZE = swordart.SIZE
SCALE = 1.25                      # model size relative to a vanilla sword
PX = 16 / SIZE * SCALE            # model units per sprite pixel
FRAME_TIME = 2                    # ticks per animation frame

# Vanilla item/handheld transforms. Third person keeps the bigger size; first person, GUI and
# item frames are scaled back so the larger model still fits.
G = round(1 / SCALE, 4)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    # First person is scaled back to vanilla size so the bigger sword doesn't block the view.
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68 * G] * 3},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68 * G] * 3},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [G, G, G]},
    "gui": {"scale": [G, G, G]},
}


def r(v):
    return round(v, 4)


def mx(x):
    return r(8 + (x - SIZE / 2) * PX)


def my(y):
    return r(8 + (SIZE / 2 - y) * PX)


def u(x):
    return r(x * 16 / SIZE)


def rects(canvas):
    """Greedy-merge opaque pixels into rectangles of equal depth."""
    used = [[False] * SIZE for _ in range(SIZE)]
    out = []
    for y in range(SIZE):
        for x in range(SIZE):
            if used[y][x] or canvas.color[y][x] is None:
                continue
            d = canvas.depth[y][x]
            x1 = x
            while x1 < SIZE and not used[y][x1] and canvas.color[y][x1] is not None and canvas.depth[y][x1] == d:
                x1 += 1
            y1 = y + 1
            while y1 < SIZE and all(not used[y1][i] and canvas.color[y1][i] is not None and canvas.depth[y1][i] == d
                                    for i in range(x, x1)):
                y1 += 1
            for yy in range(y, y1):
                for xx in range(x, x1):
                    used[yy][xx] = True
            out.append((x, y, x1, y1, d))
    return out


def covered(canvas, cells, d):
    """True if every neighbouring cell exists and is at least as thick (face fully hidden)."""
    for x, y in cells:
        if not (0 <= x < SIZE and 0 <= y < SIZE) or canvas.color[y][x] is None or canvas.depth[y][x] < d:
            return False
    return True


def elements(canvas):
    els = []
    for x0, y0, x1, y1, d in rects(canvas):
        faces = {
            "south": {"uv": [u(x0), u(y0), u(x1), u(y1)], "texture": "#sword"},
            "north": {"uv": [u(x1), u(y0), u(x0), u(y1)], "texture": "#sword"},
        }
        if not covered(canvas, [(x, y0 - 1) for x in range(x0, x1)], d):
            faces["up"] = {"uv": [u(x0), u(y0), u(x1), u(y0 + 1)], "texture": "#sword"}
        if not covered(canvas, [(x, y1) for x in range(x0, x1)], d):
            faces["down"] = {"uv": [u(x0), u(y1 - 1), u(x1), u(y1)], "texture": "#sword"}
        if not covered(canvas, [(x1, y) for y in range(y0, y1)], d):
            faces["east"] = {"uv": [u(x1 - 1), u(y0), u(x1), u(y1)], "texture": "#sword"}
        if not covered(canvas, [(x0 - 1, y) for y in range(y0, y1)], d):
            faces["west"] = {"uv": [u(x0), u(y0), u(x0 + 1), u(y1)], "texture": "#sword"}
        half = d * SCALE / 2
        els.append({
            "from": [mx(x0), my(y1), r(8 - half)],
            "to": [mx(x1), my(y0), r(8 + half)],
            "faces": faces,
        })
    return els


def texture(frames):
    img = Image.new('RGBA', (SIZE, SIZE * len(frames)), (0, 0, 0, 0))
    for i, cv in enumerate(frames):
        for y in range(SIZE):
            for x in range(SIZE):
                c = cv.color[y][x]
                if c is not None:
                    img.putpixel((x, y + i * SIZE), c + (255,))
    return img


def pack_icon(sprites):
    icon = Image.new('RGBA', (128, 128), (24, 20, 36, 255))
    picks = ['cosmic', 'inferno', 'frost']
    for i, name in enumerate(picks):
        spr = sprites[name].resize((96, 96), Image.NEAREST)
        icon.alpha_composite(spr, (4 + i * 14, 28 - i * 14 + 12))
    return icon


def outputs():
    """Returns {relative path: bytes} for every generated file."""
    files = {}
    sprites = {}
    for name, fn in swordart.SWORDS.items():
        frames = fn()
        tex = texture(frames)
        sprites[name] = tex.crop((0, 0, SIZE, SIZE))
        buf = io.BytesIO()
        tex.save(buf, format='PNG', optimize=True)
        files[f'assets/{NS}/textures/item/{name}.png'] = buf.getvalue()
        if len(frames) > 1:
            files[f'assets/{NS}/textures/item/{name}.png.mcmeta'] = json.dumps(
                {"animation": {"frametime": FRAME_TIME, "interpolate": False}}, indent=2).encode() + b'\n'
        model = {
            "credit": "OP Swords - generated by tools/packgen",
            "gui_light": "front",
            "textures": {"sword": f"{NS}:item/{name}", "particle": f"{NS}:item/{name}"},
            "elements": elements(frames[0]),
            "display": DISPLAY,
        }
        files[f'assets/{NS}/models/item/{name}.json'] = json.dumps(model, separators=(',', ':')).encode() + b'\n'
        item = {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}
        files[f'assets/{NS}/items/{name}.json'] = json.dumps(item, indent=2).encode() + b'\n'
    buf = io.BytesIO()
    pack_icon(sprites).save(buf, format='PNG', optimize=True)
    files['pack.png'] = buf.getvalue()
    return files


def same(rel, old, new):
    """PNGs are compared by pixels: compression output differs between Pillow/zlib versions."""
    if old is None:
        return False
    if rel.endswith('.png'):
        a, b = Image.open(io.BytesIO(old)).convert('RGBA'), Image.open(io.BytesIO(new)).convert('RGBA')
        return a.size == b.size and a.tobytes() == b.tobytes()
    return old == new


GENERATED_DIRS = [f'assets/{NS}/textures/item', f'assets/{NS}/models/item', f'assets/{NS}/items']


def main():
    check = '--check' in sys.argv
    files = outputs()
    stale = []
    for rel, data in files.items():
        path = os.path.join(PACK, rel)
        old = open(path, 'rb').read() if os.path.exists(path) else None
        if not same(rel, old, data):
            stale.append(rel)
            if not check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, 'wb') as f:
                    f.write(data)
    # remove leftovers that are no longer generated
    for d in GENERATED_DIRS:
        full = os.path.join(PACK, d)
        for fname in sorted(os.listdir(full)) if os.path.isdir(full) else []:
            rel = f'{d}/{fname}'
            if rel not in files:
                stale.append(rel)
                if not check:
                    os.remove(os.path.join(full, fname))
    if check:
        if stale:
            print('Resource pack is out of date; run tools/packgen/build.py:\n  ' + '\n  '.join(stale))
            sys.exit(1)
        print('Resource pack is up to date.')
        return
    counts = {n: len(json.loads(files[f'assets/{NS}/models/item/{n}.json'])['elements']) for n in swordart.SWORDS}
    print('Wrote', len(files), 'files. Elements per model:', counts)


if __name__ == '__main__':
    main()
