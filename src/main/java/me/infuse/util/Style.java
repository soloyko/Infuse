package me.infuse.util;

import me.infuse.effect.EffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

/** Text styling: gradients and small caps ("ᴛɪɴʏ ᴄᴀᴘꜱ"). */
public final class Style {
    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private static boolean smallCaps = true;
    private static boolean gradients = true;
    private static double lighten = 0.55;

    private Style() {}

    public static void load(FileConfiguration cfg) {
        smallCaps = cfg.getBoolean("style.small-caps", true);
        gradients = cfg.getBoolean("style.gradients", true);
        lighten = cfg.getDouble("style.gradient-lighten", 0.55);
    }

    /** Converts letters to small caps when enabled. */
    public static String caps(String s) {
        if (!smallCaps || s == null) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = NORMAL.indexOf(Character.toLowerCase(c));
            sb.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return sb.toString();
    }

    /** Always converts, regardless of config (used by the {@code <caps>} message tag). */
    public static String forceCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = NORMAL.indexOf(Character.toLowerCase(c));
            sb.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return sb.toString();
    }

    /** Text in an effect-coloured gradient (or flat colour when gradients are off). */
    public static Component colored(String text, int rgb) {
        String esc = MiniMessage.miniMessage().escapeTags(text);
        String mm = gradients
                ? "<gradient:" + Msg.hex(rgb) + ":" + Msg.hex(Fx.lighten(rgb, lighten)) + ">" + esc + "</gradient>"
                : "<" + Msg.hex(rgb) + ">" + esc + "</" + Msg.hex(rgb) + ">";
        return MiniMessage.miniMessage().deserialize(mm).decoration(TextDecoration.ITALIC, false);
    }

    /** "ᴀᴜɢᴍᴇɴᴛᴇᴅ ᴛʜɪᴇꜰ ᴇꜰꜰᴇᴄᴛ" in a gradient; augmented names are bold. */
    public static Component effectName(EffectType type, boolean augmented) {
        String raw = (augmented ? "Augmented " : "") + type.displayName() + " Effect";
        Component c = colored(caps(raw), type.rgb());
        return augmented ? c.decorate(TextDecoration.BOLD) : c;
    }

    /** Just the effect's name, e.g. "ᴛʜɪᴇꜰ". */
    public static Component shortName(EffectType type) {
        return colored(caps(type.displayName()), type.rgb());
    }
}
