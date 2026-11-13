package dev.infuse.customs;

import dev.infuse.customs.ability.AbilityRegistry;
import dev.infuse.customs.altar.AltarService;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.command.CustomsCommand;
import dev.infuse.customs.gui.Menu;
import dev.infuse.customs.listener.AbilityListener;
import dev.infuse.customs.listener.CombatListener;
import dev.infuse.customs.listener.ItemListener;
import dev.infuse.customs.manager.CooldownManager;
import dev.infuse.customs.manager.ForgeRegistry;
import dev.infuse.customs.manager.PassiveManager;
import dev.infuse.customs.manager.StatusManager;
import dev.infuse.customs.pack.ResourcePackService;
import dev.infuse.customs.recipe.RecipeService;
import dev.infuse.customs.sword.SwordItems;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class CustomsPlugin extends JavaPlugin {

    private Settings settings;
    private SwordItems items;
    private Combat combat;
    private CooldownManager cooldowns;
    private StatusManager status;
    private PassiveManager passives;
    private AbilityRegistry abilities;
    private RecipeService recipes;
    private ResourcePackService resourcePack;
    private ForgeRegistry forges;
    private Displays displays;
    private dev.infuse.customs.ability.Fx3D fx;
    private dev.infuse.customs.ability.DamageNumbers damageNumbers;
    private AltarService altars;

    /** Before the rename the data folder was plugins/OPSwords: move it so configs and altars carry over. */
    @Override
    public void onLoad() {
        java.io.File old = new java.io.File(getDataFolder().getParentFile(), "OPSwords");
        if (old.isDirectory() && !getDataFolder().exists()) {
            if (old.renameTo(getDataFolder())) {
                getLogger().info("Moved plugins/OPSwords to plugins/Customs.");
                new java.io.File(getDataFolder(), "OPSwords-ResourcePack.zip").delete();
            } else {
                getLogger().warning("Could not move plugins/OPSwords to plugins/Customs; copy it over by hand.");
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeNewDefaults();
        settings = Settings.load(getConfig(), getLogger());

        items = new SwordItems(this);
        combat = new Combat(this);
        displays = new Displays(this);
        fx = new dev.infuse.customs.ability.Fx3D(displays);
        damageNumbers = new dev.infuse.customs.ability.DamageNumbers(displays);
        cooldowns = new CooldownManager();
        status = new StatusManager(this);
        passives = new PassiveManager(this);
        abilities = new AbilityRegistry(this);
        recipes = new RecipeService(this);
        resourcePack = new ResourcePackService(this);
        altars = new AltarService(this);
        forges = new ForgeRegistry(this);
        forges.load();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new AbilityListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new Menu.Listener(), this);
        pm.registerEvents(resourcePack, this);
        pm.registerEvents(displays, this);
        pm.registerEvents(altars, this);
        abilities.registerListeners();

        PluginCommand command = getCommand("customs");
        if (command != null) {
            CustomsCommand executor = new CustomsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        status.start();
        passives.start();
        recipes.registerAll();
        resourcePack.start();
        altars.start();

        getLogger().info("Loaded " + SwordType.values().length + " weapons.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) {
            abilities.shutdown();
        }
        if (displays != null) {
            displays.shutdown();
        }
        if (altars != null) {
            altars.stop();
        }
        if (passives != null) {
            passives.shutdown();
        }
        if (status != null) {
            status.shutdown();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        if (resourcePack != null) {
            resourcePack.stop();
        }
    }

    /** Re-reads config.yml and re-applies everything that depends on it. */
    /**
     * Applies settings changed in game: saves config.yml and reloads everything that reads it,
     * except the resource pack (restarting it would re-send the pack to everyone).
     */
    public void applySettings() {
        saveConfig();
        settings = Settings.load(getConfig(), getLogger());
        recipes.unregisterAll();
        recipes.registerAll();
        altars.refreshAll();
    }

    public void reload() {
        reloadConfig(); // pick up the user's edits first, then merge so nothing on disk is overwritten
        mergeNewDefaults();
        settings = Settings.load(getConfig(), getLogger());
        forges.load();
        recipes.unregisterAll();
        recipes.registerAll();
        altars.refreshAll();
        resourcePack.stop();
        resourcePack.start();
    }

    /** Adds settings and swords from newer versions to an existing config.yml, keeping the user's values. */
    private void mergeNewDefaults() {
        if (getConfig().getInt("config-version", 1) < 2) {
            // 1.5.1: legendary mode became the default; every weapon can be crafted once
            getConfig().set("crafting.unique-swords", true);
            getLogger().info("Legendary mode is now on: each weapon can only be crafted once."
                    + " Set crafting.unique-swords to false to allow copies.");
        }
        if (getConfig().getInt("config-version", 1) < 3) {
            // 2.0: renamed from OP Swords to Customs
            String prompt = getConfig().getString("resource-pack.prompt", "");
            if (prompt.contains("OP Swords")) {
                getConfig().set("resource-pack.prompt", prompt.replace("OP Swords", "Customs").replace("3D sword models", "3D weapon models"));
            }
            getConfig().set("config-version", 3);
        }
        getConfig().set("altars.recipe", null); // 1.4 only: altars are now placed by admins, not crafted
        getConfig().options().copyDefaults(true);
        // keep the explanations in the file in step with this version (values are never touched)
        var defaults = getConfig().getDefaults();
        if (defaults != null) {
            for (String key : defaults.getKeys(true)) {
                if (!defaults.getComments(key).isEmpty()) {
                    getConfig().setComments(key, defaults.getComments(key));
                }
            }
            if (defaults instanceof org.bukkit.configuration.file.FileConfiguration file) {
                getConfig().options().setHeader(file.options().getHeader());
            }
        }
        saveConfig();
        reloadConfig(); // read the merged values back from disk, not just the in-memory defaults
    }

    /** Permission, per-sword toggle and disabled-world checks in one place. */
    public boolean canUse(Player player, SwordType type) {
        return player.hasPermission("customs.use")
                && settings.sword(type).enabled()
                && !settings.disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public Settings settings() {
        return settings;
    }

    public SwordItems items() {
        return items;
    }

    public Combat combat() {
        return combat;
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    public StatusManager status() {
        return status;
    }

    public PassiveManager passives() {
        return passives;
    }

    public AbilityRegistry abilities() {
        return abilities;
    }

    public RecipeService recipes() {
        return recipes;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    public ForgeRegistry forges() {
        return forges;
    }

    public dev.infuse.customs.ability.DamageNumbers damageNumbers() {
        return damageNumbers;
    }

    public dev.infuse.customs.ability.Fx3D fx() {
        return fx;
    }

    public Displays displays() {
        return displays;
    }

    public AltarService altars() {
        return altars;
    }
}
