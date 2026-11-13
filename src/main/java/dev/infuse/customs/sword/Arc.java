package dev.infuse.customs.sword;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * The five arcs the weapons are split into: nine weapons each, in no particular order. Menus, the
 * recipe book and {@code /customs list} show the weapons arc by arc.
 */
public enum Arc {
    I("I", "Awakening", 0xECBA44,
            SwordType.MANTIS, SwordType.SHURIKEN, SwordType.KATANA, SwordType.SPLITTER, SwordType.CRIMSON, SwordType.MACE, SwordType.ECHO, SwordType.CANDLE, SwordType.FROST),

    II("II", "Ascension", 0x5CF2C2,
            SwordType.DEATHKNIGHT, SwordType.COSMIC, SwordType.VERDANT, SwordType.RIBBON, SwordType.SHADOW, SwordType.CHAKRAM, SwordType.WARGLAIVE, SwordType.MAUL, SwordType.STARS),

    III("III", "Dominion", 0x9B5DE5,
            SwordType.SOUL, SwordType.SCYTHE, SwordType.ASHURA, SwordType.TRIDENT, SwordType.STAFF, SwordType.RAPIER, SwordType.HAMMER, SwordType.KHOPESH, SwordType.CLAWS),

    IV("IV", "Cataclysm", 0xE5482C,
            SwordType.VOID, SwordType.AXE, SwordType.CLAYMORE, SwordType.SPEAR, SwordType.CUTLASS, SwordType.SAWBLADE, SwordType.STORM, SwordType.PLAGUE, SwordType.GLAIVE),

    V("V", "Eternity", 0xDDE6FF,
            SwordType.MIRROR, SwordType.TIDE, SwordType.NEEDLE, SwordType.DRAGON, SwordType.DAGGERS, SwordType.HALBERD, SwordType.SOLAR, SwordType.PHOENIX, SwordType.CHRONO);

    private final String numeral;
    private final String title;
    private final TextColor color;
    private final List<SwordType> members;

    Arc(String numeral, String title, int color, SwordType... members) {
        this.numeral = numeral;
        this.title = title;
        this.color = TextColor.color(color);
        this.members = List.of(members);
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
