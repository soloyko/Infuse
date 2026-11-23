# BlackCloverSMP

A Black Clover themed PvP SMP plugin for **Paper 1.21.11**.

There are 15 unique grimoires, one of each, scattered in hidden libraries. You level up only by killing
other players. Grimoires drop when their wielder dies. Players band together in **Magic Knight squads**
and race for stars, the strongest mage is crowned **Wizard King**, and there's the **Demon Possessed**, a
person the admins choose, whom the whole server hunts for the Five-Leaf Grimoire.

It comes with its own **resource pack**: 3D grimoires that float open beside you, 3D spell models, and
tall wizard towers to find them in. Players get the pack automatically when they join, and players who
decline it still see normal books and items, so nothing breaks.

---

## Install

1. Build it (needs Java 21 and Maven):
   ```bash
   mvn package
   ```
   This produces `target/BlackCloverSMP-1.3.0.jar` (or grab it from `downloads/`).
2. Put the jar in your server's `plugins/` folder and restart. No extra ports are needed: the resource
   pack downloads from GitHub (see below).
3. As an op (or from the console), start the game:
   ```
   /start 6000 30m
   ```
   This sets a 6000-block world border, gives everyone 30 minutes without PvP, and builds the 13
   library towers.

> **Run `/start` on a fresh world.** Each tower is 15×15 and 57 blocks tall and clears that space where
> it lands, so on an existing world it could cut into someone's build.

---

## The resource pack

The pack is built into the plugin jar. On startup the plugin zips it to
`plugins/BlackCloverSMP/BlackCloverSMP-pack.zip` and offers it to every player who joins.

By default (`host: auto`) players download it from this repository's `downloads/` folder on GitHub, so
you don't need to open a port. The plugin first downloads that file itself and checks it's exactly the
same pack. If it isn't (or GitHub can't be reached), the plugin serves the pack itself on port `8163`
instead, logs a warning, and checks the link again every 5 minutes for an hour, switching to GitHub as
soon as the file is there.

What players get with it:
- **15 3D grimoires,** each with its own leather colour, gold corners and clasp, its magic's rune, and
  the clover on the cover. Anti-Magic's is tattered and black. The Five-Leaf is black with a red five-leaf
  clover.
- **Floating grimoires:** while you hold your grimoire (main hand or off hand) it **floats open at your
  right shoulder**, pages turned towards you and glowing with your magic's script. You see it in first
  person, and everyone else sees it too. It bobs as you move, pulses when you cast, and your spells
  launch from its pages.
- **A floating grimoire spinning above every altar** that still holds its grimoire.
- **3D spells:** glowing Light Swords, real rocks for Stone Bullet, a dark orb, and Anti-Magic's
  demon-slayer greatsword sweeping out with every slash.

Settings in `config.yml` under `resource-pack`:

| Setting | What it does |
|---|---|
| `host: auto` | Default. Uses `url` (GitHub) if it holds this exact pack, otherwise serves it on `port`. |
| `host: url` | Always use `url`. Upload `BlackCloverSMP-pack.zip` somewhere and put its direct link there. |
| `host: self` | Always serve the pack on `port` (default 8163). Open that port. |
| `public-address` | The address players download from. Leave it empty to use the address each player joined with. |
| `required` | `true` makes players accept the pack to stay on the server. |

`/bcadmin pack` shows whether the pack is being served, how many players have it loaded, and your own
download link. `/bcadmin pack resend` offers it to everyone online again.

---

## How it plays

### 1. The race for the libraries
`/start <border size> <grace period>` builds **13 libraries** at random spots **at least 1,000 blocks
from 0,0**, inside the border and spread apart. Their locations are secret.

Each library is a **round wizard tower, 57 blocks tall**, that you can see from far away:
- **Outside:** weathered stone with dark bands, buttresses, green clover windows, a balcony ring with
  lanterns, and a deepslate spire with a gold tip.
- **Inside:** four library floors of bookshelf rings, climbed by a spiral staircase that winds round a
  glowing central pillar.
- **At the top:** the altar room, lit by candles and soul lanterns.

The altar (a lectern) at the top of the tower makes **one specific grimoire, once**.

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
- **Shift + right-click** switches to the next spell.
- **Shift + left-click** (or `/magic`) opens your **spellbook**: your level, XP and mana, and the four
  spells with what each does, its cost, cooldown and power. Click a spell to select it.
- Spells use **mana**, which regenerates 4 per second, and have cooldowns. The action bar shows your
  spell, mana bar and cooldown while you hold the grimoire.
- **Spell damage is magic:** armour points don't block it, but Protection enchantments still do.
- Spells hit players and mobs, never break blocks, and respect protection plugins (WorldGuard, spawn
  protection and so on). Ultimates are announced to everyone nearby.

### 6. Magic Knight squads
Players team up in squads of up to 6. `/squad` opens the squad menu.
- **Found one** with `/squad create <name>`; you're its captain. Each squad gets its own colour.
- **Recruit:** the captain invites online players (`/squad invite <player>` or from the menu). The
  invite has a **[Join]** button and lasts 5 minutes.
- **No friendly fire:** squadmates can't hurt each other with weapons, arrows or spells. Spells fly
  straight through allies.
- **Support spells help allies:** Verdant Bloom also heals and cleanses squadmates within 10 blocks.
- **Squad chat:** `/sc` switches your chat to squad-only (`/sc` again to switch back), or
  `/sc <message>` sends one message.
- **Stars:** your squad earns ★ 1 for each kill of a player from outside it (not repeat kills),
  ★ 3 when a member is chosen by a grimoire, and ★ 5 for slaying the Demon Possessed. `/squad top`
  shows the ranking.
- Squad names show above heads, in the tab list and in chat. Squadmates can see each other even while
  invisible.
- The captain can kick, hand over the captaincy, change the colour or disband (all from the menu). If
  the captain leaves, the longest-serving member takes over.

### 7. Wizard King
The highest-level grimoire holder (level 5 or more) is crowned **Wizard King**. It's announced to the
server, the king gets a ♛ in their name tag, tab list and chat, and a small golden crown of light
floats above their head. If the king dies or loses their grimoire, the crown passes on. The Demon
Possessed can't be king.

### 8. On screen
- **Sidebar:** your grimoire, level bar, mana, kills and deaths, squad and stars, the Wizard King, and
  the PvP timer. `/magic hud` hides or shows it.
- **Tab list:** a Black Clover header, the Wizard King and top squad in the footer, and every player's
  squad and level.
- **Chat:** messages show the sender's crown, squad and level.
- **Kill feed:** `☠ Noelle [Water 7] was slain by Asta [Anti-Magic 9]`.
- **Level ups** burst with light in your grimoire's colour.
- Anyone within 64 blocks of the Demon Possessed sees its **health bar**.

### 9. The Demon Possessed
An admin opens `/bcadmin` → **Players** → picks someone → **Possess**. It can be a staff member, a guest or
a regular player. That person:
- gets the **Five-Leaf Grimoire** at level 20, with all four devil spells,
- has **double health**, a dark-red ☠ name and a demon aura,
- is announced to the whole server, with their **coordinates broadcast every 5 minutes**.

When they die, the Five-Leaf Grimoire drops where they fell, and everyone is told the location.
Whoever grabs it gets the grimoire, but isn't possessed.

---

## Spells

Every grimoire has four spells, each with a clear job: an **attack**, a **mobility, defence or support**
spell, a **control** spell, and an **ultimate**.

| Grimoire | Lv 1 · Attack | Lv 5 | Lv 10 · Control | Lv 20 · Ultimate |
|---|---|---|---|---|
| Fire | Flame Bolt | Blaze Dash *(mobility)* | Inferno Pillar | Salamander Nova |
| Water | Water Bullets | Bubble Barrier *(defence)* | Tidal Wave | Sea Dragon's Roar |
| Wind | Wind Blade | Gale Step *(mobility)* | Tornado | Gale Annihilation |
| Lightning | Thunder Bolt | Thunder Step *(mobility)* | Chain Lightning | Thunder God's Descent |
| Earth | Stone Bullet | Rampart *(defence)* | Rising Spikes | Titan Quake |
| Plant | Thorn Volley | Verdant Bloom *(support)* | Binding Vines | Thorn Garden |
| Light | Light Arrow | Light Speed *(mobility)* | Light Swords of Judgment | Judgment Ray |
| Dark | Dark Orb | Shadow Veil *(defence)* | Black Hole | Dimension Slash |
| Spatial | Space Cut | Portal Gate *(mobility)* | Space Swap | Spatial Exile |
| Gravity | Gravity Orb | Weightless *(mobility)* | Gravity Crush | Singularity |
| Blood | Blood Needles | Blood Pact *(support)* | Crimson Lash | Blood Moon |
| Poison | Toxic Spit | Venom Coat *(support)* | Miasma | Plague |
| Time | Time Bolt | Accelerate *(support)* | Rewind | Time Stop |
| Anti-Magic* | Demon-Slayer Slash | Black Dash *(mobility)* | Anti-Magic Leap | Black Form |
| Five-Leaf | Devil's Claw | Demon Wings *(mobility)* | Curse Chains | Devil Union |

\* Anti-Magic uses no mana, takes 30% less damage from spells, and its slash strips enemy spell buffs.
Five-Leaf has +50% mana and regen. Devil Union and Accelerate halve your cooldowns.

The spellbook (`/magic` or shift + left-click) describes every spell. `/magic info` prints the same in
chat.

---

## Commands

| Command | Who | What |
|---|---|---|
| `/start <border> <grace>` | admin | Start the game, e.g. `/start 6000 30m` (grace accepts `s`, `m`, `h`) |
| `/magic` | everyone | Open your spellbook: level, XP, mana, the four spells (click to select), stored levels |
| `/magic info` | everyone | The same information in chat |
| `/magic hud` | everyone | Hide or show the sidebar |
| `/squad` | everyone | Open the squad menu |
| `/squad create <name>` · `invite <player>` · `join [squad]` · `leave` | everyone | Found, recruit, join, leave |
| `/squad kick <member>` · `captain <member>` · `color <colour>` · `disband confirm` | captain | Manage the squad |
| `/squad info [squad]` · `/squad top` | everyone | Squad details and ranking |
| `/sc [message]` | everyone | Squad chat: toggle it, or send one message |
| `/magic top` | everyone | Top 10 mages by level |
| `/bcadmin` | admin | Opens the admin menu |
| `/bcadmin possess <player>` / `unpossess` | admin | Start / end a demon possession |
| `/bcadmin give <player> antimagic` | admin | Hand out Anti-Magic (their current grimoire goes home) |
| `/bcadmin take <player>` | admin | Send a player's grimoire home |
| `/bcadmin setlevel <player> <1-20>` | admin | Set level on the grimoire they carry |
| `/bcadmin addxp <player> <amount>` | admin | Add XP |
| `/bcadmin grace end` | admin | End the grace period now |
| `/bcadmin reset confirm` | admin | Return every grimoire, clear levels, kill stats and squad stars, allow `/start` again |
| `/bcadmin squad disband <squad>` | admin | Remove a squad (for example, an offensive name) |
| `/bcadmin squad stars <amount> <squad>` | admin | Give or take stars (event prizes) |
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
worlds where magic is disabled, squad size, friendly fire and star rewards, the Wizard King's minimum
level, and which on-screen parts are on. Every spell's **cost**, **cooldown** and **damage** is listed
under `spells:` and can be tuned. Run `/bcadmin reload` after editing.

If another plugin already manages the scoreboard, tab list or chat format, turn the matching option off
under `hud:` (`sidebar`, `nametags`, `tab-list`, `chat-format`).

Game state lives in `plugins/BlackCloverSMP/data.yml` and is saved automatically.

---

## Development

```bash
mvn test                        # unit tests: game rules, saving, tower blueprint, spells, resource pack linter
mvn package                     # builds the plugin jar
python3 tools/pack/generate.py  # regenerates the resource pack art (textures + 3D models)
```

The pack art is generated by `tools/pack/generate.py` into `src/main/resources/resourcepack/`. The
library tower is described block by block in `TowerBlueprint.java`.

The design and implementation plan are in `docs/superpowers/`.
