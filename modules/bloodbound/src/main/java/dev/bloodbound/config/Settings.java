package dev.bloodbound.config;

import dev.bloodbound.core.BloodRules;
import dev.bloodbound.core.Durations;
import dev.bloodbound.core.Tiers;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Immutable snapshot of config.yml. Rebuilt on /bb reload. */
public record Settings(
        BloodRules rules,
        long repeatKillCooldownMillis,
        int activeTier,
        int ultimateTier,
        long defaultEventMillis,
        double bloodMoonMultiplier,
        int bountyKillBonus,
        int bountySurvivePerMinute,
        long bountyBroadcastMillis,
        double frenzyCooldownMultiplier,
        double surgeRadius,
        int surgeBlood,
        long surgeIntervalMillis,
        boolean announceForges,
        Map<WeaponType, WeaponSettings> weapons
) {
    public Tiers tiers() {
        return rules.tiers();
    }

    public WeaponSettings weapon(WeaponType type) {
        return weapons.get(type);
    }

    public static Settings load(FileConfiguration c, Logger log) {
        List<Integer> thresholdList = c.getIntegerList("blood.tiers");
        int[] thresholds = thresholdList.isEmpty()
                ? new int[]{0, 150, 400, 800, 1400}
                : thresholdList.stream().mapToInt(Integer::intValue).toArray();
        Tiers tiers;
        try {
            tiers = new Tiers(thresholds);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid blood.tiers (" + e.getMessage() + "); using defaults.");
            tiers = new Tiers(new int[]{0, 150, 400, 800, 1400});
        }

        BloodRules rules = new BloodRules(
                tiers,
                c.getInt("blood.kill-reward", 100),
                c.getInt("blood.mob-reward", 2),
                c.getDouble("blood.catch-up-per-tier", 0.25),
                c.getDouble("blood.shatter-fraction", 0.30),
                c.getDouble("blood.killer-share", 0.50),
                c.getDouble("blood.environmental-loss", 0.10));

        Map<WeaponType, WeaponSettings> weapons = new EnumMap<>(WeaponType.class);
        for (WeaponType type : WeaponType.values()) {
            ConfigurationSection s = c.getConfigurationSection("weapons." + type.configKey());
            if (s == null) {
                log.warning("Missing config section weapons." + type.configKey() + "; using built-in values.");
            }
            weapons.put(type, new WeaponSettings(
                    s == null ? 1.0 : s.getDouble("damage-per-tier", 1.0),
                    durationOr(s == null ? null : s.getString("active-cooldown"), 12_000),
                    durationOr(s == null ? null : s.getString("ultimate-cooldown"), 90_000),
                    recipe(s == null ? null : s.getConfigurationSection("recipe"), type, log),
                    s));
        }

        return new Settings(
                rules,
                durationOr(c.getString("blood.repeat-kill-cooldown"), 600_000),
                Math.max(1, c.getInt("abilities.active-tier", 2)),
                Math.max(1, c.getInt("abilities.ultimate-tier", 5)),
                durationOr(c.getString("events.default-duration"), 600_000),
                c.getDouble("events.blood-moon.multiplier", 2.0),
                c.getInt("events.bounty.kill-bonus", 300),
                c.getInt("events.bounty.survive-bonus-per-minute", 50),
                durationOr(c.getString("events.bounty.broadcast-interval"), 60_000),
                c.getDouble("events.frenzy.cooldown-multiplier", 0.5),
                c.getDouble("events.altar-surge.radius", 8.0),
                c.getInt("events.altar-surge.blood", 5),
                durationOr(c.getString("events.altar-surge.interval"), 5_000),
                c.getBoolean("altars.announce-forges", true),
                weapons);
    }

    private static Map<Material, Integer> recipe(ConfigurationSection s, WeaponType type, Logger log) {
        Map<Material, Integer> recipe = new LinkedHashMap<>();
        if (s == null) {
            recipe.put(type.material(), 1);
            return recipe;
        }
        for (String key : s.getKeys(false)) {
            Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            int amount = s.getInt(key);
            if (m == null || !m.isItem() || amount <= 0) {
                log.warning("Ignoring invalid recipe entry " + key + " for " + type.configKey());
                continue;
            }
            recipe.put(m, amount);
        }
        return recipe;
    }

    static long durationOr(String raw, long fallback) {
        return Durations.parse(raw).orElse(fallback);
    }
}
