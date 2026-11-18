# Bloodbound Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Bloodbound Paper 1.21.11 plugin: five altar-crafted weapons that level up from PvP during admin sessions, with shatter-on-death and admin events.

**Architecture:** Pure game rules (tiers, blood, shatter, durations, kill cooldowns) live in `core/` with no Bukkit imports and are unit-tested. Bukkit-facing features (altars, weapons, abilities, sessions, events, commands) are each in their own package and get their dependencies through constructors from `BloodboundPlugin`. State is saved in YAML through small store classes.

**Tech Stack:** Java 21, Gradle 8 (Kotlin DSL), `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` (compileOnly), JUnit 5, MiniMessage (bundled with Paper), Paper Brigadier command API.

**Spec:** `docs/superpowers/specs/2026-11-14-bloodbound-design.md`

## Global Constraints
- Target Paper 1.21.11, Java 21, no runtime dependencies (the jar contains only plugin classes).
- Base package `dev.bloodbound`. Command `/bloodbound`, alias `/bb`. Admin permission `bloodbound.admin`.
- Every balance number lives in `config.yml`. Every player-facing string lives in `messages.yml` as MiniMessage.
- No blocking file I/O on the main thread except on disable.
- Defaults from the spec: kill +100, mob +2, catch-up +25% per tier above, thresholds 0/150/400/800/1400, repeat-kill cooldown 10 min, shatter 30% capped at one tier, killer gets 50% of the lost amount, environmental death loses 10%.

## Review Focus
1. Killer and victim are the same player (suicide via own arrow or ability): no reward, treated as an environmental death. Tested in `BloodRulesTest`.
2. A victim at tier 1 with 0 blood dies: loses nothing, and the killer still gets the kill reward. Tested in `BloodRulesTest`.
3. A session ends while an event is running: the event stops and its effects (glowing, regen rule) are cleared. Handled in `SessionManager.stop`.
4. A player logs out while holding the Bounty: the bounty moves to the next top player online or ends. Handled in `EventManager`.
5. Bad duration input (`/bb session start 5x`): rejected with a message, no crash. Tested in `DurationsTest`.

---

### Task 1: Project scaffold
**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/*`, `gradlew`, `src/main/resources/plugin.yml`, `src/main/java/dev/bloodbound/BloodboundPlugin.java`, `.gitignore`
- [ ] Write the Gradle build (paper repo, compileOnly paper-api, JUnit 5, Java 21 toolchain release, processResources expands `version`).
- [ ] `plugin.yml`: `api-version: '1.21.11'`, main `dev.bloodbound.BloodboundPlugin`, permissions `bloodbound.admin` (default op) and `bloodbound.use` (default true).
- [ ] Run `gradle wrapper`, then `./gradlew build`. Expected: BUILD SUCCESSFUL. Commit.

### Task 2: Core rules (TDD)
**Files:** `core/Tiers.java`, `core/BloodRules.java`, `core/Durations.java`, `core/KillCooldowns.java`, plus tests in `src/test/java/dev/bloodbound/core/`.

**Interfaces (produces):**
- `record Tiers(int[] thresholds)`: `int tierFor(int blood)` (1-based), `int floorOf(int tier)`, `int maxTier()`, `int toNext(int blood)` (−1 at max tier).
- `record BloodRules(Tiers tiers, int killReward, int mobReward, double catchUpPerTier, double shatterFraction, double killerShare, double environmentalLoss)`:
  - `int killReward(int killerTier, int victimTier, double multiplier)`
  - `Shatter shatter(int victimBlood)` → `record Shatter(int lost, int killerGain)`
  - `int environmentalLoss(int blood)`
- `Durations.parse(String) -> OptionalLong` (millis; accepts `90m`, `1h30m`, `45s`, `2h`; rejects anything else). `Durations.format(long millis) -> String` ("1h 05m", "4m 09s").
- `KillCooldowns(long windowMillis, LongSupplier clock)`: `boolean tryRecord(UUID killer, UUID victim)` returns false if still on cooldown.

**Tests:**
- `tierFor(0)==1`, `tierFor(149)==1`, `tierFor(150)==2`, `tierFor(1400)==5`, `tierFor(99999)==5`. `toNext(100)==50`, `toNext(1400)==-1`.
- `killReward(1,3,1.0)==150` (100 + 2×25%), `killReward(3,1,1.0)==100`, `killReward(1,1,2.0)==200`.
- Shatter at blood 500 (tier 3, floor 400): 30% = 150, capped to one tier → may drop no lower than floor of tier 2 (150), so lost = 150 and killerGain = 75. Shatter at blood 1000 (tier 4, floor 800): 30% = 300, the cap allows dropping to 400 → lost 300, gain 150. Shatter at blood 0 → lost 0, gain 0.
- `environmentalLoss(500)==50`.
- `Durations.parse("90m")==5_400_000`, `parse("1h30m")==5_400_000`, `parse("5x")` empty, `parse("")` empty, `parse("0m")` empty.
- `KillCooldowns`: first kill true, second within the window false, after the window true, a different victim true.
- [ ] Write the tests and see them fail, then implement and see them pass with `./gradlew test`. Commit.

### Task 3: Config, messages, storage
**Files:** `config/Settings.java`, `config/Messages.java`, `data/PlayerData.java`, `data/PlayerStore.java`, `data/AltarStore.java`, `data/SessionStore.java`, `resources/config.yml`, `resources/messages.yml`
- `Settings.load(FileConfiguration)` builds `BloodRules`, `Tiers`, per-weapon numbers (`WeaponStats damagePerTier, activeCooldown, ultimateCooldown`), event defaults, recipes (`Map<WeaponType, List<ItemStack>>`).
- `Messages.send(Audience, String key, TagResolver...)`, `Messages.component(key, ...)`, prefix support.
- `PlayerData { UUID id; String name; WeaponType type (nullable); int blood; int weaponVersion; }`. `PlayerStore.get(UUID)`, `all()`, `saveAsync()`, `saveNow()`.
- `AltarStore`: `Set<BlockKey>` with `add/remove/contains/all`. `SessionStore`: `active`, `endsAt` (0 = open-ended).
- [ ] Build passes. Commit.

### Task 4: Session manager
**Files:** `session/SessionManager.java`
- `start(OptionalLong duration)`, `stop()`, `isActive()`, `remainingMillis()`, `bossBar` updated every second, title and sound on start and end, persisted via `SessionStore`, resumed on enable. `stop()` also calls `EventManager.stopCurrent()`.

### Task 5: Weapons and ownership
**Files:** `weapon/WeaponType.java` (enum of five: `CRIMSON_BLADE` NETHERITE_SWORD, `RAVAGER_AXE` NETHERITE_AXE, `TEMPEST_TRIDENT` TRIDENT, `EARTHSHAKER_MACE` MACE, `PHANTOM_BOW` BOW), `weapon/WeaponItems.java` (`create(PlayerData)`, `read(ItemStack) -> Optional<WeaponTag(UUID owner, WeaponType type, int version)>`, `isValidFor(ItemStack, PlayerData)`, `refreshLore`), `weapon/OwnershipListener.java` (cancels hits by non-owners, keeps the weapon on death and gives it back on respawn, cancels the hand swap).

### Task 6: Altars
**Files:** `altar/AltarItem.java`, `altar/AltarListener.java` (place/break gating, explosion/piston protection, interaction), `altar/AltarMenu.java` (InventoryHolder with 5 weapon slots showing cost lore), `altar/AltarCrafting.java` (`CraftResult tryCraft(Player, WeaponType)`: NOT_IN_SESSION, ALREADY_OWNS, MISSING_ITEMS, OK; consumes the items, bumps the version, gives the weapon), `altar/AltarEffects.java` (particles during a session; beam during Altar Surge).

### Task 7: Blood progression listener
**Files:** `blood/BloodService.java` (`addBlood(Player, int, String reason)` handles the tier-up title, sound and lore refresh; `setBlood`), `blood/BloodListener.java` (PlayerDeathEvent with killer → `KillCooldowns`, `BloodRules`; hostile mob kill → mob reward; environmental death → loss; all only during a session; event multiplier from `EventManager`).

### Task 8: Abilities
**Files:** `ability/AbilityManager.java` (cooldowns map, action bar every 10 ticks, swap-hand trigger: sneaking = ultimate, else active; tier gates: active ≥ 2, ultimate = 5; cooldown multiplier from Frenzy), `ability/WeaponKit.java` (interface: `onHit(EntityDamageByEntityEvent, ctx)`, `active(ctx)`, `ultimate(ctx)`, optional `onShoot/onProjectileHit/onLand`), and one kit per weapon: `CrimsonBladeKit`, `RavagerAxeKit`, `TempestTridentKit`, `EarthshakerMaceKit`, `PhantomBowKit`; `ability/AbilityListener.java` wires the events. All damage from abilities targets players and hostile mobs only, never the user, and uses `damage(amount, user)` so kills are credited.

### Task 9: Events
**Files:** `event/EventType.java` (BLOOD_MOON, BOUNTY, FRENZY, LAST_STAND, ALTAR_SURGE), `event/EventManager.java` (`start(type, durationMillis, Player bountyTarget)`, `stopCurrent()`, `bloodMultiplier()`, `cooldownMultiplier()`, `current()`), `event/EventListener.java` (Last Stand regen cancel and heal on kill; Bounty kill reward; bounty holder logout).

### Task 10: Commands
**Files:** `command/BloodboundCommand.java` (Brigadier tree registered through `LifecycleEvents.COMMANDS`): `stats [player]`, `top`, and with admin permission `session start [duration]|stop|status`, `event start <type> [duration] [target]|stop|list`, `altar give|list|remove`, `blood set|add <player> <amount>`, `reload`.
- [ ] `./gradlew build` succeeds, the jar contains `plugin.yml`, tests pass. Commit and push.

### Task 11: Smoke test on a real server (if reachable)
- [ ] Download a Paper 1.21.11 server jar, start it with the plugin, check the log for `Enabled Bloodbound` and no stack traces, then stop it.
