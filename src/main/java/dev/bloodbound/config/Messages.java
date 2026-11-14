package dev.bloodbound.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Loads messages.yml and renders MiniMessage strings with placeholders. */
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private YamlConfiguration yaml = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default messages: " + e.getMessage());
        }
    }

    public String raw(String key) {
        return yaml.getString(key, key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return render(raw(key), resolvers);
    }

    /** Like {@link #get} but without italics, for item names and lore. */
    public Component item(String key, TagResolver... resolvers) {
        return get(key, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    public List<Component> itemLines(String key, TagResolver... resolvers) {
        List<Component> lines = new ArrayList<>();
        for (String line : yaml.getStringList(key)) {
            lines.add(render(line, resolvers).decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }

    public Component render(String miniMessage, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(
                TagResolver.resolver(Placeholder.parsed("prefix", yaml.getString("prefix", ""))),
                TagResolver.resolver(resolvers));
        return MM.deserialize(miniMessage, all);
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) return;
        audience.sendMessage(render(raw, resolvers));
    }

    public void actionBar(Player player, String key, TagResolver... resolvers) {
        player.sendActionBar(get(key, resolvers));
    }

    public void title(Audience audience, String titleKey, String subtitleKey, TagResolver... resolvers) {
        audience.showTitle(Title.title(get(titleKey, resolvers), get(subtitleKey, resolvers),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
    }

    // Placeholder helpers.
    public static TagResolver text(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver comp(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
