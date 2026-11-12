package dev.infuse.opswords;

import dev.infuse.opswords.ability.AbilityRegistry;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.command.OPSwordsCommand;
import dev.infuse.opswords.gui.Menu;
import dev.infuse.opswords.listener.AbilityListener;
import dev.infuse.opswords.listener.CombatListener;
import dev.infuse.opswords.listener.ItemListener;
import dev.infuse.opswords.manager.CooldownManager;
import dev.infuse.opswords.manager.ForgeRegistry;
import dev.infuse.opswords.manager.PassiveManager;
import dev.infuse.opswords.manager.StatusManager;
import dev.infuse.opswords.pack.ResourcePackService;
import dev.infuse.opswords.recipe.RecipeService;
import dev.infuse.opswords.sword.SwordItems;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class OPSwordsPlugin extends JavaPlugin {

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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());

        items = new SwordItems(this);
        combat = new Combat(this);
        cooldowns = new CooldownManager();
        status = new StatusManager(this);
        passives = new PassiveManager(this);
        abilities = new AbilityRegistry(this);
        recipes = new RecipeService(this);
        resourcePack = new ResourcePackService(this);
        forges = new ForgeRegistry(this);
        forges.load();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new AbilityListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new Menu.Listener(), this);
        pm.registerEvents(resourcePack, this);
        abilities.registerListeners();

        PluginCommand command = getCommand("opswords");
        if (command != null) {
            OPSwordsCommand executor = new OPSwordsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        status.start();
        passives.start();
        recipes.registerAll();
        resourcePack.start();

        getLogger().info("Loaded " + SwordType.values().length + " OP swords.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) {
            abilities.shutdown();
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
    public void reload() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        forges.load();
        recipes.unregisterAll();
        recipes.registerAll();
        resourcePack.stop();
        resourcePack.start();
    }

    /** Permission, per-sword toggle and disabled-world checks in one place. */
    public boolean canUse(Player player, SwordType type) {
        return player.hasPermission("opswords.use")
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
}
