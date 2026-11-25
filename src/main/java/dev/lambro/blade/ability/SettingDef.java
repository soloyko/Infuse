package dev.lambro.blade.ability;

import org.bukkit.Material;

/** A numeric, GUI-editable setting belonging to an ability. */
public record SettingDef(String key, String label, Material icon, double def, double step, double min, double max,
                         boolean integer, String unit) {

    public static SettingDef num(String key, String label, Material icon, double def, double step, double min, double max, String unit) {
        return new SettingDef(key, label, icon, def, step, min, max, false, unit);
    }

    public static SettingDef whole(String key, String label, Material icon, int def, int step, int min, int max, String unit) {
        return new SettingDef(key, label, icon, def, step, min, max, true, unit);
    }

    public double clamp(double v) {
        double c = Math.max(min, Math.min(max, v));
        if (integer) return Math.round(c);
        return Math.round(c * 100.0) / 100.0;
    }
}
