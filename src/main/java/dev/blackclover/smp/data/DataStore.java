package dev.blackclover.smp.data;

import dev.blackclover.smp.game.GameState;
import dev.blackclover.smp.game.Library;
import dev.blackclover.smp.grimoire.BlockLoc;
import dev.blackclover.smp.grimoire.GrimoireRegistry;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Saves and loads all plugin state to a single YAML file. */
public final class DataStore {

    private static final Logger LOG = Logger.getLogger("BlackCloverSMP");
    private final File file;

    public DataStore(File file) {
        this.file = file;
    }

    /** Builds the YAML text. Call on the main thread; it only reads in-memory state. */
    public String serialize(GameState game, GrimoireRegistry registry, Map<UUID, PlayerData> players, UUID demon) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("game.started", game.isStarted());
        y.set("game.world", game.world());
        y.set("game.border", game.borderSize());
        y.set("game.grace-end", game.graceEndMillis());
        for (Library lib : game.libraries().values()) {
            String p = "libraries." + lib.type().name() + ".";
            y.set(p + "world", lib.world());
            y.set(p + "altar", java.util.List.of(lib.altarX(), lib.altarY(), lib.altarZ()));
            y.set(p + "min", java.util.List.of(lib.minX(), lib.minY(), lib.minZ()));
            y.set(p + "max", java.util.List.of(lib.maxX(), lib.maxY(), lib.maxZ()));
        }
        for (GrimoireState s : registry.all()) {
            String p = "grimoires." + s.type().name() + ".";
            y.set(p + "status", s.status().name());
            y.set(p + "instance", s.instance().toString());
            if (s.holder() != null) {
                y.set(p + "holder", s.holder().toString());
            }
            if (s.itemEntity() != null) {
                y.set(p + "item-entity", s.itemEntity().toString());
            }
            if (s.ground() != null) {
                y.set(p + "ground.world", s.ground().world());
                y.set(p + "ground.x", s.ground().x());
                y.set(p + "ground.y", s.ground().y());
                y.set(p + "ground.z", s.ground().z());
            }
        }
        for (PlayerData pd : players.values()) {
            String p = "players." + pd.id() + ".";
            y.set(p + "name", pd.name());
            y.set(p + "last-penalty", pd.lastPenaltyMillis());
            y.set(p + "last-seen", pd.lastSeenMillis());
            for (Map.Entry<GrimoireType, Integer> e : pd.xpView().entrySet()) {
                y.set(p + "xp." + e.getKey().name(), e.getValue());
            }
        }
        y.set("demon", demon == null ? null : demon.toString());
        return y.saveToString();
    }

    /** Writes atomically (temp file then move). Safe to call from an async thread. */
    public synchronized void writeSync(String content) {
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            File tmp = new File(parent, file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    public void load(GameState game, GrimoireRegistry registry, Map<UUID, PlayerData> players, AtomicReference<UUID> demon) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        game.setStarted(y.getBoolean("game.started"));
        game.setWorld(y.getString("game.world"));
        game.setBorderSize(y.getInt("game.border"));
        game.setGraceEndMillis(y.getLong("game.grace-end"));

        ConfigurationSection libs = y.getConfigurationSection("libraries");
        if (libs != null) {
            for (String key : libs.getKeys(false)) {
                GrimoireType type = GrimoireType.valueOf(key);
                ConfigurationSection l = libs.getConfigurationSection(key);
                java.util.List<Integer> a = l.getIntegerList("altar");
                java.util.List<Integer> min = l.getIntegerList("min");
                java.util.List<Integer> max = l.getIntegerList("max");
                game.libraries().put(type, new Library(type, l.getString("world"),
                        a.get(0), a.get(1), a.get(2), min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2)));
            }
        }

        ConfigurationSection gs = y.getConfigurationSection("grimoires");
        if (gs != null) {
            for (String key : gs.getKeys(false)) {
                GrimoireType type = GrimoireType.valueOf(key);
                ConfigurationSection g = gs.getConfigurationSection(key);
                BlockLoc ground = null;
                if (g.isConfigurationSection("ground")) {
                    ground = new BlockLoc(g.getString("ground.world"), g.getDouble("ground.x"),
                            g.getDouble("ground.y"), g.getDouble("ground.z"));
                }
                registry.restore(new GrimoireState(type, GrimoireStatus.valueOf(g.getString("status")),
                        uuid(g.getString("instance")), uuid(g.getString("holder")), uuid(g.getString("item-entity")), ground));
            }
        }

        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection p = ps.getConfigurationSection(key);
                PlayerData pd = new PlayerData(UUID.fromString(key), p.getString("name", "?"));
                pd.setLastPenaltyMillis(p.getLong("last-penalty", pd.lastPenaltyMillis()));
                pd.setLastSeenMillis(p.getLong("last-seen"));
                ConfigurationSection xp = p.getConfigurationSection("xp");
                if (xp != null) {
                    for (String t : xp.getKeys(false)) {
                        pd.setXp(GrimoireType.valueOf(t), xp.getInt(t));
                    }
                }
                players.put(pd.id(), pd);
            }
        }
        demon.set(uuid(y.getString("demon")));
    }

    private static UUID uuid(String s) {
        return s == null ? null : UUID.fromString(s);
    }
}
