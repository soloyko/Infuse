package dev.bloodbound.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** session.yml: whether a session is running and when it ends (0 = open-ended). */
public final class SessionStore {
    private final YamlFile file;
    public boolean active;
    public long startedAt;
    public long endsAt;

    public SessionStore(Plugin plugin) {
        this.file = new YamlFile(plugin, "session.yml");
    }

    public void load() {
        YamlConfiguration yaml = file.read();
        active = yaml.getBoolean("active");
        startedAt = yaml.getLong("started-at");
        endsAt = yaml.getLong("ends-at");
    }

    public void saveAsync() {
        file.writeAsync(serialize());
    }

    public void saveNow() {
        file.writeNow(serialize());
    }

    private YamlConfiguration serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", active);
        yaml.set("started-at", startedAt);
        yaml.set("ends-at", endsAt);
        return yaml;
    }
}
