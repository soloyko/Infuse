package dev.lambro.blade.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Theme palette + MiniMessage helpers. Everything here is blue &amp; black. */
public final class Text {

    // Palette (hex strings usable inside MiniMessage tags)
    public static final String ICE = "#9FE8FF";
    public static final String CYAN = "#5FCBFF";
    public static final String BLUE = "#2F7BFF";
    public static final String ROYAL = "#1F4FD8";
    public static final String DEEP = "#16245C";
    public static final String STEEL = "#5C6B85";
    public static final String SHADOW = "#2A3142";
    public static final String WHITE = "#E6F1FF";

    /** Signature gradient used for headers and the blade name. */
    public static final String GRADIENT = "<gradient:" + ICE + ":" + BLUE + ":" + DEEP + ">";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    /** Parses MiniMessage and strips the default lore/name italics. */
    public static Component mm(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Safe placeholder: inserts plain text without parsing tags inside it. */
    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static String tc(String s) {
        return TinyCaps.of(s);
    }

    /** Small "▰▰▰▱▱" progress bar. */
    public static String bar(double progress, int length, String full, String empty) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * length);
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(full).append(">");
        for (int i = 0; i < length; i++) {
            if (i == filled) sb.append("<").append(empty).append(">");
            sb.append(i < filled ? "▰" : "▱");
        }
        return sb.toString();
    }

    public static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
