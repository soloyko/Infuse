package dev.bloodbound.data;

import dev.bloodbound.weapon.WeaponType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** players.yml: UUID -> name, weapon type, blood, weapon version. */
public final class PlayerStore {
    private final YamlFile file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private boolean dirty;

    public PlayerStore(Plugin plugin) {
        this.file = new YamlFile(plugin, "players.yml");
    }

    public void load() {
        players.clear();
        YamlConfiguration yaml = file.read();
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            PlayerData data = new PlayerData(id, s.getString("name", "unknown"));
            data.type(WeaponType.fromKey(s.getString("weapon")).orElse(null));
            data.blood(s.getInt("blood"));
            data.weaponVersion(s.getInt("version"));
            players.put(id, data);
        }
    }

    public PlayerData get(Player player) {
        PlayerData data = players.computeIfAbsent(player.getUniqueId(), id -> {
            dirty = true;
            return new PlayerData(id, player.getName());
        });
        if (!player.getName().equals(data.name())) {
            data.name(player.getName());
            dirty = true;
        }
        return data;
    }

    public Optional<PlayerData> find(UUID id) {
        return Optional.ofNullable(players.get(id));
    }

    public Optional<PlayerData> findByName(String name) {
        return players.values().stream().filter(d -> d.name().equalsIgnoreCase(name)).findFirst();
    }

    public Collection<PlayerData> all() {
        return Collections.unmodifiableCollection(players.values());
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveAsync() {
        if (!dirty) return;
        dirty = false;
        file.writeAsync(serialize());
    }

    public void saveNow() {
        dirty = false;
        file.writeNow(serialize());
    }

    private YamlConfiguration serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerData d : players.values()) {
            String path = "players." + d.id();
            yaml.set(path + ".name", d.name());
            yaml.set(path + ".weapon", d.type() == null ? null : d.type().key());
            yaml.set(path + ".blood", d.blood());
            yaml.set(path + ".version", d.weaponVersion());
        }
        return yaml;
    }
}
