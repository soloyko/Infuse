package dev.blackclover.smp.config;

import dev.blackclover.smp.core.KillRewards;
import dev.blackclover.smp.core.LevelCurve;
import dev.blackclover.smp.core.ManaMath;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Typed, immutable view of config.yml. */
public final class Settings {

    private static final long MINUTE = 60_000L;

    private final LevelCurve levelCurve;
    private final KillRewards killRewards;
    private final ManaMath manaMath;
    private final long repeatKillMillis;
    private final long combatTagMillis;
    private final long penaltyCooldownMillis;
    private final boolean combatLogKills;
    private final Map<Material, Integer> recipe;
    private final int libraryMinFromOrigin;
    private final int libraryBorderMargin;
    private final int librarySpacing;
    private final long offlineReturnMillis;
    private final double demonHealth;
    private final long demonBroadcastMillis;
    private final Set<String> disabledWorlds;
    private final boolean packEnabled;
    private final String packHost;
    private final int packPort;
    private final String packPublicAddress;
    private final String packUrl;
    private final boolean packRequired;
    private final String packPrompt;

    private Settings(FileConfiguration c) {
        levelCurve = new LevelCurve(
                c.getInt("progression.level-cap", 20),
                c.getInt("progression.xp-base", 100),
                c.getInt("progression.xp-step", 25));
        killRewards = new KillRewards(
                c.getInt("kills.base-xp", 100),
                c.getDouble("kills.bonus-per-level", 0.10),
                c.getDouble("kills.max-bonus", 1.0),
                c.getDouble("kills.min-factor", 0.20),
                c.getDouble("death.penalty-fraction", 0.15));
        manaMath = new ManaMath(
                c.getDouble("mana.base", 100),
                c.getDouble("mana.per-level", 4),
                c.getDouble("mana.regen-per-second", 4),
                c.getDouble("mana.five-leaf-multiplier", 1.5));
        repeatKillMillis = (long) (c.getDouble("kills.repeat-kill-minutes", 10) * MINUTE);
        combatTagMillis = (long) (c.getDouble("kills.combat-tag-seconds", 15) * 1000);
        penaltyCooldownMillis = (long) (c.getDouble("death.penalty-cooldown-minutes", 5) * MINUTE);
        combatLogKills = c.getBoolean("death.combat-log-kills", true);
        recipe = readRecipe(c.getConfigurationSection("libraries.recipe"));
        libraryMinFromOrigin = c.getInt("libraries.min-distance-from-origin", 1000);
        libraryBorderMargin = c.getInt("libraries.border-margin", 50);
        librarySpacing = c.getInt("libraries.min-spacing", 400);
        offlineReturnMillis = (long) (c.getDouble("grimoires.offline-return-days", 7) * 24 * 60 * MINUTE);
        demonHealth = c.getDouble("demon.max-health", 40);
        demonBroadcastMillis = (long) (c.getDouble("demon.broadcast-minutes", 5) * MINUTE);
        disabledWorlds = Collections.unmodifiableSet(new HashSet<>(c.getStringList("disabled-worlds")));
        packEnabled = c.getBoolean("resource-pack.enabled", true);
        packHost = c.getString("resource-pack.host", "auto").trim().toLowerCase(java.util.Locale.ROOT);
        packPort = c.getInt("resource-pack.port", 8163);
        packPublicAddress = c.getString("resource-pack.public-address", "").trim();
        packUrl = c.getString("resource-pack.url", "").trim();
        packRequired = c.getBoolean("resource-pack.required", false);
        packPrompt = c.getString("resource-pack.prompt", "<green>Black Clover <gray>3D grimoires and magic");
    }

    private static Map<Material, Integer> readRecipe(ConfigurationSection section) {
        Map<Material, Integer> result = new EnumMap<>(Material.class);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                int amount = section.getInt(key);
                if (m != null && amount > 0) {
                    result.put(m, amount);
                }
            }
        }
        if (result.isEmpty()) {
            result.put(Material.BOOK, 1);
            result.put(Material.LAPIS_LAZULI, 16);
            result.put(Material.DIAMOND, 1);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Settings load(FileConfiguration config) {
        return new Settings(config);
    }

    public LevelCurve levelCurve() { return levelCurve; }
    public KillRewards killRewards() { return killRewards; }
    public ManaMath manaMath() { return manaMath; }
    public long repeatKillMillis() { return repeatKillMillis; }
    public long combatTagMillis() { return combatTagMillis; }
    public long penaltyCooldownMillis() { return penaltyCooldownMillis; }
    public boolean combatLogKills() { return combatLogKills; }
    public Map<Material, Integer> recipe() { return recipe; }
    public int libraryMinFromOrigin() { return libraryMinFromOrigin; }
    public int libraryBorderMargin() { return libraryBorderMargin; }
    public int librarySpacing() { return librarySpacing; }
    public long offlineReturnMillis() { return offlineReturnMillis; }
    public double demonHealth() { return demonHealth; }
    public long demonBroadcastMillis() { return demonBroadcastMillis; }
    public Set<String> disabledWorlds() { return disabledWorlds; }
    public boolean packEnabled() { return packEnabled; }
    public String packHost() { return packHost; }
    public int packPort() { return packPort; }
    public String packPublicAddress() { return packPublicAddress; }
    public String packUrl() { return packUrl; }
    public boolean packRequired() { return packRequired; }
    public String packPrompt() { return packPrompt; }
}
