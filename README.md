# BlackCloverSMP

A Black Clover themed PvP SMP plugin for **Paper 1.21.11**.

There are 15 unique grimoires, one of each, scattered in hidden libraries. You level up only by killing
other players. Grimoires drop when their wielder dies. And there's the **Demon Possessed**, a person the
admins choose, whom the whole server hunts for the Five-Leaf Grimoire.

It comes with its own **resource pack**: 3D grimoires that hover open beside you, 3D spell models, and
long library halls to find them in. The plugin serves the pack itself. Players who decline it still see
normal books and items, so nothing breaks.

---

## Install

1. Build it (needs Java 21 and Maven):
   ```bash
   mvn package
   ```
   This produces `target/BlackCloverSMP-1.1.0.jar` (or grab it from `downloads/`).
2. Put the jar in your server's `plugins/` folder and restart.
   Open port **8163** on your host so players can download the resource pack (see below).
3. As an op (or from the console), start the game:
   ```
   /start 6000 30m
   ```
   This sets a 6000-block world border, gives everyone 30 minutes without PvP, and builds the 13
   libraries.

> **Run `/start` on a fresh world.** Each library is a 15×39 hall and clears that area where it lands,
> so on an existing world it could cut into someone's build.

---

## The resource pack

The pack is built into the plugin jar. On startup the plugin zips it to
`plugins/BlackCloverSMP/BlackCloverSMP-pack.zip`. It also serves the zip on port `8163` and offers it
to every player who joins.

What players get with it:
- **15 3D grimoires,** each with its own leather colour, gold corners and clasp, its magic's rune, and
  the clover on the cover. Anti-Magic's is tattered and black. The Five-Leaf is black with a red five-leaf
  clover.
- **Hovering grimoires:** while you hold your grimoire it **floats open beside you**, pages glowing with
  your magic's script. Other players see it hovering at your shoulder, and it pulses when you cast.
- **A floating grimoire spinning above every altar** that still holds its grimoire.
- **3D spells:** glowing Light Swords, real rocks for Rock Throw, a dark orb, and Anti-Magic's
  demon-slayer greatsword sweeping out with every slash.

Settings in `config.yml` under `resource-pack`:

| Setting | What it does |
|---|---|
| `host: self` | The plugin serves the pack on `port` (default 8163). Open that port. |
| `host: url` | You upload `BlackCloverSMP-pack.zip` somewhere and put its direct link in `url`. Use this if your host only gives you one port. |
| `public-address` | The address players download from. Leave it empty to use the address each player joined with. |
| `required` | `true` makes players accept the pack to stay on the server. |

`/bcadmin pack` shows whether the pack is being served, how many players have it loaded, and your own
download link. `/bcadmin pack resend` offers it to everyone online again.

---

## How it plays

### 1. The race for the libraries
`/start <border size> <grace period>` builds **13 libraries** at random spots **at least 1,000 blocks
from 0,0**, inside the border and spread apart. Their locations are secret.

Each library is a long timber-framed hall:
- **Outside:** a deepslate roof, a bell tower, a covered entrance porch, and clover stained-glass windows.
- **Inside:** bookshelf bays down both sides, chandeliers, a red carpet, and a raised dais at the far end.

The altar (a lectern) on the dais makes **one specific grimoire, once**.

Bring **1 Book, 16 Lapis Lazuli and 1 Diamond** to an altar and right-click it. The grimoire chooses you,
and the server is told who got it. After that the altar goes dormant.

The border must be at least **2116** blocks so the libraries fit.

### 2. The 15 grimoires
| Grimoire | How you get it |
|---|---|
| Fire, Water, Wind, Lightning, Earth, Plant, Dark, **Light** | its library |
| Spatial, Gravity, Blood, Poison, **Time** | its library |
| **Anti-Magic** | admins give it out at events (`/bcadmin`) |
| **Five-Leaf** | held by the Demon Possessed; dropped when they die |

Every grimoire is unique, so Light and Time are as rare as anything can be.

### 3. Grimoire rules
- **One grimoire per player.** You can't pick up a second one.
- **It drops when you die** (except during the grace period), and anyone can pick it up.
- **It can't be destroyed.** A dropped grimoire glows and survives lava, fire, cactus and explosions,
  and never despawns. If it falls into the void, it **returns to its library** and the altar wakes up
  again.
- **No hoarding.** Grimoires can't go into chests, ender chests, shulkers, bundles, hoppers or item
  frames. A grimoire whose holder is offline for 7 days returns home.
- Duplicated or outdated copies delete themselves.

### 4. Levels: PvP only
- Your level belongs to **you and that particular grimoire**. Pick up a grimoire you've never used and
  you start at level 1. Get back one you used before and your old level comes back with it.
- **Killing a player who carries a grimoire** gives 100 magic XP: +10% per level they're above you
  (up to +100%), −10% per level below (down to 20%).
- Killing the same player again within 10 minutes gives no XP.
- Kill credit goes to the last player who hit the victim within 15 seconds. Spells count, and so does
  knocking someone into lava.
- **Dying costs 15%** of your XP on the grimoire you carried, and you can drop levels. This happens at
  most once every 5 minutes, and never during grace.
- **Quitting mid-fight** (within 15 seconds of PvP damage) kills you.
- Level cap 20. Spells unlock at levels **1, 5, 10 and 20**.

### 5. Casting
- **Right-click** with the grimoire to cast the selected spell.
- **Shift + right-click** switches spell.
- Spells use **mana**, which regenerates 4 per second, and have cooldowns. The action bar shows your
  spell, mana and cooldown while you hold the grimoire.
- Spells hit players and mobs, never break blocks, and respect protection plugins (WorldGuard, spawn
  protection and so on).

### 6. The Demon Possessed
An admin opens `/bcadmin` → **Players** → picks someone → **Possess**. It can be a staff member, a guest or
a regular player. That person:
- gets the **Five-Leaf Grimoire** at level 20, with all four devil spells,
- has **double health**, a dark-red ☠ name and a demon aura,
- is announced to the whole server, with their **coordinates broadcast every 5 minutes**.

When they die, the Five-Leaf Grimoire drops where they fell, and everyone is told the location.
Whoever grabs it gets the grimoire, but isn't possessed.

---

## Spells

| Grimoire | Lv 1 | Lv 5 | Lv 10 | Lv 20 |
|---|---|---|---|---|
| Fire | Fireball | Flame Wall | Blazing Dash | Inferno |
| Water | Water Bullet | Bubble Shield | Tidal Wave | Sea Dragon |
| Wind | Wind Blade | Gust Jump | Tornado | Storm of Blades |
| Lightning | Spark Bolt | Thunder Step | Chain Lightning | Thunder God |
| Earth | Rock Throw | Stone Wall | Earthquake | Earth Titan |
| Plant | Thorn Shot | Healing Bloom | Binding Vines | Thorn Forest |
| Light | Light Arrow | Light Speed | Light Swords | Judgment Ray |
| Dark | Dark Orb | Shadow Cloak | Black Hole | Dimension Slash |
| Spatial | Space Cut | Recall Gate | Swap | Banish |
| Gravity | Gravity Bullet | Float | Crush | Singularity |
| Blood | Blood Needle | Blood Pact | Crimson Lash | Blood Moon |
| Poison | Toxic Spit | Venom Blade | Toxic Cloud | Plague |
| Time | Time Bolt | Accelerate | Rewind | Time Stop |
| Anti-Magic* | Anti-Magic Slash | Deflect | Demon Leap | Black Form |
| Five-Leaf | Devil's Claw | Demon Wings | Curse Chains | Devil Union |

\* Anti-Magic uses no mana, takes 30% less damage from spells, and its slash strips enemy spell buffs.
Five-Leaf has +50% mana and regen. Devil Union halves your cooldowns.

`/magic` in game lists your spells with their costs and cooldowns.

---

## Commands

| Command | Who | What |
|---|---|---|
| `/start <border> <grace>` | admin | Start the game, e.g. `/start 6000 30m` (grace accepts `s`, `m`, `h`) |
| `/magic` | everyone | Your grimoire, level, XP, mana, spells, and stored levels on other grimoires |
| `/magic top` | everyone | Top 10 mages by level |
| `/bcadmin` | admin | Opens the admin menu |
| `/bcadmin possess <player>` / `unpossess` | admin | Start / end a demon possession |
| `/bcadmin give <player> antimagic` | admin | Hand out Anti-Magic (their current grimoire goes home) |
| `/bcadmin take <player>` | admin | Send a player's grimoire home |
| `/bcadmin setlevel <player> <1-20>` | admin | Set level on the grimoire they carry |
| `/bcadmin addxp <player> <amount>` | admin | Add XP |
| `/bcadmin grace end` | admin | End the grace period now |
| `/bcadmin reset confirm` | admin | Return every grimoire, clear all levels, allow `/start` again |
| `/bcadmin reload` | admin | Reload `config.yml` |
| `/bcadmin pack [resend]` | admin | Resource pack status and link / offer it to everyone again |

Permission: `blackclover.admin` (ops have it by default).

### Admin menu
- **Players:** possess, give Anti-Magic, take grimoire, level +1/−1, clear XP.
- **Grimoires:** where all 15 are (in a library, held by someone, on the ground at x y z, or in the
  admin pool). Click to teleport to one; shift-click to send it home.
- **Game:** status, end grace, end possession, reset game (asks for confirmation).

---

## Configuration

Everything is in `plugins/BlackCloverSMP/config.yml`: XP curve, kill XP, death penalty, timers, mana,
the altar recipe, library spacing, offline-return days, demon health and broadcast interval, and
worlds where magic is disabled. Every spell's **cost**, **cooldown** and **damage** is listed under
`spells:` and can be tuned. Run `/bcadmin reload` after editing.

Game state lives in `plugins/BlackCloverSMP/data.yml` and is saved automatically.

---

## Development

```bash
mvn test                        # unit tests: game rules, saving, library blueprint, resource pack linter
mvn package                     # builds the plugin jar
python3 tools/pack/generate.py  # regenerates the resource pack art (textures + 3D models)
```

The pack art is generated by `tools/pack/generate.py` into `src/main/resources/resourcepack/`. The
library hall is described block by block in `LibraryBlueprint.java`.

The design and implementation plan are in `docs/superpowers/`.
