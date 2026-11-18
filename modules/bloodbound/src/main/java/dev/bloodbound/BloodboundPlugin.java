package dev.bloodbound;

import dev.bloodbound.ability.AbilityListener;
import dev.bloodbound.ability.AbilityManager;
import dev.bloodbound.altar.AltarCrafting;
import dev.bloodbound.altar.AltarEffects;
import dev.bloodbound.altar.AltarItem;
import dev.bloodbound.altar.AltarListener;
import dev.bloodbound.blood.BloodListener;
import dev.bloodbound.blood.BloodService;
import dev.bloodbound.command.BloodboundCommand;
import dev.bloodbound.config.Messages;
import dev.bloodbound.config.Settings;
import dev.bloodbound.core.KillCooldowns;
import dev.bloodbound.data.AltarStore;
import dev.bloodbound.data.PlayerStore;
import dev.bloodbound.data.SessionStore;
import dev.bloodbound.event.EventListener;
import dev.bloodbound.event.EventManager;
import dev.bloodbound.session.SessionManager;
import dev.bloodbound.util.Keys;
import dev.bloodbound.weapon.OwnershipListener;
import dev.bloodbound.weapon.WeaponItems;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Wires every feature together. Holds no game logic itself. */
public final class BloodboundPlugin extends JavaPlugin {
    private static final long AUTOSAVE_TICKS = 20L * 60 * 5;

    private Settings settings;
    private Messages messages;
    private Keys keys;
    private PlayerStore players;
    private AltarStore altars;
    private SessionStore sessionStore;
    private KillCooldowns killCooldowns;
    private WeaponItems weapons;
    private AltarItem altarItem;
    private BloodService blood;
    private EventManager events;
    private SessionManager sessions;
    private AbilityManager abilities;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        loadSettings();

        keys = new Keys(this);
        players = new PlayerStore(this);
        players.load();
        altars = new AltarStore(this);
        altars.load();
        sessionStore = new SessionStore(this);

        weapons = new WeaponItems(this);
        altarItem = new AltarItem(this);
        blood = new BloodService(this);
        events = new EventManager(this);
        sessions = new SessionManager(this, sessionStore);
        abilities = new AbilityManager(this);

        for (Listener listener : List.of(
                sessions,
                new AltarListener(this, altarItem, new AltarCrafting(this)),
                new OwnershipListener(this),
                new BloodListener(this),
                new AbilityListener(this),
                new EventListener(this))) {
            getServer().getPluginManager().registerEvents(listener, this);
        }

        BloodboundCommand command = new BloodboundCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build().build(),
                        "Bloodbound weapons, sessions and events", List.of("bb")));

        getServer().getScheduler().runTaskTimer(this, new AltarEffects(this), 20L, 4L);
        getServer().getScheduler().runTaskTimer(this, abilities, 20L, 10L);
        getServer().getScheduler().runTaskTimer(this, players::saveAsync, AUTOSAVE_TICKS, AUTOSAVE_TICKS);

        sessions.resume();
        getLogger().info("Bloodbound enabled with " + altars.all().size() + " altar(s).");
    }

    @Override
    public void onDisable() {
        if (events != null) events.stopCurrent(false);
        if (sessions != null) sessions.shutdown();
        if (abilities != null) abilities.clearAll();
        if (players != null) players.saveNow();
        if (altars != null) altars.saveNow();
    }

    public void reload() {
        reloadConfig();
        loadSettings();
    }

    private void loadSettings() {
        messages.load();
        settings = Settings.load(getConfig(), getLogger());
        killCooldowns = new KillCooldowns(settings.repeatKillCooldownMillis(), System::currentTimeMillis);
    }

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public Keys keys() { return keys; }
    public PlayerStore players() { return players; }
    public AltarStore altars() { return altars; }
    public KillCooldowns killCooldowns() { return killCooldowns; }
    public WeaponItems weapons() { return weapons; }
    public AltarItem altarItem() { return altarItem; }
    public BloodService blood() { return blood; }
    public EventManager events() { return events; }
    public SessionManager sessions() { return sessions; }
    public AbilityManager abilities() { return abilities; }
}
