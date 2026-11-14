"""Imports the Customs weapon models from the Blades of Majestica resource pack.

    python3 tools/import_majestica.py path/to/Blades_Of_Majestica.zip

Copies the chosen models (and their textures and animations) into resourcepack/ under the
customs namespace, so they are applied with the item_model component instead of the original
pack's rename-in-an-anvil trick. Edit SWORDS to swap a model, then re-run and rebuild the plugin.
Needs Pillow and numpy (for the pack icon).
"""
import io
import json
import os
import shutil
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
PACK = os.path.join(ROOT, 'resourcepack')
NS = 'customs'

# weapon id -> model file in the Majestica pack (assets/minecraft/models/item/<name>.json)
SWORDS = {
    'dragon': 'dragonslayingblade',
    'phoenix': 'pheonixgrace',
    'frost': 'frostblade',
    'storm': 'thunderbringer',
    'tide': 'aquantic_sacred_blade',
    'verdant': 'enigma',
    'plague': 'toxic_longsword',
    'crimson': 'vampiricneedle',
    'solar': 'righteous_relic',
    'void': 'demonlordsword',
    'cosmic': 'edgeoftheastralplane',
    'chrono': 'chrono_blade',
    'shadow': 'watcher_claymore',
    'soul': 'awakened_lichblade',
    'echo': 'sculksword',
    # not swords
    'hammer': 'hearthflame',
    'scythe': 'requiem_of_hell',
    'spear': 'sunbreak',
    'chakram': 'royalchakram',
    'axe': 'demonlordsgreataxe',
    'staff': 'arcanethyst',
    'trident': 'aquantictrident',
    'shuriken': 'amethyst_shuriken',
    'halberd': 'jadehalberd',
    'warglaive': 'watching_warglaive',
    'sawblade': 'cyberneticsawblade',
    'rapier': 'royalrapier',
    'khopesh': 'pharaohs_treasure',
    # added in 2.3: swords
    'katana': 'katana',
    'needle': 'hornetsneedle',
    'candle': 'waxweaver',
    'claymore': 'brimstone_claymore',
    'mirror': 'silverine_blade',
    'cutlass': 'sabre',
    # ...and other weapons
    'claws': 'soulclaws',
    'daggers': 'thousanddemondaggers',
    'mace': 'divinepunisher',
    'glaive': 'forest_guardian_glaive',
    'maul': 'powerfusehammer',
    'mantis': 'cybermantisblade',
    # added in 2.5
    'ribbon': 'ribboncleaver',
    'stars': 'stars_edge',
    'ashura': 'ashura_blade',
    'deathknight': 'death_knight_sword',
    'splitter': 'creationsplitter',
    # the faction weapons of Arc II
    'herosword': 'herosword',
    'molten': 'moltensword',
    'fallen': 'fallengodsword',
}

# Blockbench-only fields Minecraft ignores
DROP_KEYS = ('groups', 'format_version')

# The pack's first-person poses are very large; shrink them so the sword doesn't hide half the screen.
FIRST_PERSON_SCALE = 0.8
# ...and some long or wide weapons a bit more
FIRST_PERSON_EXTRA = {'trident': 0.7, 'chakram': 0.75}


class Source:
    """Reads files from the Majestica zip or an extracted copy of it."""

    def __init__(self, path):
        self.zip = zipfile.ZipFile(path) if zipfile.is_zipfile(path) else None
        self.dir = None if self.zip else path
        self.prefix = ''
        if self.zip:
            names = self.zip.namelist()
            hit = next((n for n in names if n.endswith('assets/minecraft/items/netherite_sword.json')), None)
            if hit is None:
                sys.exit('That zip does not look like Blades of Majestica (no netherite_sword item definition).')
            self.prefix = hit[:-len('assets/minecraft/items/netherite_sword.json')]

    def read(self, rel):
        if self.zip:
            try:
                return self.zip.read(self.prefix + rel)
            except KeyError:
                return None
        p = os.path.join(self.dir, rel)
        return open(p, 'rb').read() if os.path.exists(p) else None


def write(rel, data):
    path = os.path.join(PACK, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)


def import_sword(src, sword, model_name):
    raw = src.read(f'assets/minecraft/models/item/{model_name}.json')
    if raw is None:
        sys.exit(f'Model {model_name} not found in the pack.')
    model = json.loads(raw)
    for key in DROP_KEYS:
        model.pop(key, None)
    textures = model.setdefault('textures', {})
    copied = {}
    for key, ref in list(textures.items()):
        path = ref.split(':', 1)[-1]                       # e.g. item/frostsblade
        if path in copied:
            textures[key] = copied[path]
            continue
        data = src.read(f'assets/minecraft/textures/{path}.png')
        if data is None:
            continue                                       # a vanilla texture: leave as is
        base = path.rsplit('/', 1)[-1]
        write(f'assets/{NS}/textures/item/{sword}/{base}.png', data)
        meta = src.read(f'assets/minecraft/textures/{path}.png.mcmeta')
        if meta is not None:
            write(f'assets/{NS}/textures/item/{sword}/{base}.png.mcmeta', meta)
        copied[path] = f'{NS}:item/{sword}/{base}'
        textures[key] = copied[path]
    if 'particle' not in textures and textures:
        textures['particle'] = next(iter(textures.values()))
    for slot in ('firstperson_righthand', 'firstperson_lefthand'):
        pose = model.get('display', {}).get(slot)
        if pose and 'scale' in pose:
            factor = FIRST_PERSON_SCALE * FIRST_PERSON_EXTRA.get(sword, 1)
            pose['scale'] = [round(v * factor, 4) for v in pose['scale']]
    model['credit'] = 'Model from Blades of Majestica'
    write(f'assets/{NS}/models/item/{sword}.json', json.dumps(model, separators=(',', ':')).encode() + b'\n')
    item = {"model": {"type": "minecraft:model", "model": f"{NS}:item/{sword}"}}
    write(f'assets/{NS}/items/{sword}.json', json.dumps(item, indent=2).encode() + b'\n')
    return len(model.get('elements', []))


def pack_icon():
    sys.path.insert(0, os.path.dirname(__file__))
    import modelpreview
    from PIL import Image
    assets = os.path.join(PACK, 'assets')
    icon = Image.new('RGBA', (256, 256), (22, 18, 34, 255))
    mid = f'{NS}:item/dragon'
    gui = modelpreview.load_model(assets, mid).get('display', {}).get('gui', {})
    img = modelpreview.render(assets, mid, size=256, view=tuple(gui.get('rotation', [0, 0, 0])),
                              scale=gui.get('scale', [1])[0] * 1.35)
    bbox = img.getbbox()
    if bbox:  # centre the sword
        img = img.crop(bbox)
        icon.alpha_composite(img, ((256 - img.width) // 2, (256 - img.height) // 2))
    buf = io.BytesIO()
    icon.resize((128, 128), Image.LANCZOS).save(buf, format='PNG', optimize=True)
    write('pack.png', buf.getvalue())


def clean():
    """Removes previously imported weapons, keeping files made by other tools (altar, effects)."""
    base = os.path.join(PACK, 'assets', NS)
    for sub in ('items', 'models/item', 'textures/item'):
        folder = os.path.join(base, sub)
        if not os.path.isdir(folder):
            continue
        for name in os.listdir(folder):
            if name.startswith('altar') or name == 'fx':
                continue
            path = os.path.join(folder, name)
            shutil.rmtree(path) if os.path.isdir(path) else os.remove(path)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Source(sys.argv[1])
    clean()
    for sword, model_name in SWORDS.items():
        count = import_sword(src, sword, model_name)
        print(f'  {sword:8} <- {model_name} ({count} elements)')
    pack_icon()
    print(f'Imported {len(SWORDS)} swords into {os.path.relpath(PACK, ROOT)}/')


if __name__ == '__main__':
    main()
