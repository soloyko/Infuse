# Bloodbound: design

A Paper 1.21.11 SMP plugin in Java. Players craft a weapon at an admin-placed altar. The weapon grows stronger from PvP during admin-run sessions, and part of that power is lost to whoever kills you.

## Intent

- **Audience:** a server of about 32 players. The goal is fun, with a lean toward PvP.
- **Sessions** are the heart of it. They're scheduled windows (for example, game night) where people fight, have fun and grind. Outside a session the server is normal survival.
- **Admins run the show.** They place altars, start and stop sessions, and trigger custom events to create moments.
- **Clean code:** small classes per feature, all balance values in config, all messages in MiniMessage, no blocking I/O on the main thread, and unit tests for the core logic.

## Goals / non-goals / anti-goals

- **Goals:** a weapon progression players care about, real stakes on death, server-wide moments from events, and easy balancing.
- **Non-goals (v1):** custom 3D models or resource packs, economy/Vault, Discord integration, multi-server sync, Folia support.
- **Anti-goals:**
  - One player snowballing beyond reach (prevented by catch-up bonuses and the shatter mechanic).
  - Kill farming between friends (prevented by a repeat-kill cooldown).
  - Weapons being stolen or duplicated.
  - Anything happening outside a session that bypasses the admin's control.

## Approaches considered

1. **Relics and Rifts:** unique items fought over at random rift events. Rejected because most players would hold nothing at any given time.
2. **Bloodbound weapons:** everyone has a weapon that levels from PvP, and kills shatter tiers. **Chosen by the user**, with these changes: weapons are crafted at altars instead of bound, crafting works only during admin-started sessions, and only admins can place altars.
3. **Crowns:** three buffed "kings" decided by renown. Rejected because there's less build variety and it's less about each player's own progression.

## Decided by the user

- The altar is a **single lodestone block**. Only admins can place altars.
- Weapons are **crafted at altars**. Crafting outside a session is refused.
- Admins start sessions. A session is the time for fighting, fun and grinding.
- Admins can trigger **custom events**.
- Killing a weapon's owner **shatters** part of their progress and feeds the killer's weapon.

## Design

### Altars
- `/bb altar give` gives an admin a special **Altar** lodestone, marked with a PersistentDataContainer tag.
- Placing it registers the location in `altars.yml`.
- Ordinary lodestones stay ordinary.
- Only players with `bloodbound.admin` can place or break altars.
- Altars ignore explosions, pistons, fire and fluids.
- `/bb altar list` and `/bb altar remove` (removes the nearest altar within 5 blocks).
- **During a session:**
  - Altars show ambient particles.
  - Right-clicking one opens the Altar menu. The menu shows the three weapon types and their recipe costs, which come from config.
  - Clicking a type takes the ingredients from the player's inventory and gives them the weapon.
- **Outside a session:** right-clicking says the altar is dormant and opens nothing.

### Weapons
Each player has at most one weapon type at a time, chosen when they first craft.

| Weapon | Base item | Passive (scales with tier) | Active, tier 2+ | Ultimate, tier 5 |
|---|---|---|---|---|
| **Crimson Blade** | Netherite sword | Lifesteal: heal a % of melee damage dealt | **Blood Dash:** dash forward about 8 blocks, damaging and slowing players you pass | **Hemorrhage:** for 6 s, every hit applies a short bleed (damage over time) |
| **Ravager Axe** | Netherite axe | Sunder: bonus damage against armored targets, and hits stun shields | **Ground Slam:** leap, and on landing knock up and damage nearby players | **Berserk:** Strength and Speed for 8 s, but you take +25% damage |
| **Tempest Trident** | Trident | Stormcharge: every 4th hit deals bonus damage with a lightning *effect* (no fire, no block damage) | **Gale:** pull players in a cone in front of you toward you | **Maelstrom:** strike lightning effects around you, damaging and launching nearby players |

- **Using abilities:** right-click activates the active ability. Sneak + right-click activates the ultimate. Holding the weapon shows cooldowns in the action bar. Only the trident's throw uses its normal right-click, so the trident's active is **sneak + left-click** and its ultimate is **sneak + right-click**. (The builder confirms these controls feel right.)
- **Tiers:** 1 to 5. Each tier gives configurable bonus damage and stronger passive values.
- **Abilities, passives and tier bonuses work only during a session.** Outside a session the weapon acts like its plain base item.
- **Ownership:**
  - The item holds the owner's UUID and a weapon version number.
  - In anyone else's hands it does nothing special, and the holder can't attack with it. Hits are cancelled with a message.
  - On death the weapon never drops. It's kept and given back on respawn.
- **One weapon per player:** crafting again while you own a valid weapon is refused. If the item is lost (lava, void, despawn), the player can recraft at an altar during a session, paying the recipe again. This bumps the version, so any old copies stop working. **Progress is kept**, because it's stored on the player, not on the item.
- **Changing type** (for example, Blade to Axe) is allowed through the menu's recraft option at the full recipe cost. Progress is kept.

### Blood (progression)
Blood is earned only during a session and saved per player.

| Source | Blood (config default) |
|---|---|
| Killing a player | +100 |
| Killing a hostile mob | +2 (the grind) |
| Catch-up bonus | +25% per tier the victim is above you |

- **Tier thresholds:** 0 / 150 / 400 / 800 / 1400 (tiers 1–5).
- **Repeat-kill cooldown:** killing the same victim again within 10 minutes gives no player-kill blood and causes no shatter.
- **Shatter (on death by a player, during a session):**
  - The victim loses 30% of their blood, but never more than drops them one tier.
  - The killer gains 50% of the lost amount on top of the kill reward.
  - Dying to mobs or the environment loses 10% of blood, and nobody gains it.

### Sessions
- Commands:
  - `/bb session start [duration]` (for example `90m`; no duration means it runs until stopped).
  - `/bb session stop`.
  - `/bb session status`.
- **Start and end** are announced with a title and a sound.
- **Boss bar** during a session: shows "Session" with the remaining time and the active event name.
- **Persistence:** the session's state and end time are saved, so a session survives a server restart.

### Events
Admins can start events only during a session, one at a time. The commands are `/bb event start <type> [duration]` (default 10 min) and `/bb event stop`. Each event is announced with a title, has its own boss-bar color, and ends automatically.

| Event | Effect |
|---|---|
| **Blood Moon** | Sets the time to night. All blood gains ×2. |
| **Bounty** | Marks a player (named, or the top player by blood if none is named) with Glowing. Their location is broadcast every 60 s. Killing them gives +300 blood. The holder earns +50 blood per minute they survive. |
| **Frenzy** | All ability cooldowns are halved. |
| **Last Stand** | Natural health regeneration is turned off. Getting a player kill fully heals you. |
| **Altar Surge** | Altars shoot a particle beam. Players within 8 blocks of an altar gain +5 blood every 5 s, which creates contested hot zones. |

### Player commands
- `/bb stats [player]`: weapon type, tier, blood, and blood needed for the next tier.
- `/bb top`: top 10 players by blood.

### Admin commands (`bloodbound.admin`)
- `/bb session`, `/bb event` and `/bb altar`, as above.
- `/bb blood <set|add> <player> <amount>`.
- `/bb reload`.

### Storage
- `players.yml` (UUID → type, blood, weapon version, kill-cooldown map is in-memory only).
- `altars.yml`.
- `session.yml`.
- Data is loaded on enable and saved asynchronously every 5 minutes and synchronously on disable.

### Tech
- Java 21, Gradle (Kotlin DSL), `paper-api 1.21.11-R0.1-SNAPSHOT`.
- No runtime dependencies.
- Commands use Paper's Brigadier lifecycle API. Messages use MiniMessage.
- JUnit tests cover the pure logic: tier math, blood and shatter rules, duration parsing, and the repeat-kill cooldown.

## Left to the builder
- Exact particle and sound choices.
- Menu layout.
- Precise ability geometry (dash distance, cone angle, radii), within the spirit above and set in config.
