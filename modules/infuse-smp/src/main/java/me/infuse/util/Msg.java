package me.infuse.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Messages come from messages.yml (MiniMessage). Every message can use {@code <prefix>} and
 * {@code <caps>text</caps>} (small caps), plus the placeholders documented in messages.yml.
 */
public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static YamlConfiguration messages = new YamlConfiguration();
    private static YamlConfiguration defaults = new YamlConfiguration();

    private Msg() {}

    public static void load(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
        var in = plugin.getResource("messages.yml");
        if (in != null) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }
    }

    private static TagResolver base() {
        return TagResolver.resolver(
                Placeholder.parsed("prefix", applyCaps(messages.getString("prefix", ""))),
                TagResolver.standard());
    }

    /** Raw string for a key ("" when the key is set to empty, which disables the message). */
    public static String raw(String key) {
        return messages.getString(key, key);
    }

    public static Component get(String key, TagResolver... resolvers) {
        return parse(raw(key), resolvers);
    }

    public static Component parse(String mini, TagResolver... resolvers) {
        return MM.deserialize(applyCaps(mini), TagResolver.resolver(base(), TagResolver.resolver(resolvers)));
    }

    /** Sends a message by key. Empty messages are not sent. */
    public static void send(CommandSender to, String key, TagResolver... resolvers) {
        String s = raw(key);
        if (s == null || s.isEmpty()) return;
        to.sendMessage(parse(s, resolvers));
    }

    public static void broadcast(String key, TagResolver... resolvers) {
        String s = raw(key);
        if (s == null || s.isEmpty()) return;
        Bukkit.broadcast(parse(s, resolvers));
    }

    // ------------------------------------------------------------------ helpers

    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Replaces &lt;caps&gt;...&lt;/caps&gt; spans with small caps before parsing. */
    private static String applyCaps(String s) {
        int start;
        while ((start = s.indexOf("<caps>")) >= 0) {
            int end = s.indexOf("</caps>", start);
            if (end < 0) break;
            String inner = s.substring(start + 6, end);
            StringBuilder out = new StringBuilder();
            boolean inTag = false;
            for (char c : inner.toCharArray()) {
                if (c == '<') inTag = true;
                out.append(inTag ? String.valueOf(c) : Style.forceCaps(String.valueOf(c)));
                if (c == '>') inTag = false;
            }
            s = s.substring(0, start) + out + s.substring(end + 7);
        }
        return s;
    }

    public static String esc(String s) { return MM.escapeTags(s); }

    public static String hex(int rgb) { return String.format("#%06x", rgb & 0xFFFFFF); }
}
