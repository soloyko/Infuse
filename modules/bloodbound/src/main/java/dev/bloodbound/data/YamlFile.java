package dev.bloodbound.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Small helper that writes YAML atomically, optionally off the main thread. */
public final class YamlFile {
    private final Plugin plugin;
    private final File file;

    public YamlFile(Plugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
    }

    public YamlConfiguration read() {
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Serializes on the calling thread, writes on an async thread. */
    public void writeAsync(YamlConfiguration yaml) {
        String data = yaml.saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    public void writeNow(YamlConfiguration yaml) {
        write(yaml.saveToString());
    }

    private synchronized void write(String data) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + file.getName() + ": " + e.getMessage());
        }
    }
}
