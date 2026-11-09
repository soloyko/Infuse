# Infuse

A Paper **1.21.11** plugin for an Infuse-style SMP: craftable effects with passives and activated **sparks**, shown as icons above the hotbar via a resource pack.

## Build

```
gradle build
```

Outputs:
- `build/libs/Infuse-1.0.0.jar` – the plugin (drop into `plugins/`)
- `build/InfusePack.zip` – the resource pack (from `/resourcepack`, MIT, by TurboJax)

Host the zip somewhere with a direct download link and set `resource-pack.url` (and optionally `sha1`) in `config.yml` to push it to players, or have players install it themselves.

## Effects

Emerald, Ender, Feather, Fire, Frost, Haste, Heart, Invisibility, Ocean, Regeneration, Speed, Strength, Thunder, Apophis, Thief. Run `/infuses` in game for what each does.

- Players have two slots: **primary** (left) and **secondary** (right). Drink an effect bottle to equip it.
- Augmented effects have half the spark cooldown (`cooldowns.augmented-multiplier`).
- On death: the primary drops if equipped, otherwise the secondary.

## Customising

- **`messages.yml`**: every chat message, title, boss bar and item line (MiniMessage). Use `<caps>text</caps>` for small caps; set a message to `""` to hide it.
- **`config.yml`**:
  - `style`: small-caps and gradient effect names
  - `allies.mode`: `team` or `none`
  - per-effect `enabled`, `display-name` and `extra-potions`
  - `death.drop-mode`: `primary-first`, `secondary-first`, `random`, `both`, `none`
  - per-effect craft limits, augmented craft counts and ritual lengths
  - `sparks.disabled-worlds`
  - `hud.format`
- **`recipes.yml`**: every recipe; add `enabled: false` to turn one off.

## Crafting

- Right-clicking a brewing stand opens a crafting table (`brewing.replace-with-crafting: false` restores brewing).
- Recipes live in `recipes.yml` and can be edited freely.
- Per effect: the 1st craft starts a 10-minute **ritual** and drops the augmented effect at the stand when it ends; crafts 2–4 give regular effects; after that it's uncraftable (`crafting.max-crafts`).
- Every craft broadcasts its coordinates.

## Commands

| Command | Description |
|---|---|
| `/infuses` | What every effect does |
| `/controls` | Toggle Offhand (F = primary, Sneak+F = secondary) / Command Keys |
| `/lspark`, `/rspark` | Activate primary / secondary spark |
| `/ldrain`, `/rdrain` | Turn an effect back into a bottle |
| `/swap` | Swap primary and secondary |
| `/infuse gui` | Preview effects (`infuse.gui`), take them (`infuse.gui.take`) |
| `/cleareffects <player>` | Clear a player's effects (`infuse.admin`) |
| `/cooldown <player>` | Reset a player's cooldowns (`infuse.admin`) |
| `/infuse give <player> <effect> [augmented]` | Give an effect bottle (`infuse.admin`) |
| `/infuse crafts`, `/infuse resetcrafts [effect]` | View / reset craft counts (`infuse.admin`) |
| `/infuse reload` | Reload `config.yml` and `recipes.yml` (`infuse.admin`) |

Allies (for sparks like Invisibility and Heart) are players on the same scoreboard team.

## Visual preview

Open `docs/preview.html` in a browser for an interactive preview of the HUD, every effect, rituals and the Apophis blind screen using the real pack textures. Regenerate it after changing effects, config or recipes with `python3 tools/build_preview.py`.
