package dev.infuse.customs.sword;

import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The five arcs: nine weapons each, in no particular order. A server runs through them one at a
 * time (see ArcService): each arc unlocks its weapons for forging. Arcs I and II also have a PvP
 * twist, which runs during admin-started sessions.
 */
public enum Arc {
    I("I", "Awakening", 0xECBA44,
            "Bounty Hunt", "During a session, one player at a time becomes the bounty: they glow and everyone is shown the way to them. Kill them for a reward, or survive the hunt and claim it yourself. Every kill heals the killer fully.",
            SwordType.MANTIS, SwordType.SHURIKEN, SwordType.KATANA, SwordType.SPLITTER, SwordType.CRIMSON, SwordType.MACE, SwordType.ECHO, SwordType.CANDLE, SwordType.FROST),

    II("II", "Ascension", 0x5CF2C2,
            "Faction War", "When a session starts, three random players become the leaders of the Order of the Blade, the House of the Hammer and the Covenant of the Spear, carrying the Hero Sword, Mjolnir and the Fallen God's Spear, and two random players join each. Factions can't hurt their own and every kill scores. Anyone unsworn who falls to a faction's weapon joins that faction. The top faction wins when the session ends.",
            SwordType.DEATHKNIGHT, SwordType.COSMIC, SwordType.VERDANT, SwordType.RIBBON, SwordType.SHADOW, SwordType.CHAKRAM, SwordType.WARGLAIVE, SwordType.MAUL, SwordType.STARS),

    III("III", "Dominion", 0x9B5DE5, null, null,
            SwordType.SOUL, SwordType.SCYTHE, SwordType.ASHURA, SwordType.TRIDENT, SwordType.STAFF, SwordType.RAPIER, SwordType.HAMMER, SwordType.KHOPESH, SwordType.CLAWS),

    IV("IV", "Cataclysm", 0xE5482C, null, null,
            SwordType.VOID, SwordType.AXE, SwordType.CLAYMORE, SwordType.SPEAR, SwordType.CUTLASS, SwordType.SAWBLADE, SwordType.STORM, SwordType.PLAGUE, SwordType.GLAIVE),

    V("V", "Eternity", 0xDDE6FF, null, null,
            SwordType.MIRROR, SwordType.TIDE, SwordType.NEEDLE, SwordType.DRAGON, SwordType.DAGGERS, SwordType.HALBERD, SwordType.SOLAR, SwordType.PHOENIX, SwordType.CHRONO);

    private final String numeral;
    private final String title;
    private final TextColor color;
    private final @Nullable String twist;
    private final @Nullable String twistText;
    private final List<SwordType> members;

    Arc(String numeral, String title, int color, @Nullable String twist, @Nullable String twistText, SwordType... members) {
        this.numeral = numeral;
        this.title = title;
        this.color = TextColor.color(color);
        this.twist = twist;
        this.twistText = twistText;
        this.members = List.of(members);
    }

    /** The name of this arc's twist, e.g. "Bounty Hunt", or null if it has none. */
    public @Nullable String twist() {
        return twist;
    }

    /** What the twist does, in a sentence or two, or null if it has none. */
    public @Nullable String twistText() {
        return twistText;
    }

    public boolean hasTwist() {
        return twist != null;
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
        return null;
    }
}
