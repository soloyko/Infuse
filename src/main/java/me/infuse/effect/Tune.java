package me.infuse.effect;

import me.infuse.InfusePlugin;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Effect values that augmented effects can override: when the player's effect is augmented,
 * {@code augmented.<effect>.<key>} in config.yml replaces {@code effects.<effect>.<key>}.
 */
public final class Tune {
    private static InfusePlugin plugin;

    /** Shown on augmented bottles (and the preview) under "Augmented bonus". */
    public static final Map<EffectType, List<String>> BONUS = Map.ofEntries(
            Map.entry(EffectType.EMERALD, List.of("Mobs you kill drop double EXP")),
            Map.entry(EffectType.ENDER, List.of("Glow radius 24 blocks", "Teleport range 25 blocks")),
            Map.entry(EffectType.FEATHER, List.of("Slam deals 8 hearts")),
            Map.entry(EffectType.FIRE, List.of("Spark reaches 8 blocks and burns for 8s")),
            Map.entry(EffectType.FROST, List.of("Frozen enemies take 20% more damage from you")),
            Map.entry(EffectType.HASTE, List.of("Spark also gives Haste III")),
            Map.entry(EffectType.HEART, List.of("+7 hearts instead of +5")),
            Map.entry(EffectType.INVISIBILITY, List.of("Ring is 4 blocks wide", "Bow shots blind for 8s")),
            Map.entry(EffectType.OCEAN, List.of("Bigger, stronger Whirlhole")),
            Map.entry(EffectType.REGENERATION, List.of("Lifesteal heals 50% of damage")),
            Map.entry(EffectType.SPEED, List.of("Base Speed II and up to 5 stacks")),
            Map.entry(EffectType.STRENGTH, List.of("Auto-crits deal 25% more damage")),
            Map.entry(EffectType.THUNDER, List.of("Lightning chains to 5 targets")),
            Map.entry(EffectType.APOPHIS, List.of("Blinding lasts 5s")),
            Map.entry(EffectType.THIEF, List.of("Backstab +100% damage", "Silence lasts 12s")),
            Map.entry(EffectType.WEAVING, List.of("Web Snare reaches 9 blocks")),
            Map.entry(EffectType.OOZING, List.of("30% less knockback", "3 slimes burst out")),
            Map.entry(EffectType.RESISTANCE, List.of("Reflect 50% of melee damage", "6 absorption hearts every 10 hits")));

    private Tune() {}

    public static void setup(InfusePlugin p) { plugin = p; }

    /** e.g. "Sparks last 25% longer, 50% shorter cooldown" from the current config. */
    public static String summary() {
        if (plugin == null) return "Sparks last longer, shorter cooldown";
        int longer = (int) Math.round((plugin.getConfig().getDouble("durations.augmented-multiplier", 1.25) - 1) * 100);
        int shorter = (int) Math.round((1 - plugin.getConfig().getDouble("cooldowns.augmented-multiplier", 0.5)) * 100);
        return "Sparks last " + longer + "% longer, " + shorter + "% shorter cooldown";
    }

    private static String augPath(String path) {
        return "augmented." + path.substring("effects.".length());
    }

    private static boolean augmented(Player p, String path) {
        if (p == null || !path.startsWith("effects.")) return false;
        String[] parts = path.split("\\.");
        EffectType type = EffectType.fromId(parts.length > 1 ? parts[1] : null);
        return type != null && plugin.data().get(p).isAugmented(type) && plugin.getConfig().contains(augPath(path));
    }

    public static double d(Player p, String path, double def) {
        return augmented(p, path) ? plugin.getConfig().getDouble(augPath(path)) : plugin.getConfig().getDouble(path, def);
    }

    public static int i(Player p, String path, int def) {
        return augmented(p, path) ? plugin.getConfig().getInt(augPath(path)) : plugin.getConfig().getInt(path, def);
    }
}
