package dev.bloodbound.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** altars.yml: the set of registered altar blocks. */
public final class AltarStore {
    private final YamlFile file;
    private final Set<BlockKey> altars = new LinkedHashSet<>();

    public AltarStore(Plugin plugin) {
        this.file = new YamlFile(plugin, "altars.yml");
    }

    public void load() {
        altars.clear();
        for (String raw : file.read().getStringList("altars")) {
            BlockKey key = BlockKey.parse(raw);
            if (key != null) altars.add(key);
        }
    }

    public boolean contains(BlockKey key) {
        return altars.contains(key);
    }

    public void add(BlockKey key) {
        if (altars.add(key)) save();
    }

    public void remove(BlockKey key) {
        if (altars.remove(key)) save();
    }

    public Set<BlockKey> all() {
        return Collections.unmodifiableSet(altars);
    }

    private void save() {
        file.writeAsync(serialize());
    }

    public void saveNow() {
        file.writeNow(serialize());
    }

    private YamlConfiguration serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (BlockKey key : altars) list.add(key.serialize());
        yaml.set("altars", list);
        return yaml;
    }
}
