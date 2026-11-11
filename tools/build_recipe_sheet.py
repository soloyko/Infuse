#!/usr/bin/env python3
"""Builds docs/recipes.html: every effect recipe on one page (screenshot it for docs/recipes.png).

Run from the repo root:  python3 tools/build_recipe_sheet.py
"""
import base64
import json
import re
from pathlib import Path

import yaml

ROOT = Path(".")
SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"


def caps(s):
    return "".join(SMALL["abcdefghijklmnopqrstuvwxyz".index(c.lower())] if c.isalpha() and c.isascii() else c for c in s)


def effects():
    src = (ROOT / "src/main/java/me/infuse/effect/EffectType.java").read_text()
    pat = re.compile(r'^\s{4}([A-Z]+)\("([^"]+)",\s*0x([0-9A-Fa-f]{6}),', re.M)
    return [{"id": m.group(1).lower(), "name": m.group(2), "color": "#" + m.group(3).lower(), "index": i}
            for i, m in enumerate(pat.finditer(src))]


def recipes():
    data = yaml.safe_load((ROOT / "src/main/resources/recipes.yml").read_text())["recipes"]
    out = {}
    for rid, r in data.items():
        grid = []
        rows = list(r["shape"]) + [""] * (3 - len(r["shape"]))
        for row in rows:
            for ch in row.ljust(3):
                ing = r["ingredients"].get(ch) if ch != " " else None
                if isinstance(ing, list):
                    ing = " / ".join(i.lower().replace("_", " ") for i in ing[:1]) + (" (any)" if len(ing) > 1 else "")
                elif ing:
                    ing = ing.lower().replace("_", " ")
                grid.append(ing)
        out.setdefault(r["effect"], []).append(grid)
    # Merge recipes for the same effect that only differ in a few slots ("Ender Eye or Dragon Egg")
    for eid, grids in out.items():
        if len(grids) > 1 and all(sum(a != b for a, b in zip(grids[0], g)) <= 2 for g in grids[1:]):
            merged = []
            for i in range(9):
                opts = []
                for g in grids:
                    if g[i] not in opts:
                        opts.append(g[i])
                merged.append(" or ".join(o for o in opts if o) or None)
            out[eid] = [merged]
    return out


def short(name):
    if not name:
        return ""
    name = name.replace(" armor trim smithing template", " trim").replace(" smithing template", " template")
    name = name.replace("enchanted golden apple", "enchanted golden apple").replace("netherite upgrade template", "netherite upgrade")
    return name


def lighten(hex_color, k=0.55):
    n = int(hex_color[1:], 16)
    ch = [((n >> s) & 255) for s in (16, 8, 0)]
    return "#" + "".join(f"{round(v + (255 - v) * k):02x}" for v in ch)


icons = "data:image/png;base64," + base64.b64encode(
    (ROOT / "resourcepack/assets/infuse/textures/icons/effects.png").read_bytes()).decode()
effs = effects()
recs = recipes()

cards = []
for e in effs:
    grids = recs.get(e["id"], [])
    grid_html = "".join(
        '<div class="grid">' + "".join(
            f'<div class="slot{" empty" if not g else ""}">{short(g)}</div>' for g in grid) + '</div>'
        for grid in grids)
    k = e["index"] * 4
    icon = (f'<div class="icon" style="background-image:url({icons});background-size:{504 * 3}px {1728 * 3}px;'
            f'background-position:0 {-k * 72}px"></div>')
    aug = (f'<div class="icon" style="background-image:url({icons});background-size:{504 * 3}px {1728 * 3}px;'
           f'background-position:0 {-(k + 1) * 72}px"></div>')
    cards.append(f'''
    <div class="card" style="--c:{e["color"]};--c2:{lighten(e["color"])}">
      <div class="head">{icon}<div class="name">{caps(e["name"])}</div>{aug}</div>
      <div class="grids">{grid_html}</div>
      {'<div class="note">' + caps("either recipe") + '</div>' if len(grids) > 1 else ''}
    </div>''')

html = f'''<!DOCTYPE html><html><head><meta charset="utf-8"><style>
* {{ box-sizing: border-box; }}
body {{ margin: 0; width: 1600px; background:
  radial-gradient(900px 500px at 10% -5%, #2a0f3a 0%, transparent 60%),
  radial-gradient(800px 500px at 100% 0%, #1b1046 0%, transparent 60%), #0e0b14;
  font-family: "Noto Sans", "DejaVu Sans", sans-serif; color: #ece6f5; padding: 36px 36px 30px; }}
h1 {{ margin: 0; font-size: 54px; letter-spacing: 3px; text-align: center;
  background: linear-gradient(90deg, #d11fd1, #7f3cff); -webkit-background-clip: text; background-clip: text; color: transparent; }}
.sub {{ text-align: center; color: #9a8fb0; font-size: 17px; margin: 4px 0 26px; }}
.sheet {{ display: flex; flex-wrap: wrap; justify-content: center; gap: 18px; }}
.sheet .card {{ width: calc((100% - 54px) / 4); }}
.card {{ position: relative; background: linear-gradient(180deg, #1f1829, #17121f); border: 1px solid #2e2440;
  border-radius: 12px; padding: 14px; overflow: hidden; }}
.card::before {{ content: ""; position: absolute; right: -60px; top: -60px; width: 200px; height: 200px; border-radius: 50%;
  background: var(--c); opacity: .14; filter: blur(30px); }}
.head {{ display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }}
.icon {{ width: 72px; height: 72px; image-rendering: pixelated; background-repeat: no-repeat; flex: none; }}
.name {{ font-size: 28px; font-weight: bold; text-align: center; flex: 1;
  background: linear-gradient(90deg, var(--c), var(--c2)); -webkit-background-clip: text; background-clip: text; color: transparent;
  filter: drop-shadow(0 0 10px var(--c)); }}
.grids {{ display: flex; flex-direction: column; gap: 8px; }}
.grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; padding: 6px; background: #8b8b8b;
  border: 3px solid; border-color: #fff #555 #555 #fff; }}
.slot {{ height: 62px; background: #8b8b8b; box-shadow: inset 2px 2px 0 #373737, inset -2px -2px 0 #fff;
  display: flex; align-items: center; justify-content: center; text-align: center; padding: 4px;
  font-size: 12.5px; line-height: 1.15; color: #1d1d1d; font-weight: 600; text-transform: capitalize; }}
.slot.empty {{ color: transparent; }}
.note {{ text-align: center; color: #9a8fb0; font-size: 13px; margin-top: 6px; }}
.foot {{ text-align: center; color: #9a8fb0; font-size: 15px; margin-top: 22px; }}
</style></head><body>
<h1>{caps("Infuse Recipes")}</h1>
<div class="sub">{caps("Craft in a brewing stand")} · {caps("1st craft starts a ritual for the augmented effect")} · {caps("crafts 2-4 give regular effects")}</div>
<div class="sheet">{"".join(cards)}</div>
<div class="foot">{caps("Left icon: regular")} · {caps("Right icon: augmented")}</div>
</body></html>'''

out = ROOT / "docs/recipes.html"
out.write_text(html)
print(f"wrote {out} ({len(effs)} effects)")
