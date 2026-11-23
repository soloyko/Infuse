package dev.blackclover.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/** MiniMessage helpers. User-supplied text (player names) must go through {@link #arg}. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<#4caf63>☘</#4caf63> <dark_gray>»</dark_gray> <gray>";

    private Msg() {
    }

    /** Small capitals for labels and headings (tags are preserved). */
    public static String sc(String text) {
        return SmallCaps.of(text);
    }

    public static TagResolver arg(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static Component mm(String text, TagResolver... args) {
        return MM.deserialize(text, args);
    }

    public static void send(CommandSender to, String text, TagResolver... args) {
        to.sendMessage(mm(PREFIX + text, args));
    }

    public static void broadcast(String text, TagResolver... args) {
        Bukkit.getServer().sendMessage(mm(PREFIX + text, args));
    }

    public static void actionBar(Player p, String text, TagResolver... args) {
        p.sendActionBar(mm(text, args));
    }

    public static void title(Player p, String title, String subtitle, TagResolver... args) {
        p.showTitle(Title.title(mm(title, args), mm(subtitle, args),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
    }

    public static void titleAll(String title, String subtitle, TagResolver... args) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            title(p, title, subtitle, args);
        }
    }

    /** A {@code <key>...</key>} tag that colours its contents with {@code rgb}. */
    public static TagResolver color(String key, int rgb) {
        return TagResolver.resolver(key, net.kyori.adventure.text.minimessage.tag.Tag.styling(
                net.kyori.adventure.text.format.TextColor.color(rgb)));
    }

    public static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }
}
