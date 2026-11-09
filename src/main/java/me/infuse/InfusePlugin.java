package me.infuse;

import me.infuse.command.Commands;
import me.infuse.command.InfuseGui;
import me.infuse.craft.CraftingListener;
import me.infuse.craft.RecipeManager;
import me.infuse.craft.RitualManager;
import me.infuse.data.DataManager;
import me.infuse.effect.EffectManager;
import me.infuse.effect.ItemFactory;
import me.infuse.effect.SparkManager;
import me.infuse.listener.CombatListener;
import me.infuse.listener.PlayerListener;
import me.infuse.util.Msg;
import me.infuse.util.Status;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfusePlugin extends JavaPlugin {
    private DataManager data;
    private ItemFactory items;
    private EffectManager effects;
    private SparkManager sparks;
    private RecipeManager recipes;
    private RitualManager rituals;
    private InfuseGui gui;
    private CraftingListener crafting;
    private me.infuse.effect.Resonance resonance;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        me.infuse.util.Fx.init(this);
        Status status = new Status();
        data = new DataManager(this);
        items = new ItemFactory(this);
        resonance = new me.infuse.effect.Resonance(this);
        items.setResonance(resonance);
        effects = new EffectManager(this, status);
        sparks = new SparkManager(this, status);
        recipes = new RecipeManager(this);
        rituals = new RitualManager(this);
        gui = new InfuseGui(this);

        recipes.load();
        rituals.start();
        effects.start();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new CombatListener(this, status), this);
        crafting = new CraftingListener(this);
        pm.registerEvents(crafting, this);
        pm.registerEvents(gui, this);

        Commands commands = new Commands(this);
        for (String name : getDescription().getCommands().keySet()) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) effects.refresh(p);
    }

    @Override
    public void onDisable() {
        if (effects != null) effects.stop();
        if (rituals != null) rituals.stop();
        if (recipes != null) recipes.unregister();
        if (data != null) data.saveAll();
    }

    public void reload() {
        reloadConfig();
        loadSettings();
        recipes.load();
        for (Player p : Bukkit.getOnlinePlayers()) effects.refresh(p);
    }

    private void loadSettings() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        Msg.load(this);
        me.infuse.util.Style.load(getConfig());
        me.infuse.effect.EffectType.loadOverrides(getConfig());
        me.infuse.util.Util.loadAllies(getConfig());
    }

    public DataManager data() { return data; }
    public ItemFactory items() { return items; }
    public EffectManager effects() { return effects; }
    public SparkManager sparks() { return sparks; }
    public RecipeManager recipes() { return recipes; }
    public RitualManager rituals() { return rituals; }
    public InfuseGui gui() { return gui; }
    public CraftingListener crafting() { return crafting; }
    public me.infuse.effect.Resonance resonance() { return resonance; }
}
