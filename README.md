# Bloodbound

A PvP-leaning SMP plugin for **Paper 1.21.11**, built for about 32 players.

Players forge one of five weapons at admin-placed altars. During admin-run **sessions** the weapon grows stronger from kills. Every player killed during a session has their weapon **shattered**, and part of their progress goes to the killer. Admins can trigger **events** mid-session to create big moments.

## Install
1. Build with `./gradlew build`, or take the jar from `build/libs/Bloodbound-1.0.0.jar`.
2. Put it in your server's `plugins/` folder and restart.
3. Tune `plugins/Bloodbound/config.yml` and `messages.yml`, then run `/bb reload`.

Requires Paper 1.21.11 and Java 21. No other plugins and no resource pack.

## How it plays
| | |
|---|---|
| **Altars** | A glowing lodestone. Only admins can place or break one (`/bb altar give`). Altars are protected from explosions and pistons. |
| **Sessions** | Nothing Bloodbound happens outside a session. Altars are dormant, and weapons act like their plain base item. |
| **Forging** | Right-click an altar during a session, pick a weapon and pay the recipe from your inventory. One weapon per player. |
| **Blood** | +100 per player kill (more when the victim is a higher tier) and +2 per hostile mob. Tiers 1–5 at 0 / 150 / 400 / 800 / 1400 blood. |
| **Shatter** | When a player kills you, you lose 30% of your blood (never more than one tier) and they gain half of it. Other deaths cost 10%. Killing the same player again within 10 minutes gives nothing. |
| **Ownership** | Weapons never drop on death, others can't use or pick them up, and they're unbreakable. Lost yours? Reforge at an altar and keep your progress. Old copies stop working. |
| **Controls** | **F** (swap-hand key) uses the active ability (tier 2+). **Sneak + F** uses the ultimate (tier 5). Cooldowns show in the action bar. |

## Weapons
| Weapon | Passive | F: active | Sneak+F: ultimate |
|---|---|---|---|
| **Crimson Blade** | Lifesteal | Blood Dash: dash through enemies, damaging and slowing them | Hemorrhage: hits apply bleed for 6 s |
| **Ravager Axe** | Sunder: bonus damage against armor, stuns shields | Ground Slam: leap, then shockwave on landing | Berserk: Strength and Speed, but you take +25% damage |
| **Tempest Trident** | Stormcharge: every 4th hit adds lightning damage | Gale: pull enemies in front of you | Maelstrom: lightning burst that launches nearby enemies |
| **Earthshaker Mace** | Tremor: hits slow | Skyward: launch straight up, ready to smash | Meteor: rocket up, then a huge shockwave on landing |
| **Phantom Bow** | Seeker: charged arrows curve toward players | Volley: a fan of 5 arrows | Shadowstep: your next arrow teleports you to where it lands |

## Events (admins, during a session)
| Event | Effect |
|---|---|
| `blood_moon` | Night falls. All blood gains ×2. |
| `bounty` | A player glows and their coordinates are broadcast. Killing them gives +300 blood, **and the bounty passes to the killer**. The holder earns blood for every minute they survive. |
| `frenzy` | Ability cooldowns are halved. |
| `last_stand` | No natural regeneration. Kills fully heal you. |
| `altar_surge` | Altars shoot a beam. Standing within 8 blocks of one earns blood every 5 s. |

## Commands
| Command | Who | |
|---|---|---|
| `/bb stats [player]` | everyone | Weapon, tier and blood |
| `/bb top` | everyone | Top 10 by blood |
| `/bb session start [duration]` / `stop` / `status` | admin | e.g. `/bb session start 90m` |
| `/bb event start <type> [duration] [player]` / `stop` / `list` | admin | e.g. `/bb event start bounty 15m Steve` |
| `/bb altar give` / `list` / `remove` | admin | `remove` removes the altar within 5 blocks |
| `/bb blood set\|add <player> <amount>` | admin | |
| `/bb reload` | admin | Reloads config and messages |

Permissions: `bloodbound.use` (default: everyone) and `bloodbound.admin` (default: op).

## Development
- `./gradlew build` compiles and runs the unit tests (`src/test`). Game rules live in `dev.bloodbound.core` and have no Bukkit dependency.
- Design: `docs/superpowers/specs/2026-11-14-bloodbound-design.md`. Plan: `docs/superpowers/plans/2026-11-14-bloodbound.md`. Research: `docs/RESEARCH.md`.
