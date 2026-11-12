package me.infuse.data;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import me.infuse.effect.Equipped;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Stores per-player effect data (players/<uuid>.yml) and global data (data.yml: craft counts, rituals). */
public final class DataManager {
    private final InfusePlugin plugin;
    private final File playerDir;
    private final File globalFile;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<EffectType, Integer> craftCounts = new EnumMap<>(EffectType.class);
    private YamlConfiguration global;

    public DataManager(InfusePlugin plugin) {
        this.plugin = plugin;
        this.playerDir = new File(plugin.getDataFolder(), "players");
        this.globalFile = new File(plugin.getDataFolder(), "data.yml");
        //noinspection ResultOfMethodCallIgnored
        playerDir.mkdirs();
        loadGlobal();
    }

    // ---------------------------------------------------------------- players

    public PlayerData get(Player player) {
        return get(player.getUniqueId());
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, this::load);
    }

    private PlayerData load(UUID id) {
        PlayerData data = new PlayerData(id);
        File file = new File(playerDir, id + ".yml");
        if (!file.exists()) return data;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.set(PlayerData.PRIMARY, readSlot(yml.getConfigurationSection("primary")));
        data.set(PlayerData.SECONDARY, readSlot(yml.getConfigurationSection("secondary")));
        for (String s : yml.getStringList("trusted")) {
            try {
                data.trusted().add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.setOffhandControls(yml.getBoolean("offhand-controls", plugin.getConfig().getBoolean("controls.default-offhand", true)));
        for (int slot = 0; slot < 2; slot++) {
            data.setCooldownRaw(slot, yml.getLong("cooldown." + slot + ".end"), yml.getLong("cooldown." + slot + ".total"));
        }
        return data;
    }

    private Equipped readSlot(ConfigurationSection section) {
        if (section == null) return null;
        EffectType type = EffectType.fromId(section.getString("type"));
        if (type == null) return null;
        return new Equipped(type, section.getBoolean("augmented"));
    }

    public void save(PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        writeSlot(yml, "primary", data.get(PlayerData.PRIMARY));
        writeSlot(yml, "secondary", data.get(PlayerData.SECONDARY));
        yml.set("offhand-controls", data.offhandControls());
        yml.set("trusted", data.trusted().stream().map(UUID::toString).toList());
        for (int slot = 0; slot < 2; slot++) {
            yml.set("cooldown." + slot + ".end", data.cooldownEndRaw(slot));
            yml.set("cooldown." + slot + ".total", data.cooldownTotal(slot));
        }
        try {
            yml.save(new File(playerDir, data.id() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save player data for " + data.id(), e);
        }
    }

    private void writeSlot(YamlConfiguration yml, String path, Equipped e) {
        if (e == null) return;
        yml.set(path + ".type", e.type().id());
        yml.set(path + ".augmented", e.augmented());
    }

    public void unload(UUID id) {
        PlayerData data = players.remove(id);
        if (data != null) save(data);
    }

    public void saveAll() {
        players.values().forEach(this::save);
        saveGlobal();
    }

    // ---------------------------------------------------------------- global

    private void loadGlobal() {
        global = YamlConfiguration.loadConfiguration(globalFile);
        craftCounts.clear();
        ConfigurationSection crafts = global.getConfigurationSection("crafts");
        if (crafts != null) {
            for (String key : crafts.getKeys(false)) {
                EffectType type = EffectType.fromId(key);
                if (type != null) craftCounts.put(type, crafts.getInt(key));
            }
        }
    }

    public YamlConfiguration global() {
        return global;
    }

    public int craftCount(EffectType type) {
        return craftCounts.getOrDefault(type, 0);
    }

    public void setCraftCount(EffectType type, int count) {
        craftCounts.put(type, count);
        global.set("crafts." + type.id(), count);
        saveGlobal();
    }

    public void saveGlobal() {
        for (Map.Entry<EffectType, Integer> e : craftCounts.entrySet()) {
            global.set("crafts." + e.getKey().id(), e.getValue());
        }
        try {
            global.save(globalFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
        }
    }
}
