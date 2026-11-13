package dev.infuse.opswords.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Small text helpers shared by the plugin. */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    public static final Component PREFIX = MM.deserialize(
            "<dark_gray>[<gradient:#ECBA44:#C4415B>OP Swords</gradient>]</dark_gray> ");

    private Text() {
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static void send(CommandSender to, String miniMessage) {
        to.sendMessage(PREFIX.append(mm(miniMessage)));
    }

    public static void send(CommandSender to, Component message) {
        to.sendMessage(PREFIX.append(message));
    }

    /** A component with italics explicitly disabled (item names and lore are italic by default). */
    public static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Colours each character of {@code text} along a two-stop gradient. */
    public static Component gradient(String text, TextColor from, TextColor to) {
        int[] cps = text.codePoints().toArray();
        Component out = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < cps.length; i++) {
            float t = cps.length <= 1 ? 0f : (float) i / (cps.length - 1);
            out = out.append(Component.text(new String(cps, i, 1), TextColor.lerp(t, from, to)));
        }
        return out;
    }

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    /** "Legendary" → "ʟᴇɢᴇɴᴅᴀʀʏ": tiny capitals for labels and accents. Digits and symbols are kept. */
    public static String smallCaps(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            char lower = Character.toLowerCase(c);
            out.append(lower >= 'a' && lower <= 'z' ? SMALL_CAPS.charAt(lower - 'a') : c);
        }
        return out.toString();
    }

    /** {@link #smallCaps(String)} as a non-italic component. */
    public static Component caps(String text, TextColor color) {
        return plain(smallCaps(text), color);
    }

    /** A sword's name ("🐉 Wyrmsbane") in its gradient colours. */
    public static Component sword(dev.infuse.opswords.sword.SwordType type) {
        return gradient(type.displayName(), type.color(), type.accent());
    }

    /** Greedy word wrap for tooltip lines. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static String seconds(long millis) {
        double s = millis / 1000.0;
        return s >= 10 ? String.format("%.0fs", Math.ceil(s)) : String.format("%.1fs", s);
    }

    public static final TextColor GRAY = NamedTextColor.GRAY;
    public static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
}
