package dev.infuse.opswords.manager;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Remembers who forged each sword, for legendary mode (crafting.unique-swords), where every
 * sword can only be crafted once per server. Stored in plugins/OPSwords/data.yml.
 */
public final class ForgeRegistry {

    public record Forged(UUID by, String name, long at) {
    }

    private final OPSwordsPlugin plugin;
    private final File file;
    private final Map<SwordType, Forged> forged = new EnumMap<>(SwordType.class);

    public ForgeRegistry(OPSwordsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        forged.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("forged");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            SwordType type = SwordType.byId(key);
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (type == null || entry == null) {
                continue;
            }
            try {
                forged.put(type, new Forged(UUID.fromString(entry.getString("by", "")),
                        entry.getString("name", "?"), entry.getLong("at")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring bad entry for " + key + " in data.yml");
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(java.util.List.of(
                "Swords forged in legendary mode (crafting.unique-swords).",
                "Use /opswords unforge <sword> instead of editing this by hand."));
        for (Map.Entry<SwordType, Forged> e : forged.entrySet()) {
            String path = "forged." + e.getKey().id();
            data.set(path + ".by", e.getValue().by().toString());
            data.set(path + ".name", e.getValue().name());
            data.set(path + ".at", e.getValue().at());
        }
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", ex);
        }
    }

    public @Nullable Forged get(SwordType type) {
        return forged.get(type);
    }

    public boolean isForged(SwordType type) {
        return forged.containsKey(type);
    }

    public void record(SwordType type, Player player) {
        forged.put(type, new Forged(player.getUniqueId(), player.getName(), System.currentTimeMillis()));
        save();
    }

    /** Lets the sword be crafted again. Returns false if it wasn't forged. */
    public boolean clear(SwordType type) {
        if (forged.remove(type) == null) {
            return false;
        }
        save();
        return true;
    }
}
