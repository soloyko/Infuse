# Black Clover SMP Plugin — Design

Date: 2026-11-23
Status: awaiting approval (revision 2)

## Intent

A Paper plugin for a PvP-focused SMP, themed on the anime *Black Clover*. When an
admin starts the game, a fixed set of **unique grimoires** is placed in libraries
scattered across the world. Each grimoire can be crafted once. After that it changes
hands only when its wielder dies and drops it. Players level up by killing other
players, and each player's level is tied to the specific grimoire they earned it on.
The rarest power, the Five-Leaf Grimoire, is held by the **Demon Possessed**: a real
person the admins choose, whom the whole server hunts.

Success: a race for the libraries at the start, then a server where every grimoire has
a story, fights matter, and no single player can stay on top for long.

## Goals

- `/start <border size> <grace period>` sets the world border, starts a PvP-free grace
  period and builds one library per craftable grimoire.
- **Every grimoire is unique.** One exists of each kind, and each craftable one has
  exactly one library where it can be crafted once.
- Grimoires **drop on death** and can be picked up by anyone.
- Levels are **per player per grimoire**. A new wielder starts at level 1; a previous
  wielder who gets it back resumes their old level.
- Magic XP comes **only from killing players**. There's repeat-kill protection and a
  bonus for killing higher-level players.
- Death penalty: lose 15% of your XP on your current grimoire on any death.
  Quitting during combat counts as a death.
- Spells are cast from the grimoire, use mana and have cooldowns.
- Admin **GUI** to pick the Demon Possessed, give Anti-Magic, manage players and track
  every grimoire.

## Non-goals

- Grimoires given automatically on join.
- Random magic rolls and a reroll item (dropped in revision 2: the library you reach
  decides your magic).
- XP from mobs.
- Squads, Wizard King, teams or factions.
- A computer-controlled boss. The Demon Possessed is played by a real person.
- A resource pack or custom textures. Everything uses vanilla items and particles.
- MySQL or other databases, and Bedrock/Geyser support.

## Anti-goals (would be a failure even if it works)

- **One player snowballing.** Countered by levels resetting for a new wielder, only one
  grimoire per player, repeat-kill protection, low XP for killing weaker players, and
  the death penalty.
- **Grimoires disappearing from the game.** A grimoire must never be lost for good:
  not in lava, not in the void, not to despawning, not to an inactive player.
- **Hoarding.** One player sitting on several grimoires would take them out of play.
- **Spam-cast fights.** Every spell has a mana cost and a cooldown.
- **Griefing via magic.** Spells never break or permanently place blocks.

## Constraints

- Paper plugin, Java 21, Maven. The server runs **Paper 1.21.11**, so the plugin is
  compiled against `paper-api 1.21.11`, `api-version: 1.21`.
- Vanilla clients only.
- Spell damage goes through Bukkit's normal damage events, so protection plugins can
  block it.

## Approaches considered

**How it's built.** Only one approach makes sense: a Paper plugin in Java.
Skript gets slow and fragile at this size. A datapack can't do right-click casting, a
mana HUD or GUIs cleanly. A Fabric or Forge mod would make every player install a mod.

**Decisions the user made in conversation:**
- *How players get magic:* first a random roll on join (rev 1), then replaced by
  **unique grimoires crafted once at libraries**, which drop on death (rev 2).
- *Death penalty:* chosen over "keep level" and "killer takes XP": **lose 15% of
  XP, can drop levels.**
- *Levels when a grimoire changes hands:* chosen over "level stays with the player"
  and "level lives in the grimoire": **per player per grimoire.**
- *Five-Leaf source:* first a Devil Boss event (rev 1), then replaced by **the Demon
  Possessed, a real person the admins choose through a GUI** (rev 2).
- *Anti-Magic source:* **admins only** (given through the GUI at events they run).

## The design

### Game start: `/start <border size> <grace period>`

- Admin only. Example: `/start 6000 30m` (grace accepts `s`, `m` and `h`). It works on
  the overworld, centred on 0,0.
- Sets the world border diameter to `<border size>`.
- **Grace period:** PvP is off, deaths cost no XP and grimoires don't drop. A boss bar
  counts down, and at the end everyone gets a title: "PvP is now enabled".
- Builds the **13 libraries** (one per craftable grimoire) at random surface spots
  **at least 1,000 blocks from 0,0** and at least 50 blocks inside the border. Each
  library is at least 400 blocks from every other one, and that spacing is relaxed
  automatically if the border is too small to fit them. If the border radius isn't big
  enough to fit any spot 1,000+ blocks out, the command refuses and says why.
- Library locations are **not announced**. Players have to explore to find them (my
  call).
- `/start` runs once per world. To start over, an admin uses **Reset game** in the
  admin GUI (see below).

### Libraries and altars

- **Library:** a small stone and wood building (about 9×9) lined with bookshelves,
  built on cleared, levelled ground. The whole library is protected: no breaking,
  placing or explosions inside it (my call: stops players from walling it off or
  griefing it).
- **Altar:** a lectern in the centre with floating text above it naming the grimoire
  and its recipe, e.g. "Fire Grimoire — 1 Book, 16 Lapis Lazuli, 1 Diamond". The text
  is only visible up close, so you find out which grimoire it is by reaching it.
- **Crafting:** right-click the altar while carrying the ingredients and no other
  grimoire. The ingredients are used up, the "A grimoire has chosen you…" title,
  particles and sound play, and the grimoire goes into your inventory. The recipe is
  in config (default: 1 book, 16 lapis, 1 diamond).
- After crafting, the altar goes **dormant** (text: "This grimoire has already chosen
  its master"). If that grimoire ever returns to its library (see below), the altar
  wakes up and can be crafted again.

### The 15 grimoires

| Grimoire | How it's obtained |
|----------|-------------------|
| Fire, Water, Wind, Lightning, Earth, Plant, Dark, Light | library (one each) |
| Spatial, Gravity, Blood, Poison, Time | library (one each; the 5 new types, my call) |
| **Anti-Magic** | admins give it (GUI) at events |
| **Five-Leaf** | held by the Demon Possessed; dropped when they die |

Since there's only one of each, Light and Time are automatically as rare as anything
can be.

### Grimoire item rules

- A `BOOK` with enchant glint, a coloured name and lore showing the grimoire, the
  holder's level on it, XP progress and the selected spell. A PersistentDataContainer
  tag marks which grimoire it is.
- **Anyone carrying it can use it.** There's no binding to one player.
- **One grimoire per player.** You can't pick up a second while carrying one. You can
  throw yours (Q) to drop it, for example to hand it to a friend.
- **No hoarding:** grimoires can't go into chests, ender chests, shulker boxes,
  bundles, hoppers, item frames or any other container.
- **Drops on any death** (except during grace) at the death spot.
- **Dropped grimoires can't be destroyed.** The item entity is invulnerable (lava,
  fire, cactus, explosions), never despawns and glows so it's easy to spot.
- **Return to library:** a grimoire goes back to its library, and its altar wakes up,
  if its item falls into the void or is somehow removed, if its holder stays offline
  for **7 days** (my call, in config), or if an admin returns it. Anti-Magic and
  Five-Leaf have no library, so they return to the admin pool and can be handed out
  again.
- The plugin always knows where every grimoire is (held by whom, on the ground where,
  or in its library). The admin GUI shows this.

### Levels and XP (all values in config)

- **Levels are per player per grimoire**, stored as `player → grimoire → XP`. Picking
  up a grimoire you've never wielded starts you at level 1. Picking up one you wielded
  before resumes your stored XP for it, minus any death penalties you took.
- Level cap **20**. Going from level L to L+1 costs `100 + 25 × (L − 1)` XP (6,175 total
  to reach 20). XP stops at the cap.
- Spells unlock at levels **1, 5, 10 and 20**. Spell damage scales +1% per level, up
  to +19%.
- **Kill XP:** the killer must be carrying a grimoire, and so must the victim. Base
  100, +10% for each level the victim is above you (up to +100%), −10% for each level
  below you (down to 20%). "Level" here means each player's level on the grimoire
  they're carrying.
- **Repeat-kill protection:** no XP for killing the same player again within 10
  minutes.
- **Kill credit** goes to the last player who damaged the victim within 15 seconds
  (spells, arrows, knocking someone into lava).
- **Death penalty:** on any death, lose 15% of your XP on the grimoire you were
  carrying, which can drop your level. It hits at most once every 5 minutes (my call:
  stops spawn-camp draining). No penalty during grace. Rough cost: about 2 levels at
  level 20 and about 1 at level 10.
- **Combat logging:** quitting within 15 seconds of PvP damage kills you on quit. You
  drop your grimoire and items, take the penalty, and the last attacker gets the kill.
  This is a config toggle, on by default.
- Feedback: a title and sound on level-up, a chat line on level-down naming the spell
  that got locked, and chat lines for XP gained or lost.

### Casting

- **Right-click** with the grimoire in your main hand casts the selected spell. This
  works on air, blocks and entities, and the normal interaction is cancelled.
- **Shift + right-click** cycles through your unlocked spells. If a level-down locks
  your selected spell, the selection moves to your highest unlocked one.
- While you hold it, the **action bar** shows grimoire, level, selected spell, mana and
  cooldown.
- The cast fails (message and sound) when mana is short, the spell is on cooldown, or
  you're silenced or frozen.

### Mana (config)

- Max mana = `100 + 4 × (level − 1)`. Regen 4 per second, always.
- Five-Leaf: +50% max mana and regen. Anti-Magic: uses no mana, cooldowns only.

### Spells

Spells hit any living entity except the caster, armour stands, and creative/spectator
players. Cost, cooldown and damage for every spell are in `config.yml`. The values
below are starting points (damage in health points; 20 = full health).

**Fire**: *Fireball* (5 dmg, 3s burn) · *Flame Wall* (5s wall, 3 dmg/s and burn) ·
*Blazing Dash* (dash, 5 dmg and burn to anyone passed) · *Inferno* (radius 6 burst,
12 dmg, 6s burn, knockback)

**Water**: *Water Bullet* (4 dmg, knockback) · *Bubble Shield* (4 absorption hearts,
Resistance I 5s) · *Tidal Wave* (cone, 6 dmg, big push, Slowness) · *Sea Dragon*
(line torrent, 14 dmg, heals you 6)

**Wind**: *Wind Blade* (fast, 5 dmg) · *Gust Jump* (launch, no fall damage 5s) ·
*Tornado* (4s at target, lifts, 2 dmg/0.5s) · *Storm of Blades* (radius 8 for 3s,
~16 dmg total)

**Lightning**: *Spark Bolt* (instant, range 25, 4 dmg) · *Thunder Step* (8-block blink) ·
*Chain Lightning* (target + 3 jumps, 6 dmg each) · *Thunder God* (10s Speed III,
melee +4 dmg with cosmetic lightning)

**Earth**: *Rock Throw* (6 dmg) · *Stone Wall* (temporary 5×3 wall 8s, air only,
always restored) · *Earthquake* (radius 6, 7 dmg, launch, Slowness) · *Earth Titan*
(15s Resistance II, Strength II, knockback immunity)

**Plant**: *Thorn Shot* (4 dmg, Slowness 2s) · *Healing Bloom* (heal 6, Regeneration) ·
*Binding Vines* (root target 3s, 3 dmg) · *Thorn Forest* (radius 7: root 3s, 12 dmg
over 4s, you regenerate)

**Light**: *Light Arrow* (instant, range 35, 6 dmg) · *Light Speed* (15-block teleport) ·
*Light Swords* (5 swords at nearest enemies, 4 dmg each) · *Judgment Ray* (1s charge,
40-block beam, 18 dmg)

**Dark**: *Dark Orb* (6 dmg, Blindness 1s) · *Shadow Cloak* (Invisibility and Speed 5s) ·
*Black Hole* (3s vortex pulls enemies in, 2 dmg/0.5s) · *Dimension Slash* (range 20,
16 dmg)

**Spatial**: *Space Cut* (8-block line, 5 dmg) · *Recall Gate* (mark, recast within
60s to return) · *Swap* (swap places with target, range 25) · *Banish* (target sent
~40 blocks away to a safe spot, Slowness and Weakness 5s)

**Gravity**: *Gravity Bullet* (4 dmg, pulls target to you) · *Float* (Slow Falling and
Jump Boost 6s) · *Crush* (radius 4 slam, 7 dmg, Slowness III) · *Singularity* (radius
10 lift then slam, 15 dmg)

**Blood**: *Blood Needle* (5 dmg, heal 1) · *Blood Pact* (costs 4 health, restores 40
mana) · *Crimson Lash* (cone range 6, 7 dmg, Wither 3s, heal 3) · *Blood Moon*
(radius 8, 3 dmg/s for 4s, heal half the damage dealt)

**Poison**: *Toxic Spit* (3 dmg, Poison II 4s) · *Venom Blade* (next 3 melee hits in
10s: Poison II and Weakness) · *Toxic Cloud* (6s cloud: Poison II, Nausea) ·
*Plague* (radius 10: 6 dmg, Wither II 6s, Poison, Slowness)

**Time**: *Time Bolt* (4 dmg, Slowness II 3s) · *Accelerate* (Speed II and Haste II
8s) · *Rewind* (back to your position and health from 5s ago) · *Time Stop* (radius
12: enemies frozen 3s, can't move or cast)

**Anti-Magic** (no mana; passive: 30% less spell damage taken): *Anti-Magic Slash*
(4-block cone, 7 dmg, strips spell buffs) · *Deflect* (immune to spells 3s) ·
*Demon Leap* (dash up to 12 blocks, 8 dmg, silence 3s) · *Black Form* (12s
Strength II, Speed II, spell immunity, melee silences 2s)

**Five-Leaf: devil magic** (+50% mana and regen): *Devil's Claw* (7 dmg, Wither 2s) ·
*Demon Wings* (powerful launch plus 6s of gliding) · *Curse Chains* (target within
20: rooted and silenced 3s, 6 dmg) · *Devil Union* (15s Strength II, Resistance I,
half cooldowns, dark aura dealing 2 dmg/s within 4 blocks)

**Shared status effects:** *silenced* (can't cast), *frozen* (can't move or cast),
*spell-immune*, and temporary spell buffs that Anti-Magic Slash strips.

### The Demon Possessed

- **Chosen by admins** in the GUI: pick any online player (a staff member, a guest or
  a regular player) and click *Possess*. Only one can exist at a time.
- On possession:
  - The player is given the Five-Leaf Grimoire. If they're carrying another grimoire,
    it returns to its library first.
  - Their XP on the Five-Leaf is set to **level 20** so they start with all four devil
    spells (my call: the hunted villain should be dangerous from the first minute).
  - Max health is doubled (40), they get a dark-red name and a constant dark aura of
    particles.
  - A server-wide title announces the possession.
  - Their coordinates are broadcast every 5 minutes (my call, in config), which turns
    it into a manhunt.
- When the Demon Possessed **dies** (any cause, including during grace), the
  possession ends: their health and name go back to normal and the Five-Leaf drops at
  the death spot. Whoever picks it up uses it under the normal rules: level 1 if new
  to it, and they are *not* possessed.
- Admins can end a possession from the GUI. The Five-Leaf then returns to the admin
  pool.
- Possession survives a relog. Combat-logging as the Demon Possessed counts as death.

### Admin GUI and commands

`/bcadmin` (permission `blackclover.admin`, op by default) opens a chest-style menu:

- **Players:** heads of online players. Clicking one opens actions: *Possess*, *Give
  Anti-Magic* (only if it's in the admin pool), *Take grimoire* (returns it home),
  *Level +1 / −1*, *Clear XP*.
- **Grimoires:** all 15 with their status (in library / held by X / on ground at
  x,y,z / in admin pool). Clicking one teleports the admin to it; shift-click returns
  it home.
- **Game:** border size, grace time left, *End grace now*, *End possession*,
  *Reset game*. Reset game asks for confirmation. It removes all grimoire items from
  online players, the ground and offline players as they next join, removes library
  protection, and clears game state so `/start` can run again. Old library buildings
  are left as ruins.

Text commands for everything in the GUI, for console and scripting:
`/bcadmin possess <player>`, `/bcadmin unpossess`,
`/bcadmin give <player> antimagic`, `/bcadmin take <player>`,
`/bcadmin setlevel <player> <level>`, `/bcadmin addxp <player> <amount>`,
`/bcadmin grace end`, `/bcadmin reload`.

Players:
- `/magic`: your grimoire, level, XP, mana and spell unlock levels, plus your stored
  levels on grimoires you've used before.
- `/magic top`: top 10 by level on the grimoire currently carried (my call).

### Storage and config

- `plugins/BlackCloverSMP/data.yml` holds game state (started, border, grace end),
  library locations and altar states, each grimoire's status and location, per-player
  per-grimoire XP, the last-penalty time, the Demon Possessed, and the queue of
  pending removals for offline players. It's loaded at startup, saved asynchronously
  every 5 minutes, and saved on shutdown.
- `config.yml` holds every number above: mana, XP curve, kill XP, penalty %, timers,
  recipe, library spacing, offline-return days, possession settings, per-spell cost,
  cooldown and damage, and worlds where magic is disabled.

### Safety rules

- Spell damage calls `target.damage(amount, caster)`, so protection plugins can cancel
  it. Spells that only move players (Swap, Banish, Black Hole and so on) first check
  through the same event that PvP is allowed. No spell affects players during grace.
- No spell breaks blocks. Stone Wall replaces only air and always restores it,
  including on shutdown.
- Teleport spells never put a player inside blocks or above the void.

## Left to the builder

- Internal structure (spell registry, projectile/beam/area helpers, status manager,
  grimoire tracker).
- Exact library layout, particles, sounds and colours.
- Final tuning numbers within the spirit of the values above.
- Tests: unit tests for the pure rules (XP curve, kill XP, penalty, per-grimoire level
  storage, repeat-kill window, library placement maths). Then a real Paper 1.21.11
  server smoke test confirming the plugin loads and `/start` builds libraries. If
  feasible, a scripted bot player that crafts a grimoire and casts spells.
