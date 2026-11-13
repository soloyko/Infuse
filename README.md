# Infuse — OP Swords

A Paper **1.21.11** plugin that adds 15 legendary swords and 6 other legendary weapons (a
hammer, a scythe, a spear, a chakram, an axe and a staff). Each has its own name and lore, a 3D
model (bundled resource pack), a passive, an on-hit effect and two activated abilities.
Show them off on **weapon altars**: one-block 3D altars with the weapon floating above them.

![The fifteen swords](docs/swords.png)

![In hand: Wyrmsbane, Cinderwing, Gravecaller, Tidebreaker, Starfall, Deepwhisper, Elderbloom, Stormcaller](docs/in-hand.jpg)

![Abilities: Firestorm, Meteor Shower, Singularity, Thunder Strike, Solar Beam, Blizzard, Tidal Wave, Miasma, Stasis Field, Spectral Hounds, Sonic Boom, Shriek](docs/abilities.jpg)

| Control (weapon in main hand) | Does |
| --- | --- |
| Swap-hand key (**F**) | First ability |
| Sneak + **F** | Second ability |

Right-click keeps its normal use (the spear still charges, the mace still smashes). Rebound the swap-hand key? Tooltips and the action bar show
your own key. `controls.trigger` in `config.yml` switches to right-click (`right-click`) or
allows both (`both`).

While a weapon is held the action bar shows both abilities with a cooldown bar, a chime plays
when an ability is ready again, and other players see a faint themed aura around it. Kills get their own death
messages ("Steve was frozen solid by Alex wielding ❄ Everwinter").

## The swords

| Sword | Passive (while held) | On hit | F | Sneak + F |
| --- | --- | --- | --- | --- |
| 🐉 **Wyrmsbane** · Dragon | Resistance I, +50% knockback resistance | 25%: +4 bonus damage | **Dragon Breath** (12s): 8-pulse cone of dragonfire | **Wyrm Dive** (30s): launch up, crash down in a shockwave, no fall damage |
| 🔥 **Cinderwing** · Phoenix | Fire Resistance; **Rebirth**: a killing blow revives you at half health (every 5 min) | Ignites; 15%: combustion splash | **Ember Volley** (6s): 3 exploding fireballs (no block fire) | **Firestorm** (25s): expanding ring of fire |
| ❄ **Everwinter** · Frost | +4 armor, +2 toughness, freeze immunity | Slowness II + freeze | **Glacial Spike** (10s): ice projectile that impales | **Blizzard** (30s): 6s storm that freezes & damages |
| ⚡ **Stormcaller** · Storm | +25% speed, lightning immunity | 12%: lightning, +5 damage, stun | **Thunder Strike** (10s): lightning where you aim (40 blocks) | **Chain Lightning** (28s): arcs through up to 6 enemies |
| 🌊 **Tidebreaker** · Tide | Conduit Power, Dolphin's Grace | 20%: drag the target to you, slow it | **Riptide Surge** (8s): riptide dash through enemies | **Tidal Wave** (28s): a wave that carries enemies away |
| 🌿 **Elderbloom** · Verdant | Regeneration I; mobs drop double XP | 25%: Poison II | **Nature's Grasp** (14s): roots + damages enemies in 7 blocks | **Verdant Bloom** (40s): heal, Absorption II, cleanse (shared with scoreboard teammates) |
| ☣ **Pestilence** · Plague | Immune to poison, wither, hunger, nausea | Plague stack; infected targets wither for +2 | **Miasma** (12s): plague cloud that stacks infection | **Pandemic** (30s): every infected enemy erupts (3 + 2.5 per stack) |
| ❤ **Sanguine** · Crimson | +3 max hearts | Heal 15% of damage; 25%: bleed | **Blood Crescent** (9s): piercing blood slash | **Crimson Harvest** (30s): drain all nearby enemies |
| ☀ **Solaris** · Solar | Haste II; nearby enemies glow | Ignites; +50% vs undead | **Solar Beam** (9s): piercing 30-block beam | **Supernova** (35s): charge, then radius-10 solar blast |
| 🌌 **Voidreaver** · Void | Night Vision; carrying it saves you from the void | 20%: Wither II + steal 2 HP | **Void Step** (8s): blink up to 15 blocks | **Singularity** (35s): black hole that pulls enemies in, then implodes |
| ✨ **Starfall** · Cosmic | Low gravity, higher jumps, no fall damage | 20%: Levitation | **Gravity Pulse** (12s): launch enemies up, slam them down | **Meteor Shower** (40s): 8 meteors on the target area |
| ⌛ **Aeon** · Chrono | +25% attack speed | 15%: freeze the target in time | **Stasis Field** (14s): enemies crawl, projectiles freeze mid-air | **Rewind** (30s): back to where you were 4s ago, with that health |
| 🌑 **Umbra** · Shadow | Invisible while sneaking, faster movement | +60% from behind; 20%: blind | **Shadow Strike** (10s): teleport behind your target | **Shadow Veil** (35s): 6s full invisibility (armor too); next hit +8 |
| 💀 **Gravecaller** · Soul | Kills heal 4 and grant Absorption | 20%: +4 soul damage | **Spectral Hounds** (25s): 3 soul wolves hunt your target for 15s | **Wail of the Damned** (32s): wither, weaken and scatter enemies |
| 🔊 **Deepwhisper** · Echo | Darkness immunity; sculk sensors ignore you | 20%: Darkness / slow | **Sonic Boom** (12s): charged warden blast through a line of enemies | **Shriek** (30s): darken and reveal all enemies, quicken yourself |

## The other weapons

![Earthshaker, Requiem, Skypiercer, Zephyr, Ragnarök and Arcanum on their altars](docs/weapons.png)

They're built on the matching vanilla item, so the hammer keeps the mace's smash attack, the
spear keeps its charge and the axe still disables shields.

| Weapon | Passive (while held) | On hit | F | Sneak + F |
| --- | --- | --- | --- | --- |
| ⚒ **Earthshaker** · Titan Hammer | No knockback, +3 armor | 20%: **Quake** stuns everything around the target | **Seismic Slam** (12s): a ring of erupting earth launches everything around you | **Earthen Bulwark** (35s): stone pillars orbit you; Resistance II, attackers are hurt and thrown back |
| ⚰ **Requiem** · Reaper's Scythe | Kills refresh Reap and grant speed | +50% vs targets below 35% health | **Reap** (8s): 220° sweeping arc, heals you per enemy hit | **Final Harvest** (30s): a giant spectral scythe falls on the target, harder the more health it's missing |
| ♆ **Skypiercer** · Celestial Spear | +1.5 blocks reach | 20%: **Impale** pins the target, +3 damage | **Javelin** (8s): a spectral spear pierces a line of enemies and comes back | **Phalanx** (25s): spears burst from the ground in a line, launching enemies |
| ☸ **Zephyr** · Wind Chakram | Double jump, no fall damage after it | 20%: **Gust** blasts the target away | **Halo Throw** (5s): the chakram cuts through enemies out and back | **Cyclone** (28s): three chakrams orbit you for 6s, shredding enemies and deflecting arrows |
| ᛉ **Ragnarök** · Berserker Axe | Up to +50% damage the lower your health | **Cleave**: 40% to enemies beside the target | **Whirlwind** (14s): spin for 2.5s, carving through everything around you | **Warcry** (40s): Strength II and Resistance for you, Weakness and Slowness for enemies |
| ⚚ **Arcanum** · Arcane Staff | A 2-heart ward that recharges 12s after it breaks | 25%: **Arcane Spark** leaps to another enemy | **Arcane Missiles** (6s): five homing amethyst missiles | **Crystal Nova** (25s): rings of amethyst burst from the ground around you |

Ability damage is credited to the player, so kills, mob aggro, world PvP settings and
protection plugins all work as normal. No ability breaks or places blocks: the
flying weapons, erupting earth, pillars and crystals are all display entities that are never
saved with the world.

<details>
<summary><b>Lore</b></summary>

- **Wyrmsbane** — Forged to slay the last elder dragon, it drank the beast's soul instead. Its rage still burns in the steel.
- **Cinderwing** — A feather of the first phoenix, hammered into a blade. Those who carry it do not stay dead for long.
- **Everwinter** — Carved from ice that has not melted since the world was young. Its touch slows even the heart.
- **Stormcaller** — Struck by a thousand bolts atop the highest peak, until the storm itself agreed to obey.
- **Tidebreaker** — Lost with a drowned kingdom and returned by the sea. It still remembers the weight of the deep.
- **Elderbloom** — Grown, not forged, from the heartwood of the world's oldest tree. Life follows wherever it goes.
- **Pestilence** — Quenched in the blight that emptied a hundred villages. Every scratch it leaves festers.
- **Sanguine** — A duelist's needle with a thirst of its own. It returns every drop it draws to its master.
- **Solaris** — Blessed at dawn by a forgotten order of sun-priests. The undead burn at the very sight of it.
- **Voidreaver** — Pulled from the empty space between the stars. Light bends around its edge and never comes back.
- **Starfall** — Forged in the heart of a falling star. Gravity itself kneels to whoever holds it.
- **Aeon** — Its maker spent a thousand years forging it, then went back in time and did it in an afternoon.
- **Umbra** — The blade of a watcher who was never seen. It is darkest exactly where you aren't looking.
- **Gravecaller** — A lich's blade, still humming with the souls it collected. Some of them are eager to help.
- **Deepwhisper** — Grown in the sculk beneath the Deep Dark. It hears your heartbeat before you even swing.
- **Earthshaker** — Forged in a dead volcano by giants who wanted mountains to kneel. The ground still remembers its weight.
- **Requiem** — The ninth abyss sings through its blade. Every soul it reaps adds another voice to the choir.
- **Skypiercer** — Hurled down from the heavens to end a war between gods. It has been looking for a worthy arm ever since.
- **Zephyr** — A royal war-disc blessed by the four winds. Thrown true, it always finds its way home.
- **Ragnarök** — The axe that will end the world, carved with runes of doom. It grows hungrier the closer you are to death.
- **Arcanum** — Grown from a single amethyst that hummed with magic for ten thousand years. It still hums.

</details>

## Weapon altars

![Two weapon altars: an owned altar with Wyrmsbane and a claimable shrine with Everwinter](docs/altars.png)

A weapon altar is a single block with its own 3D model: a deepslate and gold plinth with rune
plates, a glowing sigil and four crystals that take on the colour of the weapon it holds. The
weapon floats above it (swords point-down, the other weapons head-up), slowly turning and bobbing, with a nameplate and the
weapon's aura.

- **Craft** one (`/opswords recipes` shows the recipe) and place it like a block.
- **Right-click with an OP weapon** to put it on the altar. Right-click with an empty hand to
  take it back. **Sneak + right-click** with an empty hand picks the altar up.
- Only the owner (or an admin) can use or break an altar; breaking it drops the weapon too.
- **Shrines**: an admin looks at a block and runs `/opswords altar shrine <weapon>`. The first
  player to right-click the shrine claims the weapon (announced in chat; counts as forging it in
  legendary mode). `/opswords altar refill <weapon>` puts a new one on the shrine.

Altars are saved in `plugins/OPSwords/altars.yml`. `altars.enabled` turns the feature off.

## Install

1. Build it (below) or grab `OPSwords-<version>.jar`, put it in `plugins/`, start the server.
2. Players are offered the resource pack when they join. By default the plugin hosts it itself
   on **TCP port 8163**, so open that port (or change it in `config.yml`).
   - On a host that only gives you one port, set `resource-pack.mode: url` and upload
     `plugins/OPSwords/OPSwords-ResourcePack.zip` somewhere (or merge it into your own server pack).
   - Players can re-download with `/opswords pack`.
3. Get weapons with `/opswords give <player> <weapon|all>` (id or name, e.g. `dragon` or
   `wyrmsbane`), the `/opswords menu` GUI, crafting or a shrine.

Upgrading: new weapons and settings are added to your existing `config.yml`
automatically, and old Inferno/Emerald swords turn into Cinderwing/Elderbloom when next held.

<img src="docs/inventory.png" alt="Swords in the inventory" width="400">

### Crafting

Every weapon has a shaped recipe around its vanilla base item, set per sword in `config.yml`
(`crafting.enabled` turns all of them off). Players can browse them in-game with
`/opswords recipes`, and crafted swords are signed "Forged by <player>". The defaults:

| Sword | Shape | Key |
| --- | --- | --- |
| Wyrmsbane | `BHB / NSN / BEB` | B dragon's breath, H dragon head, N netherite ingot, E end crystal |
| Cinderwing | `BFB / TST / BMB` | B blaze rod, F fire charge, T totem of undying, M magma block |
| Everwinter | `IPI / DSD / IPI` | I blue ice, P powder snow bucket, D diamond block |
| Stormcaller | `LBL / CSC / LBL` | L lightning rod, B breeze rod, C copper block |
| Tidebreaker | `NHN / PSP / NTN` | N nautilus shell, H heart of the sea, P prismarine crystals, T trident |
| Elderbloom | `EGE / MSM / EGE` | E emerald block, G golden apple, M moss block |
| Pestilence | `WFW / PSP / WFW` | W wither rose, F fermented spider eye, P poisonous potato |
| Sanguine | `RGR / FSF / RNR` | R redstone block, G ghast tear, F crimson fungus, N netherite ingot |
| Solaris | `GBG / LSL / GLG` | G gold block, B beacon, L glowstone |
| Voidreaver | `OEO / CSC / OEO` | O crying obsidian, E eye of ender, C end crystal |
| Starfall | `APA / NSN / APA` | A amethyst block, P ender pearl, N nether star |
| Aeon | `CRC / DSD / CRC` | C clock, R recovery compass, D diamond block |
| Umbra | `PNP / OSO / PNP` | P phantom membrane, N netherite scrap, O obsidian |
| Gravecaller | `LKL / BSB / LKL` | L soul lantern, K wither skeleton skull, B bone block |
| Deepwhisper | `ECE / HSH / ECE` | E echo shard, C sculk catalyst, H sculk shrieker |
| Earthshaker | `NAN / OSO / NAN` | N netherite ingot, A anvil, O obsidian, **S mace** |
| Requiem | `PNP / RSR / PNP` | P phantom membrane, N nether star, R wither rose |
| Skypiercer | `BGB / ESE / BGB` | B breeze rod, G gold block, E end rod, **S netherite spear** |
| Zephyr | `WEW / BSB / WEW` | W wind charge, E emerald block, B breeze rod |
| Ragnarök | `INI / GSG / INI` | I iron block, N netherite ingot, G goat horn, **S netherite axe** |
| Arcanum | `CLC / BSB / CLC` | C amethyst cluster, L lapis block, B bottle o' enchanting |
| Weapon Altar | `AGA / BLB / DDD` | A amethyst shard, G gold block, B polished blackstone bricks, L lodestone, D polished deepslate |

(`S` = netherite sword unless noted.) An OP sword can never be used up as an ingredient.

**Legendary mode** (`crafting.unique-swords: true`): each sword can only be crafted once per
server. `/opswords list` and the recipe book show who forged what; `/opswords unforge <sword>`
lets a lost sword be crafted again. Admins can always `/opswords give`.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/opswords list` | — | All weapons (hover for details, click for lore) |
| `/opswords info [weapon]` | — | Lore and abilities of a weapon |
| `/opswords recipes [weapon]` | — | Recipe book GUI (weapons and the altar) |
| `/opswords pack` | `opswords.pack` | Re-send the resource pack |
| `/opswords give <player> <weapon\|all>` | `opswords.admin` | Give weapons |
| `/opswords menu` | `opswords.admin` | Take weapons and altars from a GUI |
| `/opswords altar give [player] [amount]` | `opswords.admin` | Give weapon altars |
| `/opswords altar shrine <weapon>` | `opswords.admin` | Turn the block you look at into a claimable shrine |
| `/opswords altar refill <weapon>` | `opswords.admin` | Put a weapon back on the shrine you look at |
| `/opswords altar remove` / `list` | `opswords.admin` | Remove the altar you look at / list all altars |
| `/opswords resetcooldowns [player\|*]` | `opswords.admin` | Clear cooldowns |
| `/opswords unforge <weapon>` | `opswords.admin` | Legendary mode: allow crafting a weapon again |
| `/opswords reload` | `opswords.admin` | Reload `config.yml` |

Aliases: `/opsword`, `/swords`.

Other permissions: `opswords.use` (abilities and passives, default everyone),
`opswords.craft` (default everyone), `opswords.bypass.cooldown` (default nobody).

## Config highlights

- Per weapon: `enabled`, `attack-damage`, `attack-speed`, `cooldowns`, `recipe`.
- `settings.abilities-hit-players`, `ability-damage-multiplier`,
  `ability-damage-multiplier-vs-players` (tone abilities down for PvP only), `disabled-worlds`,
  `unbreakable`, `protect-dropped-swords` (dropped swords never despawn and glow).
- Cosmetics: `action-bar-hud`, `ability-ready-sound`, `held-aura`, `death-messages`.
- `controls.trigger` (`offhand`, `right-click` or `both`), `crafting.unique-swords`,
  `altars.enabled` and `altars.recipe`.
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
The pack in `resourcepack/` is embedded into the jar at build time. Every push is also built by
GitHub Actions; the jar and pack are attached to each run as the `OPSwords` artifact.

### The resource pack

The 3D models and textures come from the **Blades of Majestica** resource pack. 15 of its 119
weapons (15 swords and the 6 other weapons) are imported into the `op_swords` namespace, so they're applied by the plugin with the
`item_model` component instead of the original pack's rename-in-an-anvil trick.

```
pip install pillow numpy
python3 tools/import_majestica.py path/to/Blades_Of_Majestica.zip
```

The altar model is original: `tools/altar_model.py` generates it from vanilla textures plus
three small rune/sigil/crystal textures.

To use a different model for a weapon, change its entry in `SWORDS` at the top of
`tools/import_majestica.py`, re-run it and rebuild. `tools/modelpreview.py` renders models to a
PNG for a quick look without starting the game.

Blades of Majestica is a third-party pack; check its license before distributing these assets
publicly.
