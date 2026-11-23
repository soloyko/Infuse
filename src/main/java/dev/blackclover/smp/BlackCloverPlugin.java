package dev.blackclover.smp;

import dev.blackclover.smp.command.AdminActions;
import dev.blackclover.smp.command.AdminCommand;
import dev.blackclover.smp.command.MagicCommand;
import dev.blackclover.smp.command.StartCommand;
import dev.blackclover.smp.config.Settings;
import dev.blackclover.smp.data.DataStore;
import dev.blackclover.smp.demon.DemonService;
import dev.blackclover.smp.game.AltarService;
import dev.blackclover.smp.game.GameService;
import dev.blackclover.smp.game.LibraryBuilder;
import dev.blackclover.smp.game.GameState;
import dev.blackclover.smp.gui.AdminMenus;
import dev.blackclover.smp.gui.MenuListener;
import dev.blackclover.smp.grimoire.GrimoireItems;
import dev.blackclover.smp.grimoire.GrimoireListener;
import dev.blackclover.smp.grimoire.GrimoireRegistry;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.mage.CombatListener;
import dev.blackclover.smp.mage.MageService;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.magic.CastingService;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.SpellRegistry;
import dev.blackclover.smp.magic.StatusService;
import dev.blackclover.smp.magic.TempBlocks;
import dev.blackclover.smp.magic.spells.AllSpells;
import dev.blackclover.smp.pack.ResourcePackService;
import dev.blackclover.smp.squad.SquadRoster;
import dev.blackclover.smp.squad.SquadService;
import org.bukkit.entity.Player;
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
    private final java.util.concurrent.atomic.AtomicLong saveVersion = new java.util.concurrent.atomic.AtomicLong();
    private GrimoireService grimoires;
    private AltarService altars;
    private GameService gameService;
    private MageService mage;
    private CombatListener combatListener;
    private MagicCommand magicCommand;
    private final SpellRegistry spells = new SpellRegistry();
    private StatusService status;
    private TempBlocks tempBlocks;
    private CastingService casting;
    private DemonService demonService;
    private ResourcePackService pack;
    private dev.blackclover.smp.magic.GrimoireHover hover;
    private SquadRoster roster;
    private SquadService squads;
    private dev.blackclover.smp.hud.HudService hud;
    private dev.blackclover.smp.gui.SquadMenu squadMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        store = new DataStore(new File(getDataFolder(), "data.yml"));
        roster = new SquadRoster(settings.squadMaxMembers(), settings.squadInviteMillis());
        store.load(game, registry, players, demon, roster);
        squads = new SquadService(this, roster);
        getServer().getPluginManager().registerEvents(squads, this);
        squadMenu = new dev.blackclover.smp.gui.SquadMenu(this, squads);
        dev.blackclover.smp.command.SquadCommand squadCommand = new dev.blackclover.smp.command.SquadCommand(squads, squadMenu);
        for (String cmd : new String[]{"squad", "sc"}) {
            getCommand(cmd).setExecutor(squadCommand);
            getCommand(cmd).setTabCompleter(squadCommand);
        }

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

        status = new StatusService(this);
        tempBlocks = new TempBlocks(this, b -> altars.libraryAt(b).isPresent());
        Fx fx = new Fx(this, status, tempBlocks);
        AllSpells.register(spells);
        spells.applyConfig(getConfig());
        saveConfig();
        casting = new CastingService(this, spells, mage, grimoires, status, fx);
        getServer().getPluginManager().registerEvents(status, this);
        getServer().getPluginManager().registerEvents(tempBlocks, this);
        getServer().getPluginManager().registerEvents(casting, this);
        status.startTasks();
        casting.startTasks();
        mage.setSpellNames((type, slot) -> {
            java.util.List<Spell> list = spells.spells(type);
            return slot < list.size() ? list.get(slot).name() : "spell " + (slot + 1);
        });
        mage.onLevelChange(casting::onLevelChanged);
        magicCommand.setDetails(casting::describe);
        demonService = new DemonService(this, grimoires, mage);
        getServer().getPluginManager().registerEvents(demonService, this);
        combatListener.setOnDemonDeath(demonService::onDeath);
        demonService.startTasks();

        dev.blackclover.smp.gui.SpellbookMenu spellbook = new dev.blackclover.smp.gui.SpellbookMenu(this);
        casting.setOpenBook(spellbook::open);
        magicCommand.setOpenBook(spellbook::open);

        hover = new dev.blackclover.smp.magic.GrimoireHover(this);
        getServer().getPluginManager().registerEvents(hover, this);
        hover.startTasks();
        casting.setOnCast(hover::onCast);

        hud = new dev.blackclover.smp.hud.HudService(this);
        getServer().getPluginManager().registerEvents(hud, this);
        hud.startTasks();

        pack = new ResourcePackService(this);
        pack.enable();
        getServer().getPluginManager().registerEvents(pack, this);

        AdminActions adminActions = new AdminActions(this);
        AdminCommand admin = new AdminCommand(this, adminActions, new AdminMenus(this, adminActions));
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getCommand("bcadmin").setExecutor(admin);
        getCommand("bcadmin").setTabCompleter(admin);

        grimoires.setHolderInfo(new GrimoireService.HolderInfo() {
            @Override
            public int level(Player player, dev.blackclover.smp.grimoire.GrimoireType type) {
                return mage.level(player.getUniqueId(), type);
            }

            @Override
            public String selectedSpell(Player player, dev.blackclover.smp.grimoire.GrimoireType type) {
                return casting.selectedSpell(player, type).map(Spell::name).orElse(null);
            }
        });

        getServer().getScheduler().runTaskTimer(this, this::saveAsync, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        if (pack != null) {
            pack.disable();
        }
        if (hover != null) {
            hover.removeAll();
        }
        if (hud != null) {
            hud.removeAll();
        }
        if (altars != null) {
            altars.removeAllDisplays();
        }
        if (tempBlocks != null) {
            tempBlocks.restoreAll();
        }
        if (store != null) {
            store.write(snapshot(), saveVersion.incrementAndGet());
        }
    }

    private String snapshot() {
        return store.serialize(game, registry, players, demon.get(), roster);
    }

    public void saveAsync() {
        String content = snapshot();
        long version = saveVersion.incrementAndGet();
        getServer().getScheduler().runTaskAsynchronously(this, () -> store.write(content, version));
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
        spells.applyConfig(getConfig());
        saveConfig();
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
    public SpellRegistry spells() { return spells; }
    public CastingService casting() { return casting; }
    public StatusService status() { return status; }
    public DemonService demon() { return demonService; }
    public ResourcePackService pack() { return pack; }
    public dev.blackclover.smp.magic.GrimoireHover hover() { return hover; }
    public SquadService squads() { return squads; }
    public dev.blackclover.smp.hud.HudService hud() { return hud; }
    public dev.blackclover.smp.gui.SquadMenu squadMenu() { return squadMenu; }
}
