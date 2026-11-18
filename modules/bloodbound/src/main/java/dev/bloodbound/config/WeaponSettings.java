package dev.bloodbound.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/** Per-weapon balance values. Kit-specific numbers are read from {@link #params()}. */
public record WeaponSettings(
        double damagePerTier,
        long activeCooldownMillis,
        long ultimateCooldownMillis,
        Map<Material, Integer> recipe,
        ConfigurationSection params
) {
    public double num(String key, double fallback) {
        return params == null ? fallback : params.getDouble(key, fallback);
    }

    public int integer(String key, int fallback) {
        return params == null ? fallback : params.getInt(key, fallback);
    }

    public long millis(String key, long fallback) {
        return params == null ? fallback : Settings.durationOr(params.getString(key), fallback);
    }
}
