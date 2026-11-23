# Black Clover SMP Plugin — Design

Date: 2026-11-23
Status: awaiting approval

## Intent

A Paper plugin for a PvP-focused SMP, themed on the anime *Black Clover*. Each player
gets one grimoire that picks their magic for them, as in the anime. The only way to get
stronger is to kill other players, and dying sets you back. The rarest powers
(Anti-Magic and the Five-Leaf Grimoire) can't be rolled. They are won only in server
events, so getting one is a big shared moment.

Success: players fight each other because levelling depends on it, rare magic feels
special, and no single player can farm their way to dominance.

## Goals

- Every player gets one grimoire on first join. It has a randomly rolled magic type
  that is theirs permanently.
- Spells are cast from the grimoire item, use mana and have cooldowns.
- Magic XP comes **only from killing players**. It has farming protection and pays a
  bonus for killing higher-level players.
- Death penalty: lose 15% of total magic XP on **any** death, which can drop your
  level. Quitting during combat counts as a death.
- 13 magic types can be rolled (Light and Time are rare). Anti-Magic is event-only.
- The Five-Leaf Grimoire is an event-only upgrade that works on top of any magic type.
- Admin commands to give prizes, plus one built-in Devil Boss event that admins start
  with a command. Whoever lands the final hit wins the prize.

## Non-goals

- Magic Knight squads, Wizard King, teams or factions (not requested; could come later).
- XP from mobs. Player kills are the only XP source.
- A resource pack or custom textures. Everything uses vanilla items and particles, so
  players need nothing extra to join.
- MySQL or other databases. A YAML file is enough for an SMP.
- Bedrock/Geyser-specific support.

## Anti-goals (would be a failure even if it works)

- **Feeling like Infuse SMP.** Grimoires are never stolen, traded or dropped, and
  death never takes your magic type away.
- **One player snowballing.** Countered by repeat-kill protection, low XP for killing
  weaker players, and the death penalty.
- **Spam-cast fights.** Every spell has a mana cost and a cooldown.
- **Griefing via magic.** Spells never break or permanently place blocks.

## Constraints

- Paper plugin, Java 21, built with Maven. Compiled against `paper-api 1.21.4`,
  `api-version: 1.21`. It must load on Paper 1.21.4 and every newer 1.21.x. It should
  also load on 26.x servers, but that can't be verified here because 26.x needs a newer
  Java than the build machine has.
- Vanilla clients only.
- Spell damage must go through Bukkit's normal damage events, so protection plugins
  (WorldGuard, spawn protection, no-PvP regions) can block it.

## Approaches considered

**How it's built.** Only one approach makes sense: a Paper plugin in Java.
Skript needs an extra plugin and gets slow and fragile with ~60 spells and saved data.
A datapack can't cleanly detect right-clicks or show a mana HUD. A Fabric or Forge mod
would make every player install a mod.

**How players get magic** (chosen by the user):
1. *The grimoire picks you* (random roll, permanent, level up for more spells). **Picked.**
2. Stealable grimoires (Infuse-like). Rejected: snowballing and drama. The user
   explicitly does not want an Infuse-like plugin.
3. Choose your own. Rejected: no surprise, and everyone converges on the best type.

**Death penalty** (chosen by the user):
1. Lose 25% of current-level progress and never drop a level. Too soft for PvP stakes.
2. *Lose 15% of total XP; you can drop levels.* **Picked.**
3. The killer takes your lost XP. Rejected: feeds the top player.

**Events** (chosen by the user):
1. Admin give-commands only.
2. A built-in automatic boss only.
3. *Both: give-commands plus one Devil Boss event that admins start.* **Picked.**

## The design

### Grimoire item

- A `BOOK` with enchant glint, a coloured name (`Fire Grimoire`) and lore showing the
  magic type, level, XP progress and selected spell. It is tagged in its
  PersistentDataContainer with the owner's UUID.
- It **cannot be dropped, stored in containers, or lost on death**: it is removed from
  death drops and given back on respawn. If a non-owner somehow holds one, it does
  nothing.
- `/grimoire` re-summons the grimoire if it's missing from the player's inventory
  (my call). If the inventory is full, the player is told to free a slot; the
  grimoire is never dropped on the ground.
- The Five-Leaf version has a dark name with a `✤ Five-Leaf` prefix.

### First join: the grimoire ceremony

When a player first joins, a title reads "A grimoire has chosen you…" and then reveals
the magic type with particles and a sound. The grimoire goes into their inventory.
Players who were already on the server go through it the next time they join.

### Magic types and rolling

Roll weights live in `config.yml`. Defaults:

| Type | Roll chance | Theme |
|------|-------------|-------|
| Fire, Water, Wind, Lightning, Earth, Plant, Dark | ~8.2% each | classic attributes |
| Spatial, Gravity, Blood, Poison | ~8.2% each | the 5 new types (my call) |
| **Light** | **5%** | rare, as requested |
| **Time** | **5%** | rare (my call: Wizard King's magic) |
| **Anti-Magic** | **never rolled** | events only |

### Casting

- **Right-click** with the grimoire in your main hand casts the selected spell. This
  works on air, blocks and entities, and the normal interaction is cancelled (my call).
- **Shift + right-click** cycles through your unlocked spells (Devil Union included
  for Five-Leaf owners). If a level-down locks your selected spell, the selection moves
  to your highest unlocked spell.
- While you hold the grimoire, the **action bar** shows magic type, level, selected
  spell, mana and that spell's remaining cooldown.
- Not enough mana, spell on cooldown, silenced or frozen: the cast fails with a short
  message and a sound.

### Mana (all values in config)

- Max mana = `100 + 4 × (level − 1)`, so 100 at level 1 and 176 at level 20.
- Regenerates 4 mana per second all the time, even when the grimoire isn't held.
- Anti-Magic uses **no mana**, only cooldowns.

### Levels and XP (all values in config)

- Level cap **20**. Going from level L to L+1 costs `100 + 25 × (L − 1)` XP, so
  reaching level 20 takes 6,175 total XP. XP stops accumulating at the cap.
- Spells unlock at levels **1, 5, 10 and 20** (the level-20 spell is the final spell).
- Spell damage scales +1% per level, up to +19% at level 20.
- **Kill XP:** base 100. +10% for each level the victim is above you (up to +100%),
  −10% for each level below you (down to 20% of base).
- **Repeat-kill protection:** killing the same player again within 10 minutes gives
  no XP.
- **Kill credit** goes to the last player who damaged the victim within 15 seconds.
  This covers spells, arrows and knocking someone into lava.
- **Death penalty:** lose 15% of total XP on any death, which can drop levels. A
  newly locked spell becomes unusable until you level back up. The penalty can hit
  **at most once every 5 minutes**, so spawn-camping can't drain someone (my call).
- **Exception:** deaths to the Devil or its imps during an event cost no XP, so
  players aren't scared off from joining events (my call). PvP deaths during an event
  count as normal.
- With the default curve, one death at level 20 costs about 2 levels, and around level
  10 about 1 level (my call: the user should check this feels right).
- **Combat logging:** quitting within 15 seconds of PvP damage kills the player
  (items drop as in a normal death), applies the penalty and credits the last attacker.
  This is a config toggle, on by default.
- Feedback: a title and sound on level-up, a chat message on level-down naming the
  spell that got locked, and chat lines for XP gained or lost.

### Spells

Spells hit any living entity except the caster, armour stands, and creative/spectator
players. That includes mobs and the Devil Boss. Cost, cooldown and damage for every
spell are in `config.yml`. The numbers below are starting values (damage in health
points; 20 = full health).

**Fire**
1. *Fireball* (Lv1): flaming projectile, 5 dmg, sets the target on fire for 3s.
2. *Flame Wall* (Lv5): wall of fire for 5s, 3 dmg/s and burning to anyone in it.
3. *Blazing Dash* (Lv10): dash forward, burning and damaging (5) anyone you pass.
4. *Inferno* (Lv20): flame burst around you (radius 6), 12 dmg, 6s burn, knockback.

**Water**
1. *Water Bullet*: fast projectile, 4 dmg plus knockback.
2. *Bubble Shield*: 4 absorption hearts and Resistance I for 5s.
3. *Tidal Wave*: cone wave, 6 dmg, strong push and Slowness.
4. *Sea Dragon*: dragon-shaped torrent along a line, 14 dmg; heals you 6.

**Wind**
1. *Wind Blade*: fast slicing projectile, 5 dmg.
2. *Gust Jump*: launches you up and forward; no fall damage for 5s.
3. *Tornado*: whirlwind at the target spot for 4s, lifting enemies, 2 dmg every 0.5s.
4. *Storm of Blades*: blades rain in radius 8 around the target for 3s, ~16 dmg total.

**Lightning**
1. *Spark Bolt*: instant bolt (range 25), 4 dmg.
2. *Thunder Step*: blink 8 blocks forward.
3. *Chain Lightning*: hits a target, then jumps to up to 3 nearby enemies, 6 dmg each.
4. *Thunder God*: 10s of Speed III; melee hits call cosmetic lightning for +4 dmg.

**Earth**
1. *Rock Throw*: slower projectile, 6 dmg.
2. *Stone Wall*: temporary 5×3 stone wall for 8s (air blocks only, always restored).
3. *Earthquake*: ground slam (radius 6), 7 dmg, launch and Slowness.
4. *Earth Titan*: 15s of Resistance II, Strength II and knockback immunity.

**Plant**
1. *Thorn Shot*: vine projectile, 4 dmg plus 2s Slowness.
2. *Healing Bloom*: heals you 6 plus Regeneration.
3. *Binding Vines*: roots the target in place for 3s, 3 dmg.
4. *Thorn Forest*: everyone within radius 7 is rooted 3s and takes 12 dmg over 4s;
   you gain Regeneration.

**Light** (rare)
1. *Light Arrow*: instant beam (range 35), 6 dmg.
2. *Light Speed*: teleport up to 15 blocks where you look.
3. *Light Swords*: 5 swords fire at the nearest enemies, 4 dmg each.
4. *Judgment Ray*: after a 1s charge, a huge beam (40 blocks), 18 dmg.

**Dark**
1. *Dark Orb*: slow orb, 6 dmg plus 1s Blindness.
2. *Shadow Cloak*: Invisibility and Speed for 5s.
3. *Black Hole*: vortex at the target that pulls enemies in for 3s, 2 dmg every 0.5s.
4. *Dimension Slash*: massive slash in front of you (range 20), 16 dmg.

**Spatial** (new)
1. *Space Cut*: slash along an 8-block line, 5 dmg.
2. *Recall Gate*: first cast marks your spot; casting again within 60s returns you there.
3. *Swap*: swap places with the entity you're looking at (range 25).
4. *Banish*: teleports the target ~40 blocks away to a safe surface spot, plus
   Slowness and Weakness for 5s.

**Gravity** (new)
1. *Gravity Bullet*: orb, 4 dmg, pulls the target toward you.
2. *Float*: Slow Falling and Jump Boost for 6s.
3. *Crush*: slams everyone within radius 4 of the target down, 7 dmg, Slowness III.
4. *Singularity*: lifts everyone within radius 10, then slams them down, 15 dmg total.

**Blood** (new)
1. *Blood Needle*: projectile, 5 dmg, heals you 1.
2. *Blood Pact*: costs 4 health instead of mana; restores 40 mana.
3. *Crimson Lash*: whip in a cone (range 6), 7 dmg, Wither 3s, heals you 3.
4. *Blood Moon*: drains everyone within 8 blocks for 3 dmg/s over 4s; you heal half
   the damage dealt.

**Poison** (new)
1. *Toxic Spit*: projectile, 3 dmg plus Poison II for 4s.
2. *Venom Blade*: for 10s your next 3 melee hits apply Poison II and Weakness.
3. *Toxic Cloud*: cloud at the target for 6s, Poison II and Nausea inside.
4. *Plague*: everyone within 10 blocks gets 6 dmg, Wither II 6s, Poison and Slowness.

**Time** (new, rare)
1. *Time Bolt*: projectile, 4 dmg plus Slowness II for 3s.
2. *Accelerate*: Speed II and Haste II for 8s.
3. *Rewind*: returns you to where you were 5 seconds ago, with the health you had then.
4. *Time Stop*: freezes every enemy within 12 blocks for 3s. They can't move, jump or
   cast.

**Anti-Magic** (events only, no mana)
- *Passive:* takes 30% less damage from spells.
1. *Anti-Magic Slash*: sweeping slash (4-block cone), 7 dmg; strips the target's
   active spell buffs (shields, cloak, Thunder God and so on).
2. *Deflect*: immune to spell damage and spell effects for 3s.
3. *Demon Leap*: dash to the target (up to 12 blocks), 8 dmg, silences them for 3s.
4. *Black Form*: 12s of Strength II, Speed II and full spell immunity; melee hits
   silence for 2s.

**Shared status effects:** *silenced* (can't cast), *frozen* (can't move or cast),
*spell-immune*, and temporary spell buffs that Anti-Magic Slash can strip.

### Five-Leaf Grimoire (events only)

An upgrade on top of the player's current magic type. They keep their type, level and
XP.
- +50% max mana, +50% mana regen, +20% spell damage.
- Unlocks an extra spell, **Devil Union**, usable at any level: 15s of Strength I and
  Resistance I, half mana cost and half cooldowns, with dark wing particles. Cooldown
  2 minutes.
- Never lost, including on death.

### Reroll item (my call)

"Grimoire Reroll", a glowing nether star. Right-clicking it rerolls your magic type
(never the same type, never Anti-Magic) and keeps your level and XP. Players get it
only from admins or as an event prize. Anti-Magic holders can't use it, so nobody
throws away an event prize by accident. It's a normal item, so it can be traded.

### Devil Boss event

- `/bcadmin event start <antimagic|fiveleaf|reroll> [here|random]` spawns the Devil at
  the admin's location, or at a random surface spot within a configurable radius of
  world spawn.
- Everyone gets a title and a chat message with the coordinates and the prize, and a
  server-wide boss bar shows the Devil's health.
- **The Devil:** an enlarged Wither Skeleton (scale attribute) named "Devil" with
  600 HP. It doesn't despawn, ignores fire, fall and suffocation damage, and drops no
  vanilla loot. Attacks:
  - *Dark Wave* every 8s: radius 8, 6 dmg plus knockback.
  - *Summon Imps* every 20s: 3 Vexes.
  - *Shadow Step*: teleports next to its target when that player is far away.
  - *Enrage* below 50% HP: faster attacks and more damage.
- **Prize:** the player who lands the final hit wins. Melee, arrows and spells all
  count. If the Devil dies some other way, the last player who damaged it wins. The
  win is broadcast with a title.
  - Anti-Magic changes the winner's magic type to Anti-Magic (keeping level and XP).
  - If the winner already has that prize, they get 500 bonus XP instead (my call).
- The event ends automatically after 30 minutes if the Devil survives. Admins can end
  it with `/bcadmin event stop`. Only one event runs at a time.

### Commands

Players:
- `/grimoire`: re-summon your grimoire.
- `/magic`: your magic type, level, XP, mana and each spell's unlock level.
- `/magic top`: top 10 players by level (my call: fits a PvP server).

Admins (`blackclover.admin`, op by default):
- `/bcadmin give <player> <antimagic|fiveleaf|reroll>`
- `/bcadmin settype <player> <type>`
- `/bcadmin setlevel <player> <level>` and `/bcadmin addxp <player> <amount>`
- `/bcadmin reset <player>`: new random roll at level 1.
- `/bcadmin event start ...` and `/bcadmin event stop`
- `/bcadmin reload`

### Storage and config

- Player data (magic type, total XP, Five-Leaf flag, selected spell, last-penalty
  time) is kept in `plugins/BlackCloverSMP/players.yml`. It's loaded at startup,
  saved asynchronously every 5 minutes, and saved on shutdown.
- `config.yml` holds every number in this document: mana, XP curve, kill XP, penalty
  %, timers, roll weights, per-spell cost, cooldown and damage, event settings, and a
  list of worlds where magic is disabled.

### Safety rules for spells

- Spell damage calls `target.damage(amount, caster)`, so protection plugins can cancel
  it. Spells that only move players (Swap, Banish, Black Hole pull and so on) first
  check, through the same event, that PvP is allowed against that player.
- No spell breaks blocks. Stone Wall replaces only air blocks and always restores them,
  including on server shutdown.
- Teleport spells never put a player inside blocks or above the void.

## Left to the builder

- Internal code structure (spell registry, projectile/beam/area helpers, status
  manager).
- Exact particles, sounds and colours per spell.
- Final tuning numbers, within the spirit of the values above.
- Tests: unit tests for the pure rules (XP curve, kill XP, penalty, repeat-kill window,
  roll weights, mana). Then a smoke test on a real Paper 1.21.x server to confirm the
  plugin loads, and if feasible a scripted bot player that joins and casts spells.
