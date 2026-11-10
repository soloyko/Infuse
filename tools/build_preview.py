#!/usr/bin/env python3
"""Builds docs/preview.html: a self-contained visual preview of Infuse using the resource pack art.

Run from the repo root:  python3 tools/build_preview.py
"""
import base64
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
TEX = ROOT / "resourcepack/assets/infuse/textures"


def b64(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


def parse_effects():
    src = (ROOT / "src/main/java/me/infuse/effect/EffectType.java").read_text()
    out = []
    pat = re.compile(
        r'^\s{4}([A-Z]+)\("([^"]+)",\s*0x([0-9A-Fa-f]{6}),\s*(true|false),\s*'
        r'List\.of\((.*?)\),\s*List\.of\((.*?)\)\)[,;]', re.S | re.M)
    for m in pat.finditer(src):
        strs = lambda s: re.findall(r'"((?:[^"\\]|\\.)*)"', s)
        out.append({
            "id": m.group(1).lower(), "name": m.group(2), "color": "#" + m.group(3).lower(),
            "hits": m.group(4) == "true", "passives": strs(m.group(5)), "sparks": strs(m.group(6)),
        })
    return out


def parse_bonus():
    src = (ROOT / "src/main/java/me/infuse/effect/Tune.java").read_text()
    out = {}
    for m in re.finditer(r'Map\.entry\(EffectType\.([A-Z]+), List\.of\((.*?)\)\)', src, re.S):
        out[m.group(1).lower()] = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2))
    return out


def parse_recipes():
    data = yaml.safe_load((ROOT / "src/main/resources/recipes.yml").read_text())["recipes"]
    out = {}
    for rid, r in data.items():
        grid = []
        for row in r["shape"]:
            for ch in row.ljust(3):
                ing = r["ingredients"].get(ch) if ch != " " else None
                if isinstance(ing, list):
                    ing = ing[0]
                grid.append(ing)
        out.setdefault(r["effect"], []).append({"id": rid, "grid": grid})
    return out


def cfg():
    return yaml.safe_load((ROOT / "src/main/resources/config.yml").read_text())


effects = parse_effects()
recipes = parse_recipes()
config = cfg()
data = {
    "effects": effects, "recipes": recipes,
    "cooldowns": config["cooldowns"], "durations": config["durations"], "bonus": parse_bonus(),
}

html = (ROOT / "tools/preview_template.html").read_text()
html = html.replace("__EFFECTS_PNG__", b64(TEX / "icons/effects.png"))
html = html.replace("__HITS_PNG__", b64(TEX / "icons/hit_tracker.png"))
html = html.replace("__COVER_PNG__", b64(TEX / "apophis/cover.png"))
html = html.replace("__AUG_PNG__", b64(TEX / "item/aug.png"))
html = html.replace("__DATA__", json.dumps(data))
out = ROOT / "docs/preview.html"
out.parent.mkdir(exist_ok=True)
out.write_text(html)
print(f"wrote {out} ({out.stat().st_size // 1024} KB, {len(effects)} effects)")
