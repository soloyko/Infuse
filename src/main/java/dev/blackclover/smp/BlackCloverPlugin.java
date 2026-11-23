package dev.blackclover.smp;

import dev.blackclover.smp.command.MagicCommand;
import dev.blackclover.smp.command.StartCommand;
import dev.blackclover.smp.config.Settings;
import dev.blackclover.smp.data.DataStore;
import dev.blackclover.smp.game.AltarService;
import dev.blackclover.smp.game.GameService;
import dev.blackclover.smp.game.LibraryBuilder;
import dev.blackclover.smp.game.GameState;
import dev.blackclover.smp.grimoire.GrimoireItems;
import dev.blackclover.smp.grimoire.GrimoireListener;
import dev.blackclover.smp.grimoire.GrimoireRegistry;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.mage.CombatListener;
import dev.blackclover.smp.mage.MageService;
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
    private GrimoireService grimoires;
    private AltarService altars;
    private GameService gameService;
    private MageService mage;
    private CombatListener combatListener;
    private MagicCommand magicCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        store = new DataStore(new File(getDataFolder(), "data.yml"));
        store.load(game, registry, players, demon);

        grimoires = new GrimoireService(this, registry, new GrimoireItems(this));
        GrimoireListener grimoireListener = new GrimoireListener(this, grimoires);
        getServer().getPluginManager().registerEvents(grimoireListener, this);
        grimoireListener.startTasks();

        altars = new AltarService(this, grimoires);
        gameService = new GameService(this, grimoires, new LibraryBuilder(), altars);
        getServer().getPluginManager().registerEvents(altars, this);
        getServer().getPluginManager().registerEvents(gameService, this);
        altars.startTasks();
        gameService.startTasks();
        StartCommand start = new StartCommand(this, gameService);
        getCommand("start").setExecutor(start);
        getCommand("start").setTabCompleter(start);

        mage = new MageService(this, grimoires);
        combatListener = new CombatListener(this, mage, grimoires, gameService);
        getServer().getPluginManager().registerEvents(combatListener, this);
        magicCommand = new MagicCommand(this, mage);
        getCommand("magic").setExecutor(magicCommand);
        getCommand("magic").setTabCompleter(magicCommand);

        getServer().getScheduler().runTaskTimer(this, this::saveAsync, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        if (altars != null) {
            altars.removeAllDisplays();
        }
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
    public GrimoireService grimoires() { return grimoires; }
    public GameService gameService() { return gameService; }
    public AltarService altars() { return altars; }
    public MageService mage() { return mage; }
}
