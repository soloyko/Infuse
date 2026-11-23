# Black Clover SMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Paper 1.21.11 plugin implementing the Black Clover SMP: unique grimoires crafted at libraries, PvP-only levelling per player per grimoire, 60 spells, the Demon Possessed, and an admin GUI.

**Architecture:** Game rules that need no server (level curve, kill XP, penalty, mana, repeat-kill window, combat tag, library placement, grimoire state machine) live in `core`/`grimoire` model classes. They have no Bukkit imports and are unit-tested with JUnit. Thin Bukkit services and listeners wire those rules to events. Spells are data plus a lambda each, built on a shared `Fx` toolkit, so 60 spells stay small.

**Tech Stack:** Java 21, Maven, `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` (provided), JUnit 5. No other runtime dependencies.

**Spec:** `docs/superpowers/specs/2026-11-23-black-clover-smp-design.md` (revision 2)

## Global Constraints

- Plugin name `BlackCloverSMP`, main class `dev.blackclover.smp.BlackCloverPlugin`, `api-version: '1.21'`.
- Base package `dev.blackclover.smp`; core rule classes import nothing from `org.bukkit`.
- Admin permission `blackclover.admin` (default op).
- Level cap 20; XP to go from L to L+1 = `100 + 25 × (L − 1)`; spells unlock at levels 1, 5, 10, 20; +1% spell damage per level above 1.
- Kill XP base 100; +10% per level victim is above (max +100%); −10% per level below (min 20%).
- Death penalty 15% of carried-grimoire XP, at most once per 5 min, none during grace.
- Repeat-kill window 10 min; kill-credit/combat-tag window 15 s.
- Max mana `100 + 4 × (level − 1)`, regen 4/s; Five-Leaf ×1.5 both; Anti-Magic uses no mana.
- Libraries: 13, ≥1000 blocks from 0,0, ≥50 inside border, ≥400 apart (relaxed if needed); recipe 1 BOOK + 16 LAPIS_LAZULI + 1 DIAMOND.
- Grimoire returns home after holder is offline 7 days; Demon coords broadcast every 5 min; Demon max health 40.
- All numbers above come from `config.yml` with these defaults.

## Review Focus

1. **Duplicated or stale grimoire items** (creative middle-click, dupes, items left after return-home/reset): any grimoire item whose instance id isn't the registry's current instance, or whose holder isn't the registry holder, is deleted on sight (join, pickup, periodic scan, chunk load). Test: `GrimoireRegistryTest.staleInstanceIsInvalidAfterReturnHome`.
2. **Right-click firing twice** (block + air packets, main + off hand): one cast per click. Debounce 150 ms per player and accept only `EquipmentSlot.HAND`. Test: `CastDebounceTest`.
3. **Too-small border for `/start`**: refuse with the minimum size instead of looping forever. Test: `LibraryPlacerTest.tooSmallBorderThrows` and `smallBorderRelaxesSpacing`.
4. **Player dies above the void or in lava holding a grimoire**: it must come back. Ground item is invulnerable; if the death location is below the world's min height, return home immediately. Test: `GrimoireRegistryTest.returnHomeFromGroundGoesToLibraryOrPool`, plus server check in Task 12.
5. **Level-down mid-selection / XP edge values** (0 XP, exactly on a threshold, beyond cap, negative input): levels and selection stay valid. Tests: `LevelCurveTest.thresholds`, `KillRewardsTest.penaltyNeverNegative`.

---

### Task 1: Project scaffold and core rules

**Files:**
- Create: `pom.xml`, `src/main/resources/plugin.yml`, `src/main/java/dev/blackclover/smp/BlackCloverPlugin.java` (empty enable/disable for now)
- Create: `src/main/java/dev/blackclover/smp/core/{LevelCurve,KillRewards,ManaMath,DurationParser,RepeatKillTracker,CombatTracker}.java`
- Test: `src/test/java/dev/blackclover/smp/core/*Test.java`

**Interfaces — Produces:**
- `record LevelCurve(int cap, int base, int step)`: `int xpToNext(int level)`, `int totalXpForLevel(int level)`, `int levelForXp(int xp)`, `int clampXp(int xp)` (0..totalXpForLevel(cap)). `static LevelCurve defaults()` = (20,100,25).
- `record KillRewards(int base, double perLevel, double maxBonus, double minFactor, double penaltyFraction)`: `int killXp(int killerLevel, int victimLevel)`, `int xpAfterDeath(int xp)` (loss floored). `static defaults()`.
- `record ManaMath(double base, double perLevel, double regenPerSecond, double fiveLeafMultiplier)`: `double maxMana(int level, boolean fiveLeaf)`, `double regenPerSecond(boolean fiveLeaf)`.
- `final class DurationParser`: `static OptionalLong parseSeconds(String)` accepting `90`, `45s`, `30m`, `2h`; rejects negatives/garbage.
- `class RepeatKillTracker(long windowMillis)`: `boolean tryRecord(UUID killer, UUID victim, long now)`. True means XP is allowed, and the kill is recorded.
- `class CombatTracker(long tagMillis)`: `void recordHit(UUID attacker, UUID victim, long now)`, `Optional<UUID> lastAttacker(UUID victim, long now)`, `boolean inCombat(UUID player, long now)`, `void clear(UUID player)`.

- [ ] **Step 1: Write failing tests**
  - `LevelCurveTest.thresholds`: `xpToNext(1)==100`, `xpToNext(2)==125`, `totalXpForLevel(1)==0`, `totalXpForLevel(2)==100`, `totalXpForLevel(20)==6175`, `levelForXp(0)==1`, `levelForXp(99)==1`, `levelForXp(100)==2`, `levelForXp(6175)==20`, `levelForXp(99999)==20`, `levelForXp(-5)==1`, `clampXp(99999)==6175`.
  - `KillRewardsTest`: `killXp(5,5)==100`, `killXp(5,7)==120`, `killXp(1,20)==200`, `killXp(10,5)==50`, `killXp(20,1)==20`, `xpAfterDeath(1000)==850`, `xpAfterDeath(6175)==5249`; `penaltyNeverNegative`: `xpAfterDeath(0)==0`, `xpAfterDeath(-10)==0`.
  - `ManaMathTest`: `maxMana(1,false)==100`, `maxMana(20,false)==176`, `maxMana(1,true)==150`, `regenPerSecond(true)==6`.
  - `DurationParserTest`: `"30m"→1800`, `"45s"→45`, `"2h"→7200`, `"90"→90`, `"0"→0`; `"abc"`, `"-5m"`, `""` → empty.
  - `RepeatKillTrackerTest`: first kill true; same pair at +9 min false; at +10 min true; A→B recorded doesn't block B→A or A→C.
  - `CombatTrackerTest`: `lastAttacker` present at +14 s, empty at +16 s; latest attacker wins; both attacker and victim `inCombat`.
- [ ] **Step 2:** `mvn -q test` → compilation failures.
- [ ] **Step 3:** Write `pom.xml` (paper repo `https://repo.papermc.io/repository/maven-public/`, release 21, JUnit 5 + surefire 3.x), `plugin.yml`, empty plugin class, and implement the six core classes.
- [ ] **Step 4:** `mvn -q test` → all pass; `mvn -q package` → `target/BlackCloverSMP-*.jar`.
- [ ] **Step 5:** Commit `feat: scaffold plugin and core progression rules`.

### Task 2: Grimoire model, player data, library placement

**Files:**
- Create: `src/main/java/dev/blackclover/smp/grimoire/{GrimoireType,GrimoireStatus,GrimoireState,GrimoireRegistry,BlockLoc}.java`
- Create: `src/main/java/dev/blackclover/smp/mage/PlayerData.java`
- Create: `src/main/java/dev/blackclover/smp/core/LibraryPlacer.java`
- Test: `GrimoireRegistryTest`, `PlayerDataTest`, `LibraryPlacerTest`

**Interfaces — Produces:**
- `enum GrimoireType` with 15 constants `FIRE, WATER, WIND, LIGHTNING, EARTH, PLANT, LIGHT, DARK, SPATIAL, GRAVITY, BLOOD, POISON, TIME, ANTI_MAGIC, FIVE_LEAF`; `String displayName()` ("Fire", …, "Anti-Magic", "Five-Leaf"), `int rgb()`, `boolean craftable()` (first 13), `boolean usesMana()` (false for ANTI_MAGIC), `static List<GrimoireType> craftables()`.
- `enum GrimoireStatus { LIBRARY, ADMIN_POOL, HELD, GROUND }`.
- `record BlockLoc(String world, double x, double y, double z)`.
- `record GrimoireState(GrimoireType type, GrimoireStatus status, UUID instance, UUID holder, UUID itemEntity, BlockLoc ground)`.
- `class GrimoireRegistry`: new registry puts craftables in LIBRARY, others in ADMIN_POOL, each with a random instance. Methods: `GrimoireState get(GrimoireType)`; `Optional<GrimoireType> heldBy(UUID player)`; `UUID craft(GrimoireType, UUID player)` (requires LIBRARY + craftable + player holds none, else `IllegalStateException`); `UUID giveFromPool(GrimoireType, UUID player)` (requires ADMIN_POOL); `void markHeld(GrimoireType, UUID player)`; `void markGround(GrimoireType, UUID itemEntity, BlockLoc loc)`; `UUID returnHome(GrimoireType)` (to LIBRARY if craftable else ADMIN_POOL, **new** instance, clears holder/ground); `boolean isCurrent(GrimoireType, UUID instance)`; `void restore(GrimoireState)` (for loading); `Collection<GrimoireState> all()`.
- `class PlayerData(UUID id, String name)`: `int xp(GrimoireType)`, `void setXp(GrimoireType, int)`, `Map<GrimoireType,Integer> xpView()`, `long lastPenaltyMillis()`/setter, `long lastSeenMillis()`/setter, `boolean canBePenalized(long now, long cooldownMillis)`.
- `final class LibraryPlacer`: `record Point(int x, int z)`; `static int minimumBorder(int minFromOrigin, int margin)` = `2 × (minFromOrigin + margin + 8)`; `static List<Point> place(int count, int borderSize, int minFromOrigin, int margin, int minSpacing, Random rnd, Collection<Point> avoid)`. It samples uniformly in the square `|x|,|z| ≤ border/2 − margin − 8`, rejects points within `minFromOrigin` of 0,0, and rejects points within `spacing` of placed or avoided points. After 500 failed tries it multiplies spacing by 0.8. Throws `IllegalArgumentException` if `borderSize < minimumBorder`.

- [ ] **Step 1: Write failing tests**
  - `GrimoireRegistryTest.initialStatuses`: FIRE is LIBRARY, ANTI_MAGIC and FIVE_LEAF are ADMIN_POOL.
  - `craftThenHeld`: `craft(FIRE,a)` → HELD by a, `heldBy(a)==FIRE`; a second `craft(FIRE,b)` throws; `craft(WATER,a)` throws (a already holds one).
  - `dropAndPickup`: `markGround(FIRE, e, loc)` → GROUND, `heldBy(a)` empty; `markHeld(FIRE,b)` → held by b.
  - `staleInstanceIsInvalidAfterReturnHome`: `i = craft(...)`; `returnHome(FIRE)`; `isCurrent(FIRE,i)==false`; status LIBRARY.
  - `returnHomeFromGroundGoesToLibraryOrPool`: FIVE_LEAF from GROUND → ADMIN_POOL; FIRE from GROUND → LIBRARY.
  - `giveFromPoolRequiresPool`: `giveFromPool(ANTI_MAGIC,a)` ok; again for b throws.
  - `PlayerDataTest`: default xp 0; set/get per type independent; `canBePenalized(now, 300000)` true initially, false 1 min after `setLastPenaltyMillis`, true after 5 min.
  - `LibraryPlacerTest.largeBorder`: `place(13, 6000, 1000, 50, 400, new Random(1), List.of())` → 13 points, each `hypot ≥ 1000`, each `|x|,|z| ≤ 2942`, pairwise distance ≥ 400.
  - `smallBorderRelaxesSpacing`: border `minimumBorder(1000,50)` (=2116) → still 13 points meeting distance and bounds rules.
  - `tooSmallBorderThrows`: border 2000 → `IllegalArgumentException`.
- [ ] **Step 2:** `mvn -q test` → fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `mvn -q test` → pass.
- [ ] **Step 5:** Commit `feat: grimoire registry, player data, library placement`.

### Task 3: Settings, persistence, plugin bootstrap

**Files:**
- Create: `src/main/resources/config.yml` (all global-constraint numbers, recipe, disabled-worlds list, messages prefix)
- Create: `config/Settings.java`, `data/DataStore.java`, `game/GameState.java`, `game/Library.java`
- Modify: `BlackCloverPlugin.java` (load settings + data, autosave every 5 min async write, save on disable)
- Test: `DataStoreRoundTripTest` (uses `YamlConfiguration` directly; no server needed)

**Interfaces — Produces:**
- `final class Settings`: `static Settings load(FileConfiguration)`; fields via accessors: `levelCurve()`, `killRewards()`, `manaMath()`, `repeatKillMillis()`, `combatTagMillis()`, `penaltyCooldownMillis()`, `combatLogKills()`, `Map<Material,Integer> recipe()`, `libraryMinFromOrigin()`, `libraryBorderMargin()`, `librarySpacing()`, `offlineReturnMillis()`, `demonHealth()`, `demonBroadcastMillis()`, `Set<String> disabledWorlds()`.
- `class GameState`: `boolean started`, `String world`, `int borderSize`, `long graceEndMillis`; `boolean isGrace(long now)`; `Map<GrimoireType, Library> libraries`.
- `record Library(GrimoireType type, String world, int altarX, int altarY, int altarZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ)`: `boolean contains(String world, int x, int y, int z)`, `boolean isAltar(String world, int x, int y, int z)`.
- `class DataStore(File file)`: `void load(GameState, GrimoireRegistry, Map<UUID,PlayerData>, AtomicReference<UUID> demon)`; `String serialize(...)` (main thread) + `void writeAsync(String)` / `void writeSync(String)`.

- [ ] **Step 1:** Write `DataStoreRoundTripTest`: build a state with started game, one library, FIRE held by `a`, FIVE_LEAF on ground at a BlockLoc, player `a` with FIRE xp 640 and lastPenalty 123, demon = `a`; serialize → load into fresh objects → every field equal.
- [ ] **Step 2:** `mvn -q test` → fails.
- [ ] **Step 3:** Implement Settings, GameState, Library, DataStore and plugin bootstrap.
- [ ] **Step 4:** `mvn -q test` → pass.
- [ ] **Step 5:** Commit `feat: settings and YAML persistence`.

### Task 4: Grimoire items and item rules

**Files:**
- Create: `grimoire/GrimoireItems.java`, `grimoire/GrimoireService.java`, `grimoire/GrimoireListener.java`
- Modify: `BlackCloverPlugin.java` (register)

**Interfaces — Consumes:** `GrimoireRegistry`, `PlayerData`, `Settings`, `GameState`.
**Produces:**
- `GrimoireItems`: `ItemStack create(GrimoireType, UUID instance, int holderLevel, String selectedSpellName)` (BOOK, glint, max stack size 1, coloured name "<Type> Grimoire", PDC keys `grimoire_type` and `grimoire_instance`); `Optional<GrimoireType> typeOf(ItemStack)`; `Optional<UUID> instanceOf(ItemStack)`; `boolean isGrimoire(ItemStack)`.
- `GrimoireService`: `Optional<GrimoireType> carried(Player)`; `boolean give(Player, GrimoireType)` (registry already updated by caller); `void returnHome(GrimoireType, String reason)` (removes ground entity / holder's item when online, calls `registry.returnHome`, notifies altar listeners through a `Consumer<GrimoireType> onReturnedHome` hook, ends possession if FIVE_LEAF); `void dropAt(GrimoireType, Location)` (spawns invulnerable, glowing, unlimited-lifetime item; below min height → `returnHome`); `void validate(Player)` (deletes invalid/stale grimoire items, keeps max one, and returns home a grimoire the registry says they hold but they don't have); `void refreshLore(Player)`.
- Listener rules (one handler each): cancel pickup of a second grimoire; mark held on valid pickup and refresh lore; cancel pickup by non-players and hoppers (`InventoryPickupItemEvent`); block moving grimoires into any non-player inventory (click incl. shift/number-key/bundle actions, drag); cancel item-frame/armour-stand use with a grimoire; `PlayerDropItemEvent` → `markGround` and apply ground-item properties; `ItemDespawnEvent` cancel; `EntityRemoveEvent` for a tracked ground item with cause other than `PICKUP`/`UNLOAD`/`PLAYER_QUIT` → `returnHome`; `EntitiesLoadEvent` deletes stale grimoire items; join → `validate`, update lastSeen; quit → update lastSeen; repeating task every 2 s: `validate` online players; every 10 min: return home grimoires whose holder's `lastSeen` is older than `offlineReturnMillis`.

- [ ] **Step 1:** Build (`mvn -q package`) after implementing, since these handlers need a live server. Behaviour is verified in Task 12.
- [ ] **Step 2:** Commit `feat: grimoire items, tracking and anti-hoarding rules`.

### Task 5: Game start, libraries and altars

**Files:**
- Create: `game/GameService.java`, `game/LibraryBuilder.java`, `game/AltarService.java`, `command/StartCommand.java`
- Modify: `plugin.yml` (`start` command, alias `bcstart`, permission `blackclover.admin`), `BlackCloverPlugin.java`

**Interfaces — Consumes:** `LibraryPlacer`, `DurationParser`, `GameState`, `Library`, `GrimoireRegistry`, `GrimoireService`.
**Produces:**
- `GameService`: `void start(CommandSender, World, int border, long graceSeconds)` (refuses if already started or border < `minimumBorder`; sets border centre 0,0 + size; sets `graceEndMillis`; builds libraries); `boolean isGrace()`; `void endGrace()`; `void reset()` (clears libraries/state, `returnHome` every grimoire so stale items get deleted on sight, ends possession); grace boss bar ticking every second plus the end title "PvP is now enabled".
- `LibraryBuilder`: `CompletableFuture<Optional<Library>> build(World, GrimoireType, LibraryPlacer.Point)`. Uses `getChunkAtAsync` and snaps the centre to `chunkX*16+8, chunkZ*16+8` so the 11×11 footprint stays in one chunk. Rejects liquid surfaces (returns empty, and the caller asks the placer for a replacement point). Clears 11×11×9 above, fills foundation pillars down to solid (max 8), builds a 9×9 stone-brick and oak building with bookshelf walls, glass, a door gap, lanterns, and a lectern altar at the centre.
- `AltarService`: TextDisplay per library above the altar (`view range` short, billboard CENTER) showing `"<Type> Grimoire"` + recipe, or the dormant text; `void refresh(GrimoireType)` (hooked to `onReturnedHome` and to crafting); right-click altar (main hand only, event cancelled) → craft if active, not carrying, ingredients present (consume, `registry.craft`, `give`, ceremony title "A grimoire has chosen you…" / "<Type> Grimoire", particles, sound); protection: cancel break/place/explode/piston/burn/liquid-flow inside library bounds, except `blackclover.admin` in creative.

- [ ] **Step 1:** Implement; `mvn -q package` passes. Live behaviour is checked in Task 12.
- [ ] **Step 2:** Commit `feat: /start, libraries, altar crafting and protection`.

### Task 6: Progression, PvP and death

**Files:**
- Create: `mage/MageService.java`, `mage/CombatListener.java`, `command/MagicCommand.java`
- Modify: `plugin.yml` (`magic`), `BlackCloverPlugin.java`

**Interfaces — Consumes:** core rules, `PlayerData`, `GrimoireService`, `GameService.isGrace()`.
**Produces:**
- `MageService`: `PlayerData data(UUID)` (create on demand); `int level(UUID, GrimoireType)`; `int carriedLevel(Player)` (0 if none); `void addXp(Player, GrimoireType, int)` and `void setXp(...)`, both clamped and with feedback (level-up title and sound; level-down message naming the newly locked spell via a `Function<GrimoireType, List<String>> spellNamesByUnlock` hook set by Task 7); `void applyDeathPenalty(Player, GrimoireType)` (respects grace and cooldown); `void onKill(Player killer, Player victim, GrimoireType victimType, int victimLevel)` (both must carry, repeat-kill check, chat line `+N magic XP for defeating <name> (Lv X)`); `List<Map.Entry<String,Integer>> top(int n)` for currently carried grimoires.
- `CombatListener`: MONITOR damage → `CombatTracker.recordHit` (resolves projectiles' shooters; ignores `Fx.probing`); HIGH damage → cancel player-vs-player during grace; `PlayerDeathEvent` order: carried type and victim level → move grimoire out of drops (grace and not demon: `getItemsToKeep`; else `dropAt` death location) → killer = `CombatTracker.lastAttacker` or `getKiller()` → `onKill` → `applyDeathPenalty` → demon end if victim is demon; `PlayerQuitEvent` while `inCombat`, not grace and `combatLogKills` → `setHealth(0)`.
- `/magic` (grimoire, level, XP to next, mana, spells with unlock levels, stored levels on other grimoires) and `/magic top`.

- [ ] **Step 1:** Implement; `mvn -q package` passes. Rules are already unit-tested in Tasks 1–2, and the event flow is checked in Task 12.
- [ ] **Step 2:** Commit `feat: PvP levelling, death penalty, combat logging, /magic`.

### Task 7: Casting engine

**Files:**
- Create: `magic/{Spell,SpellContext,SpellRegistry,CastingService,CastDebounce,StatusService,Fx,TempBlocks}.java`
- Test: `CastDebounceTest`, `SpellRegistryTest`

**Interfaces — Consumes:** `MageService`, `GrimoireService`, `GameService`, `Settings`.
**Produces:**
- `record Spell(String id, String name, int unlockLevel, double cost, double cooldownSeconds, double damage, SpellAction action)`; `interface SpellAction { boolean cast(SpellContext ctx); }`.
- `record SpellContext(Player caster, GrimoireType type, int level, Spell spell, Fx fx, StatusService status)`: `double dmg()` = `spell.damage × (1 + 0.01 × (level − 1))`; `double dmg(double base)` same scaling for secondary values.
- `class SpellRegistry`: `void register(GrimoireType, Spell...)` (exactly 4 per type, unlock levels 1/5/10/20); `List<Spell> spells(GrimoireType)`; `List<Spell> unlocked(GrimoireType, int level)`; `void applyConfig(ConfigurationSection)` (per-spell `cost`, `cooldown`, `damage` overrides; writes defaults back with `copyDefaults`).
- `class CastDebounce(long millis)`: `boolean tryAcquire(UUID, long now)`.
- `CastingService`: mana map, cooldown map, selected index; right-click → cast / shift-right-click → cycle; checks (disabled world, silenced, frozen, cooldown, mana unless `!usesMana`); Devil Union halves the cooldown it sets; `double mana(UUID)`, `void addMana(UUID,double)`; regen task every 10 ticks; action bar every 5 ticks while held: `<Type> Lv N | <Spell> | Mana a/b | ready` or `| 3.2s`.
- `StatusService`: `silence/freeze/root/immune(LivingEntity, ticks)`, `isSilenced/isFrozen/isRooted/isImmune(UUID)`; `addBuff(LivingEntity, String key, int ticks, PotionEffect...)`, `hasBuff(UUID,key)`, `stripBuffs(LivingEntity)`; counters `venomCharges`; `recordHistory` (5 s ring buffer of location and health, sampled every 5 ticks for TIME carriers); `Optional<Snapshot> history5s(UUID)`; `recall marks`. A move listener enforces frozen/rooted for players; mobs get Slowness 255.
- `Fx`: `static boolean probing`, `static boolean spellDamage`; `boolean canAffect(Player caster, Entity target)` (living, not caster, not armour stand, not creative/spectator, players: not grace + probe `EntityDamageByEntityEvent` not cancelled, and not spell-immune); `boolean hit(Player caster, LivingEntity t, double dmg)` (canAffect, Anti-Magic carrier takes ×0.7, `setNoDamageTicks(0)`, `t.damage(dmg, caster)` with `spellDamage` set); `void effect(Player caster, LivingEntity t, PotionEffectType, int ticks, int amp)`; `void projectile(Player caster, ProjectileSpec spec, Consumer<LivingEntity> onHit, Consumer<Location> onEnd)` (`ProjectileSpec(Particle particle, Object data, double speed, double range, double radius, double gravity, boolean pierce)`); `Optional<LivingEntity> beam(Player caster, double range, double width, Particle, Object data)`; `List<LivingEntity> line(Player caster, Location from, Vector dir, double range, double width, Particle, Object data)`; `List<LivingEntity> cone(Player caster, double range, double degrees)`; `List<LivingEntity> area(Player caster, Location centre, double radius)`; `Location targetPoint(Player, double range)`; `Optional<LivingEntity> targetEntity(Player, double range)`; `Optional<Location> safeSpot(Location near)`; `void repeat(int periodTicks, int times, IntConsumer step)`; particle helpers `ring`, `sphere`, `dust(int rgb, float size)`.
- `TempBlocks`: `void place(Location, Material, int ticks)` (air only, never in a library); `boolean isTemp(Block)`; `void restoreAll()` (on disable). The listener cancels drops when a temp block is broken.

- [ ] **Step 1:** Tests: `CastDebounceTest`: first true, +100 ms false, +200 ms true, per-player independent. `SpellRegistryTest`: registering 3 spells throws; `unlocked(FIRE, 1)` size 1, `(FIRE, 9)` size 2, `(FIRE, 10)` size 3, `(FIRE, 20)` size 4 (using dummy actions).
- [ ] **Step 2:** `mvn -q test` → fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `mvn -q test` → pass; `mvn -q package`.
- [ ] **Step 5:** Commit `feat: casting engine, mana, statuses and spell toolkit`.

### Task 8: Spells — Fire, Water, Wind, Lightning, Earth, Plant, Light

**Files:** Create `magic/spells/{Fire,Water,Wind,Lightning,Earth,Plant,Light}Spells.java`, each `static void register(SpellRegistry r)`.
**Consumes:** `Spell`, `SpellContext`, `Fx`, `StatusService`, `TempBlocks`.

Each spell exactly as listed in the spec's Spells section (names, unlock order, effects, base damage). Costs/cooldowns: Lv1 12–15 mana / 1.2–2 s; Lv5 15–30 / 5–20 s; Lv10 35–45 / 12–15 s; Lv20 80–90 / 60–75 s. Ids are `<type>_<snake_name>` (e.g. `fire_fireball`). Thunder God's melee bonus and Venom-style hooks go through a melee listener in `StatusService` that skips `Fx.spellDamage`.

- [ ] **Step 1:** Implement; `mvn -q package`; extend `SpellRegistryTest.allTypesRegistered` to assert all 15 types have 4 spells once Task 9 lands (start with these 7).
- [ ] **Step 2:** Commit `feat: elemental spells batch 1`.

### Task 9: Spells — Dark, Spatial, Gravity, Blood, Poison, Time, Anti-Magic, Five-Leaf

**Files:** Create `magic/spells/{Dark,Spatial,Gravity,Blood,Poison,Time,AntiMagic,FiveLeaf}Spells.java`.

As in the spec. Special rules: Blood Pact costs 4 health (cost 0 mana; refuses if health ≤ 4); Anti-Magic passive is in `Fx.hit`; Rewind uses `StatusService.history5s`; Time Stop uses `freeze`; Recall Gate's first cast sets a mark without a cooldown, and the recast consumes it; Banish and Swap use `safeSpot`; Devil Union sets buff key `devil_union`, which `CastingService` reads to halve cooldowns.

- [ ] **Step 1:** Implement; `SpellRegistryTest.allTypesRegistered` covers all 15 types with unlock levels [1,5,10,20] and unique ids; `mvn -q test` passes.
- [ ] **Step 2:** Commit `feat: spells batch 2, Anti-Magic and Five-Leaf`.

### Task 10: The Demon Possessed

**Files:** Create `demon/DemonService.java`.
**Consumes:** `GrimoireRegistry`, `GrimoireService`, `MageService`, `Settings`.
**Produces:** `Optional<UUID> demon()`; `String possess(Player)` (returns error text or null). It refuses if a demon exists or FIVE_LEAF is held by someone else or on the ground. Otherwise: return the target's carried grimoire home, `giveFromPool(FIVE_LEAF)`, set FIVE_LEAF XP to `totalXpForLevel(20)`, add a transient MAX_HEALTH modifier to reach `demonHealth`, heal, dark-red display/list name, server title. `void end(boolean returnFiveLeafToPool)` removes the modifier and name, and if true calls `returnHome(FIVE_LEAF)`. `void onJoin(Player)` re-applies effects. Repeating tasks: aura particles every 10 ticks; coordinates broadcast every `demonBroadcastMillis`.

- [ ] **Step 1:** Implement and wire into `CombatListener` (death → `end(false)` after the grimoire drop) and `GrimoireService.returnHome(FIVE_LEAF)` → `end(false)`; `mvn -q package`.
- [ ] **Step 2:** Commit `feat: Demon Possessed`.

### Task 11: Admin GUI and commands

**Files:** Create `gui/{Menu,MenuListener,AdminMenus}.java`, `command/AdminCommand.java`; modify `plugin.yml` (`bcadmin`).
**Produces:** `Menu implements InventoryHolder` with slot→`Consumer<InventoryClickEvent>` actions; all clicks in a `Menu` are cancelled. `AdminMenus.openMain/openPlayers/openPlayer/openGrimoires/openGame/openResetConfirm(Player)`, laid out as in the spec's "Admin GUI and commands" section. Commands: `possess <player>`, `unpossess`, `give <player> antimagic`, `take <player>`, `setlevel <player> <level>`, `addxp <player> <amount>`, `grace end`, `reset confirm`, `reload`; no args opens the GUI; tab completion for subcommands, players and levels 1–20.

- [ ] **Step 1:** Implement; `mvn -q package`.
- [ ] **Step 2:** Commit `feat: admin GUI and /bcadmin`.

### Task 12: Live verification and docs

**Files:** Create `README.md` (replace), `tools/e2e/` (bot script + `package.json`; not shipped in the jar).

- [ ] **Step 1:** Download Paper 1.21.11 into the scratchpad. Run it offline-mode with `level-type=flat` and the plugin. Expect `[BlackCloverSMP] Enabling` with no stack traces, `config.yml`/`data.yml` created, and a clean shutdown.
- [ ] **Step 2:** Console: `start 6000 1m` → 13 libraries built (log lists coordinates, each ≥1000 from 0,0); `start` again refuses; `bcadmin` commands respond.
- [ ] **Step 3:** Mineflayer bots A and B join (offline mode). Op A via console, teleport to a library and run the craft path: give the ingredients via console, A right-clicks the altar → A holds the grimoire; a second craft is refused. A casts Lv1 (right-click) → B takes damage after grace ends. B dies → grimoire on ground, XP awarded, penalty applied. Possess B via console → Five-Leaf in inventory; kill B → Five-Leaf dropped. Kill a bot standing above the void → grimoire returns to its library.
- [ ] **Step 4:** Fix anything found (systematic-debugging), re-run, commit `test: live server verification; docs`.
- [ ] **Step 5:** Push branch.
