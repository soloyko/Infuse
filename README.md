# Infuse Ultimate

The whole Infuse SMP suite in one place for **Paper 1.21.11** — four plugins united by a hub.

## ⬇ Download

**[⬇ Infuse-Ultimate-1.0.0.zip](https://github.com/soloyko/Infuse/raw/claude/infuse-ultimate/download/Infuse-Ultimate-1.0.0.zip)** — every plugin jar + the merged resource pack + install guide.

Drop every jar from the zip's `plugins/` folder into your server's `plugins/`, restart, and run `/iu`.

## What's inside

| Module | Command | What it adds |
|---|---|---|
| ⚔️ **Customs** 2.9.0 | `/customs` | 53 legendary 3D-modelled weapons, forged at stone altars |
| 🩸 **Bloodbound** 1.0.0 | `/bloodbound`, `/bb` | Blood-powered PvP weapon kits that grow from your kills |
| ✨ **Infuse SMP** 1.0.0 | `/infuse` | Infusable effects & activated sparks, HUD, and `/trust` allies |
| 🌌 **Limitless Arsenal** 1.5.8 | `/coolability` | 24 energy-cast relic weapons with their own altars |
| 🧭 **InfuseCore** 1.0.0 | `/iu` | The hub that ties it all together |

### InfuseCore — the hub

InfuseCore adds no game content of its own. It discovers which modules are installed and presents
them as one product:

- `/iu` — a GUI menu, one icon per module (installed = glowing + version + click-to-open; missing = greyed out)
- `/iu status` — a live module matrix with clickable commands
- `/iu pack` — (re)apply the single merged resource pack (Customs' 3D models + Infuse's HUD)
- A join welcome listing active modules, and a console banner on startup

You can run any subset of modules — InfuseCore only shows what's present. It touches none of the
module code, so every feature works exactly as it did standalone.

## Build from source

```bash
./gradlew suite          # build every module jar (Customs, Bloodbound, Infuse, InfuseCore)
./gradlew test           # run every module's test suite
bash tools/build-release.sh   # assemble download/Infuse-Ultimate-<ver>.zip (jars + merged pack)
```

Requires JDK 21. Paper API is resolved from the PaperMC Maven repo.

## Layout

```
modules/customs/      Customs plugin (source)
modules/bloodbound/   Bloodbound plugin (source)
modules/infuse-smp/   Infuse SMP plugin (source)
infusecore/           InfuseCore hub plugin (source)
vendor/               Limitless Paper jar (datapack-driven; ships prebuilt)
tools/build-release.sh   release assembler
docs/INSTALL.md       the install guide bundled as README.txt
download/             the built release bundle + merged resource pack
```

## Credits

Customs 3D models by **Blades of Majestica**. Infuse HUD by **TurboJax**. Limitless Enchantments by
**Codex** (Paper port by mujtaba). Each module keeps its own authors and licences.
