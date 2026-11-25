# ʙʟᴀᴅᴇ ᴏꜰ ʟᴀᴍʙʀᴏ

A Paper 1.21.5+ plugin. The Blade of Lambro is a netherite sword that unlocks a new power every **3 kills**, eight in total (all eight at 24 kills). It uses a blue and black theme. The kill count is saved on the sword itself, so it carries over if the sword is traded.

## Install

1. Put `dist/BladeOfLambro-1.0.0.jar` in `plugins/` and restart the server.
2. Use `dist/Lambro.zip` as the resource pack. It includes `assets/minecraft/items/blade_of_lambro.json`, which gives the blade the **Storm Bringer** model (a black blade with an animated blue storm layer).
   If you already host the pack, you can add just that one file (`resourcepack-addon/`) instead.
3. Run `/lambro give` in game.

## Powers

| Kills | Power | Type | Activation | What it does |
|---|---|---|---|---|
| 3 | **Undertow** | Passive | hit | Each hit adds a tide mark. On the 3rd mark the target takes bonus damage and Slowness II, with a blue-to-black whirlpool effect. |
| 6 | **Abyssal Dash** | Ability | Right click | Dash forward. Every enemy you pass through takes damage, gets knocked aside and is blinded. You take no fall damage after the dash. |
| 9 | **Tidal Veil** | Passive | under 40% HP | A water sphere closes around you, giving Absorption II and Speed II. 40s cooldown. |
| 12 | **Riftbreaker** | Ability | Sneak + Right click | A whirlpool pulls nearby enemies inward, then explodes with a sonic boom that damages them and launches them up. |
| 15 | **Deepwater Hunger** | Passive | hit / kill | 12% lifesteal, shown as a blue stream back to you. Each kill heals you and cuts 3s off all cooldowns. |
| 18 | **Phantom Edge** | Ability | Swap hands [F] | Throws a spinning, glowing copy of the blade. It hits enemies on the way out and again on the way back. |
| 21 | **Blackwater Aura** | Passive | always | Enemies within 5 blocks get Slowness and Weakness. Two faint arcs orbit your feet. |
| 24 | **Eclipse Tide** | Ultimate | Sneak + Swap [F] | A black eclipse opens over your head for 6s. Jagged blue lightning keeps hitting the nearest enemies. |

While you hold the blade, a tiny-caps action bar shows your kills, each ability's cooldown and kills left to the next unlock. When a power unlocks you get a title, a chat breakdown, sounds and a particle spiral.

## Config GUI: `/lambro gui`

- **Main menu:** all 8 powers. Left click opens a power's settings, right click turns it on or off. Also has kills per unlock, whether player or mob kills count, whether abilities hit passive mobs, the HUD, unlock announcements, unbreakable, give blade, reset held blade and reload.
- **Power menu:** an on/off toggle plus every number for that power (cooldown, damage, radius, durations and so on). Left click raises it, right click lowers it, Shift multiplies the step by 5 and **Q** resets it to the default.

Changes save to `config.yml` right away, and blades players are carrying update straight away.

## Commands (`lambro.admin`, op by default)

`/lambro gui` · `/lambro give [player]` · `/lambro setkills <n>` (the blade in your hand) · `/lambro reload`

## Build

`mvn package` → `target/BladeOfLambro-1.0.0.jar` (Java 21, paper-api 1.21.10).
