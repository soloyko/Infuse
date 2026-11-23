package dev.blackclover.smp.pack;

import dev.blackclover.smp.grimoire.GrimoireType;

import java.util.Locale;

/** custom_model_data strings that the resource pack's item definitions dispatch on. */
public final class PackModels {

    public static final String LIGHT_SWORD = "blackclover:light_sword";
    public static final String ROCK = "blackclover:rock";
    public static final String DARK_ORB = "blackclover:dark_orb";
    public static final String DEMON_SLAYER = "blackclover:demon_slayer";

    private PackModels() {
    }

    public static String grimoireKey(GrimoireType type) {
        return "blackclover:" + type.name().toLowerCase(Locale.ROOT);
    }
}
