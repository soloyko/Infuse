package dev.blackclover.smp.grimoire;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The 15 unique grimoires. The first 13 are crafted at libraries. */
public enum GrimoireType {
    FIRE("Fire", 0xFF5522, true,
            "Flames that answer only to their master's will."),
    WATER("Water", 0x3399FF, true,
            "Tides that shield as fiercely as they strike."),
    WIND("Wind", 0x88FFAA, true,
            "Blades of air too swift for any eye to follow."),
    LIGHTNING("Lightning", 0xFFEE33, true,
            "Thunder that lands before its sound arrives."),
    EARTH("Earth", 0xA0703C, true,
            "The weight of mountains, bent to a single hand."),
    PLANT("Plant", 0x33BB33, true,
            "Roots and thorns that grow from one quiet wish."),
    LIGHT("Light", 0xFFF0B0, true,
            "The fastest magic there is. It waits for no one."),
    DARK("Dark", 0x8844DD, true,
            "A darkness deep enough to cut the world in two."),
    SPATIAL("Spatial", 0x22CCCC, true,
            "Distance means nothing to the one who holds space."),
    GRAVITY("Gravity", 0x6666EE, true,
            "The pull of the earth itself, yours to command."),
    BLOOD("Blood", 0xCC1133, true,
            "Power paid for in the mage's own lifeblood."),
    POISON("Poison", 0x99DD00, true,
            "A slow and certain end carried on every breath."),
    TIME("Time", 0xDDCC88, true,
            "Every second belongs to whoever opens this book."),
    ANTI_MAGIC("Anti-Magic", 0x777777, false,
            "A tattered grimoire that rejects magic itself."),
    FIVE_LEAF("Five-Leaf", 0xAA0000, false,
            "Born the day a devil claimed a mortal heart.");

    private static final List<GrimoireType> CRAFTABLES =
            Arrays.stream(values()).filter(GrimoireType::craftable).toList();

    private final String displayName;
    private final int rgb;
    private final boolean craftable;
    private final String bio;

    GrimoireType(String displayName, int rgb, boolean craftable, String bio) {
        this.displayName = displayName;
        this.rgb = rgb;
        this.craftable = craftable;
        this.bio = bio;
    }

    /** One line of flavour text shown on the grimoire. */
    public String bio() {
        return bio;
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
