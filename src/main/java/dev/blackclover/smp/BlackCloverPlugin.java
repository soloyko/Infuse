package dev.blackclover.smp;

import dev.blackclover.smp.config.Settings;
import dev.blackclover.smp.data.DataStore;
import dev.blackclover.smp.game.GameState;
import dev.blackclover.smp.grimoire.GrimoireRegistry;
import dev.blackclover.smp.mage.PlayerData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class BlackCloverPlugin extends JavaPlugin {

    private static final long AUTOSAVE_TICKS = 20L * 60 * 5;

    private Settings settings;
    private final GameState game = new GameState();
    private final GrimoireRegistry registry = new GrimoireRegistry();
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final AtomicReference<UUID> demon = new AtomicReference<>();
    private DataStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        store = new DataStore(new File(getDataFolder(), "data.yml"));
        store.load(game, registry, players, demon);

        getServer().getScheduler().runTaskTimer(this, this::saveAsync, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.writeSync(snapshot());
        }
    }

    private String snapshot() {
        return store.serialize(game, registry, players, demon.get());
    }

    public void saveAsync() {
        String content = snapshot();
        getServer().getScheduler().runTaskAsynchronously(this, () -> store.writeSync(content));
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
    }

    public Settings settings() { return settings; }
    public GameState game() { return game; }
    public GrimoireRegistry registry() { return registry; }
    public Map<UUID, PlayerData> players() { return players; }
    public AtomicReference<UUID> demonRef() { return demon; }
}
