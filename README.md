# Customs

A Paper **1.21.11** plugin that adds 21 legendary swords and 19 other legendary weapons (a
hammer, a scythe, a spear, a chakram, an axe, a staff, a trident, throwing stars, a halberd, a
warglaive, a cyber sawblade, a rapier, a khopesh, spectral claws, cursed daggers, a holy mace, a
glaive, a power fuse hammer and a cyber mantis blade), each named after its model in the Blades of
Majestica pack. Each has its own name and lore, a 3D model
(bundled resource pack), a passive, an on-hit effect and two activated abilities, drawn with
glowing 3D effects: jagged lightning, ground that cracks and glows, pillars of fire, ice and
stone spikes, rifts, spectral heads, crescent slashes and more. Admins can place **stone
altars**: one-block 3D forges where players craft a weapon by bringing its ingredients, no
crafting grid needed.

Formerly called OP Swords: upgrading keeps your config, altars and every weapon players own
(see *Install*).

![The first fifteen swords](docs/swords.png)

![In hand: Dragon Slaying Blade, Phoenix Grace, Awakened Lichblade, Aquatic Sacred Blade, Edge Of The Astral Plane, Sculk Sword, Enigma, Thunder Bringer](docs/in-hand.jpg)

![Twelve abilities in action, including Meteor Shower, Singularity, Thunder Strike, Solar Beam, Blizzard, Miasma, Stasis Field, Spectral Hounds, Sonic Boom and Shriek](docs/abilities.jpg)

| Control (weapon in main hand) | Does |
| --- | --- |
| Swap-hand key (**F**) | First ability |
| Sneak + **F** | Second ability |

Right-click keeps its normal use (the spear still charges, the mace still smashes). Rebound the swap-hand key? Tooltips and the action bar show
your own key. `controls.trigger` in `config.yml` switches to right-click (`right-click`) or
allows both (`both`).

While a weapon is held the action bar shows both abilities with a cooldown bar, a chime plays
when an ability is ready again, and other players see a faint themed aura around it. Kills get their own death
messages ("Steve was frozen solid by Alex wielding ❄ Frost Blade").

## The swords

| Sword | Passive (while held) | On hit | F | Sneak + F |
| --- | --- | --- | --- | --- |
| 🐉 **Dragon Slaying Blade** · Dragon | Resistance I, +50% knockback resistance | 25%: +4 bonus damage | **Dragon Breath** (12s): 8-pulse cone of dragonfire | **Wyrm Dive** (30s): launch up, crash down in a shockwave, no fall damage |
| 🔥 **Phoenix Grace** · Phoenix | Fire Resistance; **Rebirth**: a killing blow revives you at half health (every 5 min) | Ignites; 15%: combustion splash | **Ember Volley** (6s): 3 exploding fireballs that leave burning ground behind them (no real fire) | **Wall of Fire** (25s): a wall of fire across your aim for 6s; anything crossing it burns and is thrown back, arrows burn up |
| ❄ **Frost Blade** · Frost | +4 armor, +2 toughness, freeze immunity | Slowness II + freeze | **Glacial Spike** (10s): ice projectile that impales | **Blizzard** (30s): 6s storm that freezes & damages |
| ⚡ **Thunder Bringer** · Storm | +25% speed, lightning immunity | 12%: lightning, +5 damage, stun | **Thunder Strike** (10s): lightning where you aim (40 blocks) | **Chain Lightning** (28s): arcs through up to 6 enemies |
| 🌊 **Aquatic Sacred Blade** · Tide | Conduit Power, Dolphin's Grace | 20%: drag the target to you, slow it | **Riptide Surge** (8s): riptide dash through enemies | **Ride the Tide** (28s): surf a wave you steer with your crosshair for 3.5s, carrying enemies until it breaks |
| 🌿 **Enigma** · Verdant | Regeneration I; mobs drop double XP | 25%: Poison II | **Nature's Grasp** (14s): roots + damages enemies in 7 blocks | **Verdant Bloom** (40s): heal, Absorption II, cleanse (shared with scoreboard teammates) |
| ☣ **Plague Longsword** · Plague | Immune to poison, wither, hunger, nausea | Plague stack; infected targets wither for +2 | **Miasma** (12s): plague cloud that stacks infection | **Pandemic** (30s): every infected enemy erupts (3 + 2.5 per stack) |
| ❤ **Vampiric Needle** · Crimson | +3 max hearts | Heal 15% of damage; 25%: bleed | **Blood Crescent** (9s): piercing blood slash | **Blood Bond** (30s): chain up to four enemies to you for 6s, drain them every second, tear them when the chains snap |
| ☀ **Righteous Relic** · Solar | Haste II; nearby enemies glow | Ignites; +50% vs undead | **Solar Beam** (9s): a 1.5s beam that follows your aim, burning what it sweeps across | **Supernova** (35s): charge, then radius-10 solar blast |
| 🌌 **Demon Lord's Sword** · Void | Night Vision; carrying it saves you from the void | 20%: Wither II + steal 2 HP | **Void Step** (8s): blink up to 15 blocks | **Singularity** (35s): black hole that pulls enemies in, then implodes |
| ✨ **Edge Of The Astral Plane** · Cosmic | Low gravity, higher jumps, no fall damage | 20%: Levitation | **Telekinesis** (12s): seize the enemy under your crosshair, drag it with your aim into others and walls, then hurl it | **Meteor Shower** (40s): 8 meteors on the target area |
| ⌛ **Chrono Blade** · Chrono | +25% attack speed | 15%: freeze the target in time | **Stasis Field** (14s): enemies crawl, projectiles freeze mid-air | **Rewind** (30s): back to where you were 4s ago, with that health |
| 🌑 **Watcher Claymore** · Shadow | Invisible while sneaking, faster movement | +60% from behind; 20%: blind | **Shadow Strike** (10s): teleport behind your target | **Shadow Veil** (35s): 6s full invisibility (armor too); next hit +8 |
| 💀 **Awakened Lichblade** · Soul | Kills heal 4 and grant Absorption | 20%: +4 soul damage | **Spectral Hounds** (25s): 3 soul wolves hunt your target for 15s | **Wail of the Damned** (32s): wither, weaken and scatter enemies, then six screaming skulls hunt them down |
| 🔊 **Sculk Sword** · Echo | Darkness immunity; sculk sensors ignore you | 20%: Darkness / slow | **Sonic Boom** (12s): charged warden blast through a line of enemies | **Shriek** (30s): darken and reveal all enemies, quicken yourself |
| 刀 **Katana** · Ronin's Katana | **Iaido**: the first hit after 3s without attacking deals +60% | 25%: bleed | **Draw Cut** (8s): flash 10 blocks forward; a moment later everyone you passed is cut at once | **Moonlit Duel** (35s): trap the enemy under your crosshair in a ring of moonlight with you for 8s; +40% damage to it |
| ✢ **Hornet's Needle** · Hive Needle | Jump Boost I; sneak in mid-air to drift down | **Venom** stacks (up to 6) | **Swarm Form** (14s): become a swarm of hornets for 3s, fly where you look, untouchable, stinging what you pass | **Hive Mind** (30s): enemies carrying your venom turn on each other for 6s |
| ✵ **Waxweaver** · Waxen Blade | Night Vision, Fire Resistance | 30%: hot wax, Slowness up to III | **Wickfire** (10s): a lit fuse races along the ground after the enemy you aim at and explodes when it catches up | **Melt** (30s): wax every enemy within 8 blocks; your next hit on one sets them all alight |
| ♨ **Brimstone Claymore** · Volcanic Claymore | Fire Resistance; lava heals you | 25%: molten splash sets nearby enemies alight | **Geyser Leap** (14s): a lava geyser hurls you into the sky and leaves a burning pool | **Obsidian Skin** (35s): 6s of obsidian armor, 60% less damage, no knockback, then it shatters into shards |
| ◈ **Silverine Blade** · Silver Mirror Blade | **Glass Skin**: 15% of attacks miss you | 20%: shards cut enemies around the target | **Mirror Images** (25s): two copies of you that mimic your moves and draw enemies away, then burst into glass | **Reflection** (30s): for 3s every hit you take goes back to whoever dealt it |
| ⚓ **Sabre** · Corsair's Cutlass | Dolphin's Grace; kills can drop gold | 20%: a backhand cut for half damage | **Powder Keg** (12s): roll a keg out where you aim; swing at it to blow it up | **Kraken's Grasp** (30s): tentacles seize up to three enemies, slam them down twice and fling them away |

## The other weapons

![The first 13 other weapons on their altars](docs/weapons.png)

<img src="docs/weapons-menu.png" alt="The Other Weapons tab of the armory menu" width="420">

They're built on the matching vanilla item, so the hammer keeps the mace's smash attack, the
spear and halberd keep their charge, the axe still disables shields and the trident can still be
thrown.

| Weapon | Passive (while held) | On hit | F | Sneak + F |
| --- | --- | --- | --- | --- |
| ⚒ **Hearthflame** · Titan Hammer | No knockback, +3 armor | 20%: **Quake** stuns everything around the target | **Seismic Slam** (12s): a ring of erupting earth launches everything around you | **Earthen Bulwark** (35s): stone pillars orbit you; Resistance II, attackers are hurt and thrown back |
| ⚰ **Requiem of the Ninth Abyss** · Reaper's Scythe | Kills refresh Reap and grant speed | +50% vs targets below 35% health | **Reap** (8s): 220° sweeping arc, heals you per enemy hit | **Final Harvest** (30s): a giant spectral scythe falls on the target, harder the more health it's missing |
| ♆ **Sunbreak** · Celestial Spear | +1.5 blocks reach | 20%: **Impale** pins the target, +3 damage | **Skyhook** (8s): a spear on a chain that drags enemies to you or hauls you to the wall it sticks in | **Phalanx** (25s): fans of spears burst from the ground in a line, ending in a giant spear |
| ☸ **Royal Chakram** · Wind Chakram | Double jump, no fall damage after it | 20%: **Gust** blasts the target away | **Halo Throw** (5s): the chakram cuts through enemies out and back | **Cyclone** (28s): three chakrams orbit you for 6s, shredding enemies and deflecting arrows |
| ᛉ **Demon Lord's Great Axe** · Berserker Axe | Up to +50% damage the lower your health | **Cleave**: 40% to enemies beside the target | **Whirlwind** (14s): spin for 2.5s, carving through everything around you | **Warcry** (40s): a giant spectral axe crashes down ahead; Strength II and Resistance for you, Weakness and Slowness for enemies |
| ⚚ **Arcanethyst** · Arcane Staff | A 2-heart ward that recharges 12s after it breaks | 25%: **Arcane Spark** leaps to another enemy | **Arcane Missiles** (6s): five homing amethyst missiles | **Crystal Sentinel** (25s): a floating crystal that shoots enemies and mends you and your teammates for 8s |
| ✴ **Amethyst Shuriken** · Throwing Star | Speed I, Jump Boost I | 25%: **Petal Cut** flings a spare star at another enemy | **Star Fan** (6s): five spinning shuriken that each ricochet once | **Thousand Cuts** (26s): flash between up to six enemies, cutting each, then return |
| ⚜ **Jade Halberd** · Imperial Halberd | +1 reach, +4 armor | 25%: **Jade Cleave** sweeps everything in front for half damage | **Dragon Sweep** (9s): a full circle of the halberd that hurls everyone back | **Jade Dragon** (32s): a jade dragon you steer with your crosshair for 4.5s, biting everything it passes through |
| ☾ **Watching Warglaive** · Lunar Warglaive | Night Vision; +25% damage at night or in the dark | 20%: **Crescent Echo** hits the target again | **Lunar Dash** (8s): dash through enemies, leaving silver crescents | **Moonfall** (35s): a giant crescent moon falls on the target area and shatters |
| ⚙ **Cybernetic Chainsaw Blade** · Cyber Sawblade | **Overclock**: quick hits build heat; at 5 heat the next hit vents for +6 | 25%: **Arc Spark** jumps to two more enemies | **Grinder** (10s): two seconds of a spinning blade shredding what's in front | **EMP Overload** (30s): a pulse that stuns and reveals enemies and speeds you up |
| ♛ **Royal Rapier** · Duelist's Rapier | +25% attack speed, +10% speed | **Precision**: +50% against enemies at full health | **Flèche** (6s): lunge and run the first enemy through | **Riposte** (20s): a parry stance; the next attack is blocked and answered with five thrusts |
| ☥ **Pharaoh's Treasure** · Pharaoh's Khopesh | Regeneration I in daylight | 20%: **Pharaoh's Curse**: Weakness, Slowness, +20% damage from you | **Sandstorm** (12s): a sand tornado that drifts wherever your crosshair points, lifting, blinding and grinding enemies | **Tomb of Sands** (30s): seal nearby enemies in sandstone tombs, then collapse them |
| Ψ **Ocean's Rage** · Abyssal Trident | Always returns when thrown (Loyalty) and calls lightning where it hits, in any weather; Water Breathing | 20%: **Undertow** drags the target under, +3 damage, Slowness III | **Trident Rain** (10s): five spectral tridents fall on the target area | **Maelstrom** (30s): a whirlpool drags enemies in for 4s, then erupts as a geyser |
| Ж **Soul Claws** · Spectral Claws | Speed I; wounded enemies near you glow | **Frenzy**: up to 5 stacks of Haste and +1 damage | **Pounce** (8s): leap onto the enemy you aim at, pin it and rake it three times | **The Hunt** (30s): mark your prey for 12s: a trail to it, speed towards it, +5 damage; kill it to heal fully |
| † **Thousand Demon Daggers** · Cursed Daggers | **Twin Fangs**: every third hit strikes twice | **Hex**: +6% damage from you per stack | **Blood Pact** (20s): pay 3 hearts for 6s of +5 damage and lifesteal | **Soul Swap** (14s): trade places with the enemy you aim at; it's left weakened and blinded |
| ✠ **Divine Punisher** · Holy Mace | +50% vs undead, 20% less damage from undead | 20%: **Smite**, a pillar of light for +4 | **Sanctuary** (30s): a dome of light for 6s that keeps enemies and projectiles out and heals allies inside | **Martyr's Vow** (40s): 8s where you can't drop below half a heart, then heal half of what you took |
| ❦ **Forest Guardian's Glaive** · Guardian's Glaive | Regeneration I on grass, moss or leaves | 20%: a thorn wisp bites for 4s | **Leaf Glide** (12s): glide on wings of leaves for 4s, steering with your crosshair; dive into enemies to strike | **Turning Seasons** (35s): spring heals, summer burns, autumn blinds, winter freezes around you |
| Ω **Power Fuse Hammer** · Fusion Maul | **Polarity**: hits magnetize the target | 25%: **Static** arcs to every magnetized enemy | **Repulsor Field** (25s): 5s field that throws enemies back and turns projectiles around | **Overcharge** (20s): charge for 2s; your next hit deals +14 and chains to magnetized enemies |
| ⌬ **Cyber Mantis Blade** · Cyber Arm-Blade | Jump Boost II; falls under 12 blocks don't hurt | 25%: **Serrated**, +30% damage from you for 4s | **Blade Extension** (20s): 6s of +3 reach, every swing cuts everything in front | **Ambush** (25s): wait unseen; the first enemy within 6 blocks is snatched and struck four times |

## Effects

![Thunder Strike, Blizzard, Warcry and an earlier fire ability](docs/effects.jpg)

Every hit with a Customs weapon draws a glowing effect in its colour, shaped by the kind of
weapon: swords slash, scythes and axes cut a wide arc, spears and the rapier thrust a line of
light, the hammer lands an impact ring and throwing weapons make quick spinning cuts; critical
hits cut twice. Using an ability shows the weapon's element: flames, frost, sparks, splashes,
leaves, runes, shadow, blood or a sweep of wind. Kills end in a burst of light with the victim's essence rising
out of it, plus a finish that fits the weapon (ice shattering, a sunbeam, a sand burst, souls
scattering, a lightning bolt...). The abilities themselves use
3D effects drawn with display entities and a set of effect models in the resource pack, and
each has its own signature: Thunder Strike is a forked bolt that scorches the ground, Wall of
Fire raises a burning wall across your aim, Blizzard opens with a spiral of ice spikes, Dragon
Breath pours from a spectral dragon head, Seismic Slam cracks the earth and raises stone spikes,
Telekinesis lets you drag an enemy around with your crosshair, the Wail sends screaming skulls spiralling out,
drains snake to you as tendrils, Void Step and Shadow Strike open rifts, Riposte raises a
shield sigil, Arcane Missiles pour out of a rune portal, dashes leave afterimages, Stasis Field turns the
hands of a giant clock, Singularity spins an accretion disk, Supernova swells into a sun,
Warcry brings a giant spectral axe down on the ground, Moonfall drops a crescent moon, Jade
Dragon is a winding serpent with a dragon's head, Phalanx raises fans of spears that end in a
giant one, and thrown weapons leave streaks and rings of light behind them. They fade out smoothly, never touch the world, and a
glow or burst on yourself is hidden from you so it never fills your screen (others still see it).

Every hit also shows a **damage number** above the target: gold with a ✸ for critical swings,
the weapon's colour for abilities. Fast hits on the same target (beams, flurries, a fan of
shuriken) add up on one number with a hit count instead of piling up. Turn them off with
`settings.damage-numbers` or in the settings menu.

Ability damage is credited to the player, so kills, mob aggro, world PvP settings and
protection plugins all work as normal. No ability breaks or places blocks: the
flying weapons, erupting earth, pillars and crystals are all display entities that are never
saved with the world.

<details>
<summary><b>Lore</b></summary>

- **Dragon Slaying Blade** — Forged to slay the last elder dragon, it drank the beast's soul instead. When you swing it, something old and furious breathes with you.
- **Phoenix Grace** — A feather of the first phoenix, beaten into a blade over a fire that never went out. Whoever carries it rises from their own ashes.
- **Frost Blade** — Carved from ice that has not melted since the world was young. The wounds it leaves never quite stop freezing.
- **Thunder Bringer** — It hung on the highest peak for a thousand years, struck by every storm that passed. In the end, the storms began to answer it.
- **Aquatic Sacred Blade** — Lost with a drowned kingdom and given back by the sea. It still carries the weight of the deep, and the deep still follows it.
- **Enigma** — Grown, not forged, from the heartwood of the world's oldest tree. Wherever it fights, roots break through the ground.
- **Plague Longsword** — Quenched in the blight that emptied a hundred villages. Every scratch it leaves festers, and every festering wound spreads.
- **Vampiric Needle** — A duelist's needle that learned to drink. It gives every drop it draws back to the hand that holds it.
- **Righteous Relic** — Blessed at dawn by the last of the sun-priests and kept in light ever since. The dead burn at the mere sight of it.
- **Demon Lord's Sword** — Pulled from the empty space between the stars. Light bends around its edge and never comes back out.
- **Edge Of The Astral Plane** — Hammered from the core of a fallen star, still warm after ten thousand years. Gravity itself kneels to whoever holds it.
- **Chrono Blade** — Its smith worked on it for a thousand years, then went back and finished it in an afternoon. Time moves however its wielder prefers.
- **Watcher Claymore** — The blade of a watcher who was never once seen. It is darkest exactly where you are not looking.
- **Awakened Lichblade** — A lich's blade, still humming with every soul it ever took. Some of them are eager to hunt for you.
- **Sculk Sword** — Grown in the sculk beneath the Deep Dark, where silence is a weapon. It hears your heartbeat before you even swing.
- **Hearthflame** — Forged in a dead volcano by giants who wanted mountains to kneel. Strike the ground with it, and the ground remembers.
- **Requiem of the Ninth Abyss** — The ninth abyss sings through its curved blade. Every soul it reaps adds another voice to the choir.
- **Sunbreak** — Hurled from the heavens to end a war between gods, it fell for an age. It has been searching for a worthy arm ever since.
- **Royal Chakram** — A royal war-disc blessed by all four winds. Thrown true, it always finds its way home.
- **Demon Lord's Great Axe** — The axe that will end the world, carved with runes of doom. It grows hungrier the closer its wielder comes to death.
- **Arcanethyst** — Grown from a single amethyst that hummed with magic for ten thousand years. It still hums, and now it answers.
- **Ocean's Rage** — Raised from the trench where the old sea god sleeps. Wherever it is thrown, the storm follows.
- **Amethyst Shuriken** — Four petals of living amethyst, folded by a clan that was never seen. It blooms only in the dark, and never twice in the same place.
- **Jade Halberd** — Carried by the emperor's last guard, who held the gate alone for a hundred days. The jade remembers every enemy that fell before it.
- **Watching Warglaive** — Forged on the one night the moon swallowed the sun. It has been hungry for light ever since.
- **Cybernetic Chainsaw Blade** — A prototype from a future that never happened. Nobody remembers who built it, but everyone remembers the noise.
- **Royal Rapier** — The queen's own blade, which never lost a duel. It asks only that you strike first, and strike true.
- **Pharaoh's Treasure** — Buried with a pharaoh who refused to stay buried. When it is raised, the desert rises with it.
- **Katana** — A masterless blade that waits in its scabbard like held breath. It is drawn once, and once is enough.
- **Hornet's Needle** — Carved from the queen's own stinger. Wherever it goes, the hive hums along behind it.
- **Waxweaver** — Poured from the candles of a chapel that kept watch for a hundred years. Its flame never gutters.
- **Brimstone Claymore** — Hewn from the heart of a mountain that would not stop burning. It is heavy, and it is angry.
- **Silverine Blade** — Polished until it forgot which side of itself was real. Neither do the people who fight it.
- **Sabre** — Taken from a captain who swore the sea itself owed him a debt. The sea is still paying.
- **Soul Claws** — Torn from the ghost of a beast that hunted the same forest for a thousand years. It is still hungry.
- **Thousand Demon Daggers** — Twin blades forged in a pact neither smith survived. They always want a little more of you.
- **Divine Punisher** — Blessed on the steps of a cathedral and never once unblessed. The dead fear it; the living should.
- **Forest Guardian's Glaive** — Carried by the old forest's last guardian, who planted it in the earth and became a tree around it.
- **Power Fuse Hammer** — A forge-engine with a handle. The fuse in its head has been lit since the day it was built.
- **Cyber Mantis Blade** — Military hardware with an insect's patience. It folds into your forearm and unfolds into someone else.

</details>

## Stone altars

![Four stone altars, their rune windows glowing in the colour of each weapon](docs/altars.png)

A weapon altar is a single block with its own 3D model, built from real stone textures: a
polished deepslate base on deepslate tiles, a smooth stone pillar wrapped in raised deepslate
trim that flares out under a heavy capital, and a calcite top. On each side a carved window
holds a rune that glows in the colour of the weapon on the altar. The weapon
floats above it (swords point-down, the other weapons head-up), with **its recipe circling above
it** and a nameplate.

- **Only admins place altars.** Get one with `/customs altar give` or the `/customs menu`; it
  can't be crafted. Place it, then right-click it with an Customs weapon to choose what it forges (or
  look at it and run `/customs altar set <weapon>`). Sneak + right-click with an empty hand
  picks it back up; only admins can break it.
- **Players forge at it**: right-click the altar with the ingredients in your inventory. They're
  taken and the weapon goes straight into your inventory. Looking at an altar shows what you're
  still missing in the action bar, and right-clicking without everything lists it in chat.
- Forging at an altar counts like crafting: it's signed "Forged by", announced, and respects
  legendary mode (the altar then shows who forged it).

Set `crafting.crafting-table: false` to make altars the only way to get weapons. Altars are saved
in `plugins/Customs/altars.yml`; `altars.enabled` turns them off. (Upgrading from 1.4: weapons
players had on their own altars are given back to them, and those altars keep forging that weapon.)

## Install

1. Build it (below) or grab `Customs-<version>.jar`, put it in `plugins/`, start the server.
2. Players are offered the resource pack when they join. By default the plugin hosts it itself
   on **TCP port 8163**, so open that port (or change it in `config.yml`).
   - On a host that only gives you one port, set `resource-pack.mode: url` and upload
     `plugins/Customs/Customs-ResourcePack.zip` somewhere (or merge it into your own server pack).
   - Players can re-download with `/customs pack`.
3. Get weapons with `/customs give <player> <weapon|all>` (id or name, e.g. `dragon` or
   `wyrmsbane`), the `/customs menu` GUI, crafting or a weapon altar.

Upgrading: new weapons and settings are added to your existing `config.yml`
automatically, and old Inferno/Emerald swords turn into Phoenix Grace/Enigma when next held.

**Upgrading from OP Swords:** delete the old `OPSwords-*.jar` and drop in `Customs-*.jar`. On
first start `plugins/OPSwords` is moved to `plugins/Customs` (config, altars and legendary-mode
records included). Weapons and altar items made before the rename keep working and update to
the new look when held, when their owner joins, or when the chest holding them is opened. The
command is now `/customs` and permissions are `customs.*` instead of `opswords.*`, so update
any permission plugin entries.

<img src="docs/inventory.png" alt="Swords in the inventory" width="400">

### Crafting

Every weapon has a shaped recipe around its vanilla base item, made in a crafting table or by
bringing the ingredients to a weapon altar, set per sword in `config.yml`
(`crafting.enabled` turns all of them off). Players can browse them in-game with
`/customs recipes`, and crafted swords are signed "Forged by <player>". The defaults:

| Sword | Shape | Key |
| --- | --- | --- |
| Dragon Slaying Blade | `BHB / NSN / BEB` | B dragon's breath, H dragon head, N netherite ingot, E end crystal |
| Phoenix Grace | `BFB / TST / BMB` | B blaze rod, F fire charge, T totem of undying, M magma block |
| Frost Blade | `IPI / DSD / IPI` | I blue ice, P powder snow bucket, D diamond block |
| Thunder Bringer | `LBL / CSC / LBL` | L lightning rod, B breeze rod, C copper block |
| Aquatic Sacred Blade | `NHN / PSP / NTN` | N nautilus shell, H heart of the sea, P prismarine crystals, T trident |
| Enigma | `EGE / MSM / EGE` | E emerald block, G golden apple, M moss block |
| Plague Longsword | `WFW / PSP / WFW` | W wither rose, F fermented spider eye, P poisonous potato |
| Vampiric Needle | `RGR / FSF / RNR` | R redstone block, G ghast tear, F crimson fungus, N netherite ingot |
| Righteous Relic | `GBG / LSL / GLG` | G gold block, B beacon, L glowstone |
| Demon Lord's Sword | `OEO / CSC / OEO` | O crying obsidian, E eye of ender, C end crystal |
| Edge Of The Astral Plane | `APA / NSN / APA` | A amethyst block, P ender pearl, N nether star |
| Chrono Blade | `CRC / DSD / CRC` | C clock, R recovery compass, D diamond block |
| Watcher Claymore | `PNP / OSO / PNP` | P phantom membrane, N netherite scrap, O obsidian |
| Awakened Lichblade | `LKL / BSB / LKL` | L soul lantern, K wither skeleton skull, B bone block |
| Sculk Sword | `ECE / HSH / ECE` | E echo shard, C sculk catalyst, H sculk shrieker |
| Hearthflame | `NAN / OSO / NAN` | N netherite ingot, A anvil, O obsidian, **S mace** |
| Requiem of the Ninth Abyss | `PNP / RSR / PNP` | P phantom membrane, N nether star, R wither rose |
| Sunbreak | `BGB / ESE / BGB` | B breeze rod, G gold block, E end rod, **S netherite spear** |
| Royal Chakram | `WEW / BSB / WEW` | W wind charge, E emerald block, B breeze rod |
| Demon Lord's Great Axe | `INI / GSG / INI` | I iron block, N netherite ingot, G goat horn, **S netherite axe** |
| Arcanethyst | `CLC / BSB / CLC` | C amethyst cluster, L lapis block, B bottle o' enchanting |
| Ocean's Rage | `PCP / LSL / PDP` | P prismarine shard, C conduit, L lightning rod, D diamond block, **S trident** |
| Amethyst Shuriken | `PAP / ASA / PAP` | P phantom membrane, A amethyst block |
| Jade Halberd | `EGE / GSG / EGE` | E emerald block, G gold block, **S netherite spear** |
| Watching Warglaive | `PQP / OSO / PQP` | P phantom membrane, Q quartz block, O crying obsidian |
| Cybernetic Chainsaw Blade | `RCR / ISI / RCR` | R redstone block, C copper block, I iron block |
| Royal Rapier | `GDG / DSD / GDG` | G gold block, D diamond |
| Pharaoh's Treasure | `TLT / GSG / TLT` | T chiseled sandstone, L lapis block, G gold block |
| Katana | `IRI / BSB / IRI` | I iron block, R redstone block, B bamboo block |
| Hornet's Needle | `HBH / ESE / HBH` | H honeycomb block, B beehive, E spider eye |
| Waxweaver | `CHC / GSG / CHC` | C candle, H honeycomb block, G glowstone |
| Brimstone Claymore | `MOM / BSB / MOM` | M magma block, O obsidian, B blaze rod |
| Silverine Blade | `GQG / ASA / GQG` | G glass, Q quartz block, A amethyst shard |
| Sabre | `GTG / PSP / GTG` | G gold block, T TNT, P prismarine |
| Soul Claws | `BEB / LSL / BEB` | B bone block, E echo shard, L leather |
| Thousand Demon Daggers | `CWC / ASA / CWC` | C crying obsidian, W wither rose, A amethyst block |
| Divine Punisher | `GBG / TMT / GBG` | G gold block, B beacon, T totem of undying, **M mace** |
| Forest Guardian's Glaive | `LML / ESE / LML` | L oak log, M moss block, E emerald block, **S netherite spear** |
| Power Fuse Hammer | `ICI / LML / ICI` | I iron block, C copper block, L lodestone, **M mace** |
| Cyber Mantis Blade | `ORO / PSP / ORO` | O observer, R redstone block, P piston |

(`S` = netherite sword unless noted.) A Customs weapon can never be used up as an ingredient.

**Legendary mode** (on by default, `crafting.unique-swords`): every weapon can only be crafted
once per server, at a crafting table or an altar, so there is only ever one of each.
`/customs list`, the recipe book and the altars show who forged what; `/customs unforge
<weapon>` lets a lost weapon be crafted again. Admins can always `/customs give`. Set it to
`false` to allow copies.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/customs list` | — | All weapons (hover for details, click for lore) |
| `/customs info [weapon]` | — | Lore and abilities of a weapon |
| `/customs recipes [weapon]` | — | Recipe book GUI (weapons and the altar) |
| `/customs pack` | `customs.pack` | Re-send the resource pack |
| `/customs give <player> <weapon\|all>` | `customs.admin` | Give weapons |
| `/customs menu` | `customs.admin` | Take weapons and altars from a GUI |
| `/customs settings` | `customs.admin` | Change settings, weapon stats and cooldowns in a GUI |
| `/customs altar give [player] [amount]` | `customs.admin` | Give weapon altars |
| `/customs altar set <weapon>` | `customs.admin` | Make the altar you look at forge a weapon (or place a new one on the block you look at) |
| `/customs altar remove` / `list` | `customs.admin` | Remove the altar you look at / list all altars and their weapons |
| `/customs resetcooldowns [player\|*]` | `customs.admin` | Clear cooldowns |
| `/customs unforge <weapon>` | `customs.admin` | Legendary mode: allow crafting a weapon again |
| `/customs reload` | `customs.admin` | Reload `config.yml` |

Aliases: `/custom`, `/weapons`.

Other permissions: `customs.use` (abilities and passives, default everyone),
`customs.craft` (default everyone), `customs.bypass.cooldown` (default nobody).

## Settings menu

`/customs settings` (or the ⚙ button in `/customs menu`) changes the config in game, for
admins: whether abilities hit players, ability damage multipliers, the ability key, unbreakable
weapons, dropped-weapon protection, death messages, every visual effect, crafting, crafting
table recipes, announcements, legendary mode and altars. The **Weapons** page turns each weapon
on or off (left-click) or opens it (right-click) to change its damage, attack speed, both
cooldowns and recipe, or to let a legendary weapon be forged again. Changes are saved to
`config.yml` and apply at once.

## Config highlights

- Per weapon: `enabled`, `attack-damage`, `attack-speed`, `cooldowns`, `recipe`.
- `settings.abilities-hit-players`, `ability-damage-multiplier`,
  `ability-damage-multiplier-vs-players` (tone abilities down for PvP only), `disabled-worlds`,
  `unbreakable`, `protect-dropped-swords` (dropped swords never despawn and glow).
- Cosmetics: `action-bar-hud`, `ability-ready-sound`, `held-aura`, `death-messages`,
  `melee-slashes` (a glowing slash on every hit), `cast-circles` (a spell circle when you use an
  ability), `kill-effects` (a burst of light on kills) and `damage-numbers` (hits show the damage
  dealt above the target: gold for critical swings, the weapon's colour for abilities; rapid hits
  add up on one number).
- `controls.trigger` (`offhand`, `right-click` or `both`), `crafting.unique-swords`,
  `crafting.crafting-table` (turn off to make altars the only way to forge) and `altars.enabled`.
- Changes apply on `/customs reload`; swords players already own update the next time they're held.

## For developers

`dev.infuse.customs.api.SwordAbilityEvent` fires before any ability; cancel it to block the
ability (no cooldown is started). Swords are identified by the `customs:sword` persistent data key.

## Building

Requires Java 21.

```
./gradlew build
```

Output in `build/dist/`: the plugin jar and `Customs-ResourcePack.zip`.
The pack in `resourcepack/` is embedded into the jar at build time. Every push is also built by
GitHub Actions; the jar and pack are attached to each run as the `Customs` artifact.

### The resource pack

The 3D models and textures come from the **Blades of Majestica** resource pack. 40 of its 119
weapons (21 swords and 19 other weapons) are imported into the `customs` namespace, so they're applied by the plugin with the
`item_model` component instead of the original pack's rename-in-an-anvil trick.

```
pip install pillow numpy
python3 tools/import_majestica.py path/to/Blades_Of_Majestica.zip
```

The effect models are original too: `tools/fx_models.py` generates the slash, ring, burst, glow,
beam and spell-circle models in four fade levels. The altar model is original: `tools/altar_model.py` generates it (pedestal, glowing rune windows and the
spinning magic circle) from vanilla stone textures, referenced by name, plus a few small rune and glyph textures.

To use a different model for a weapon, change its entry in `SWORDS` at the top of
`tools/import_majestica.py`, re-run it and rebuild. `tools/modelpreview.py` renders models to a
PNG for a quick look without starting the game.

Blades of Majestica is a third-party pack; check its license before distributing these assets
publicly.
