package dev.infuse.customs.sword;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * The five arcs: nine weapons each, in no particular order. A server runs through them one at a
 * time (see ArcService): each arc unlocks its weapons for forging and brings its own twist.
 */
public enum Arc {
    I("I", "Awakening", 0xECBA44,
            "Bounty Hunt", "Every ten minutes one player becomes the bounty: they glow and everyone is shown the way to them. Kill them for a reward, or survive five minutes and claim it yourself. Every kill heals the killer fully.",
            SwordType.MANTIS, SwordType.SHURIKEN, SwordType.KATANA, SwordType.SPLITTER, SwordType.CRIMSON, SwordType.MACE, SwordType.ECHO, SwordType.CANDLE, SwordType.FROST),

    II("II", "Ascension", 0x5CF2C2,
            "War of the Three Orders", "Everyone joins the Solar Order, the Lunar Covenant or the Astral Syndicate. Orders can't hurt their own, each has its own blessing and relic, and every kill scores for your order. The order with the most kills wins the arc.",
            SwordType.DEATHKNIGHT, SwordType.COSMIC, SwordType.VERDANT, SwordType.RIBBON, SwordType.SHADOW, SwordType.CHAKRAM, SwordType.WARGLAIVE, SwordType.MAUL, SwordType.STARS),

    III("III", "Dominion", 0x9B5DE5,
            "Spoils of War", "Anyone carrying a legendary weapon glows for all to see, and whoever kills a player takes every legendary weapon they carried.",
            SwordType.SOUL, SwordType.SCYTHE, SwordType.ASHURA, SwordType.TRIDENT, SwordType.STAFF, SwordType.RAPIER, SwordType.HAMMER, SwordType.KHOPESH, SwordType.CLAWS),

    IV("IV", "Cataclysm", 0xE5482C,
            "Collapse", "The world border closes in on spawn for the whole arc while meteors crash down around players. There is nowhere left to hide.",
            SwordType.VOID, SwordType.AXE, SwordType.CLAYMORE, SwordType.SPEAR, SwordType.CUTLASS, SwordType.SAWBLADE, SwordType.STORM, SwordType.PLAGUE, SwordType.GLAIVE),

    V("V", "Eternity", 0xDDE6FF,
            "Eternal Hearts", "The sun never rises again. Every player you kill gives you one of their hearts, up to ten extra; every death costs you one.",
            SwordType.MIRROR, SwordType.TIDE, SwordType.NEEDLE, SwordType.DRAGON, SwordType.DAGGERS, SwordType.HALBERD, SwordType.SOLAR, SwordType.PHOENIX, SwordType.CHRONO);

    private final String numeral;
    private final String title;
    private final TextColor color;
    private final String twist;
    private final String twistText;
    private final List<SwordType> members;

    Arc(String numeral, String title, int color, String twist, String twistText, SwordType... members) {
        this.numeral = numeral;
        this.title = title;
        this.color = TextColor.color(color);
        this.twist = twist;
        this.twistText = twistText;
        this.members = List.of(members);
    }

    /** The name of this arc's twist, e.g. "Bounty Hunt". */
    public String twist() {
        return twist;
    }

    /** What the twist does, in a sentence or two. */
    public String twistText() {
        return twistText;
    }

    /** The arc after this one, or null for the last. */
    public Arc next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    /** Finds an arc by number (1-5) or numeral ("III"). */
    public static Arc parse(String input) {
        if (input == null) {
            return null;
        }
        String in = input.trim().toUpperCase(java.util.Locale.ROOT);
        for (Arc arc : values()) {
            if (arc.name().equals(in) || String.valueOf(arc.ordinal() + 1).equals(in) || arc.title.toUpperCase(java.util.Locale.ROOT).equals(in)) {
                return arc;
            }
        }
        return null;
    }

    /** "Arc I". */
    public String label() {
        return "Arc " + numeral;
    }

    /** "Awakening". */
    public String title() {
        return title;
    }

    public TextColor color() {
        return color;
    }

    public List<SwordType> members() {
        return members;
    }

    public static Arc of(SwordType type) {
        for (Arc arc : values()) {
            if (arc.members.contains(type)) {
                return arc;
            }
        }
        throw new IllegalStateException(type + " is in no arc");
    }
}
