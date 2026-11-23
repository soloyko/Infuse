package dev.blackclover.smp.grimoire;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The 15 unique grimoires. The first 13 are crafted at libraries. */
public enum GrimoireType {
    FIRE("Fire", 0xFF5522, true),
    WATER("Water", 0x3399FF, true),
    WIND("Wind", 0x88FFAA, true),
    LIGHTNING("Lightning", 0xFFEE33, true),
    EARTH("Earth", 0xA0703C, true),
    PLANT("Plant", 0x33BB33, true),
    LIGHT("Light", 0xFFF0B0, true),
    DARK("Dark", 0x8844DD, true),
    SPATIAL("Spatial", 0x22CCCC, true),
    GRAVITY("Gravity", 0x6666EE, true),
    BLOOD("Blood", 0xCC1133, true),
    POISON("Poison", 0x99DD00, true),
    TIME("Time", 0xDDCC88, true),
    ANTI_MAGIC("Anti-Magic", 0x777777, false),
    FIVE_LEAF("Five-Leaf", 0xAA0000, false);

    private static final List<GrimoireType> CRAFTABLES =
            Arrays.stream(values()).filter(GrimoireType::craftable).toList();

    private final String displayName;
    private final int rgb;
    private final boolean craftable;

    GrimoireType(String displayName, int rgb, boolean craftable) {
        this.displayName = displayName;
        this.rgb = rgb;
        this.craftable = craftable;
    }

    public String displayName() {
        return displayName;
    }

    public int rgb() {
        return rgb;
    }

    /** True for grimoires that have a library and altar. */
    public boolean craftable() {
        return craftable;
    }

    public boolean usesMana() {
        return this != ANTI_MAGIC;
    }

    public static List<GrimoireType> craftables() {
        return CRAFTABLES;
    }

    /** Lenient lookup: accepts {@code anti_magic}, {@code anti-magic}, {@code antimagic}, {@code fiveleaf}. */
    public static Optional<GrimoireType> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String key = input.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        for (GrimoireType t : values()) {
            if (t.name().replace("_", "").equals(key)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
