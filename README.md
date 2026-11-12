# Infuse — OP Swords

A Paper **1.21.11** plugin that adds 10 legendary swords with 3D models (bundled resource pack),
passives, on-hit effects and two activated abilities each.

| Control (sword in main hand) | Does |
| --- | --- |
| Right-Click | Primary ability |
| Sneak + Right-Click | Secondary ability |

An action-bar HUD shows both cooldowns while a sword is held.

## The swords

| Sword | Passive (while held) | On hit | Right-Click | Sneak + Right-Click |
| --- | --- | --- | --- | --- |
| 🐉 **Dragon** | Resistance I, +50% knockback resistance | 25%: +4 bonus damage | **Dragon Breath** (12s): 8-pulse cone of dragonfire | **Wyrm Dive** (30s): launch up, crash down in a shockwave, no fall damage |
| 🌌 **Void** | Night Vision; carrying it saves you from the void | 20%: Wither II + steal 2 HP | **Void Step** (8s): blink up to 15 blocks | **Singularity** (35s): black hole that pulls enemies in, then implodes |
| 🔥 **Inferno** | Fire Resistance | Ignites; 15%: combustion splash | **Fireball Barrage** (6s): 3 exploding fireballs (no block fire) | **Inferno Nova** (25s): expanding ring of fire |
| ❄️ **Frost** | +4 armor, +2 toughness, freeze immunity | Slowness II + freeze | **Glacial Spike** (10s): ice projectile that impales | **Blizzard** (30s): 6s storm that freezes & damages |
| ⚡ **Storm** | +25% speed, lightning immunity | 12%: lightning, +5 damage, stun | **Thunder Strike** (10s): lightning where you aim (40 blocks) | **Chain Lightning** (28s): arcs through up to 6 enemies |
| 💚 **Emerald** | Regeneration I; mobs drop double XP | 25%: Poison II | **Nature's Grasp** (14s): roots + damages enemies in 7 blocks | **Verdant Bloom** (40s): heal, Absorption II, cleanse (shared with scoreboard teammates) |
| ❤️ **Crimson** | +3 max hearts | Heal 15% of damage; 25%: bleed | **Blood Crescent** (9s): piercing blood slash | **Crimson Harvest** (30s): drain all nearby enemies |
| ☀️ **Solar** | Haste II; nearby enemies glow | Ignites; +50% vs undead | **Solar Beam** (9s): piercing 30-block beam | **Supernova** (35s): charge, then radius-10 solar blast |
| 🌑 **Shadow** | Invisible while sneaking, faster movement | +60% from behind; 20%: blind | **Shadow Strike** (10s): teleport behind your target | **Shadow Veil** (35s): 6s full invisibility (armor too); next hit +8 |
| ✨ **Cosmic** | Low gravity, higher jumps, no fall damage | 20%: Levitation | **Gravity Pulse** (12s): launch enemies up, slam them down | **Meteor Shower** (40s): 8 meteors on the target area |

Ability damage is credited to the player, so kills, mob aggro, world PvP settings and
protection plugins all work as normal. No ability breaks or places blocks.

## Install

1. Build it (below) or grab `OPSwords-<version>.jar`, put it in `plugins/`, start the server.
2. Players are offered the resource pack when they join. By default the plugin hosts it itself
   on **TCP port 8163**, so open that port (or change it in `config.yml`).
   - On a host that only gives you one port, set `resource-pack.mode: url` and upload
     `plugins/OPSwords/OPSwords-ResourcePack.zip` somewhere (or merge it into your own server pack).
   - Players can re-download with `/opswords pack`.
3. Get swords with `/opswords give <player> <sword|all>`, the `/opswords menu` GUI, or crafting.

### Crafting

Every sword has a shaped recipe around a netherite sword, set per sword in `config.yml`
(`crafting.enabled` turns all of them off). The defaults:

| Sword | Shape | Key |
| --- | --- | --- |
| Dragon | `BHB / NSN / BEB` | B dragon's breath, H dragon head, N netherite ingot, E end crystal |
| Void | `OEO / CSC / OEO` | O crying obsidian, E eye of ender, C end crystal |
| Inferno | `BFB / MSM / BNB` | B blaze rod, F fire charge, M magma block, N netherite ingot |
| Frost | `IPI / DSD / IPI` | I blue ice, P powder snow bucket, D diamond block |
| Storm | `LHL / CSC / LTL` | L lightning rod, H heart of the sea, C copper block, T trident |
| Emerald | `EGE / ESE / ETE` | E emerald block, G golden apple, T totem of undying |
| Crimson | `RGR / FSF / RNR` | R redstone block, G ghast tear, F crimson fungus, N netherite ingot |
| Solar | `GBG / LSL / GLG` | G gold block, B beacon, L glowstone |
| Shadow | `ECE / PSP / ECE` | E echo shard, C sculk catalyst, P phantom membrane |
| Cosmic | `APA / NSN / APA` | A amethyst block, P ender pearl, N nether star |

(`S` = netherite sword.) An OP sword can never be used up as an ingredient.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/opswords list` | — | All swords (hover for details) |
| `/opswords info [sword]` | — | What a sword does |
| `/opswords pack` | `opswords.pack` | Re-send the resource pack |
| `/opswords give <player> <sword\|all>` | `opswords.admin` | Give swords |
| `/opswords menu` | `opswords.admin` | Take swords from a GUI |
| `/opswords resetcooldowns [player\|*]` | `opswords.admin` | Clear cooldowns |
| `/opswords reload` | `opswords.admin` | Reload `config.yml` |

Aliases: `/opsword`, `/swords`.

Other permissions: `opswords.use` (abilities and passives, default everyone),
`opswords.craft` (default everyone), `opswords.bypass.cooldown` (default nobody).

## Config highlights

- Per sword: `enabled`, `attack-damage`, `attack-speed`, `cooldowns`, `recipe`.
- `settings.abilities-hit-players`, `ability-damage-multiplier`, `disabled-worlds`,
  `unbreakable`, `action-bar-hud`, `protect-dropped-swords` (dropped swords never despawn and glow).
- Changes apply on `/opswords reload`; swords players already own update the next time they're held.

## For developers

`dev.infuse.opswords.api.SwordAbilityEvent` fires before any ability; cancel it to block the
ability (no cooldown is started). Swords are identified by the `opswords:sword` persistent data key.

## Building

Requires Java 21.

```
./gradlew build
```

Output in `build/dist/`: the plugin jar and `OPSwords-ResourcePack.zip`.
The pack sources live in `resourcepack/` and are embedded into the jar at build time.
