package dev.lambro.blade;

import dev.lambro.blade.ability.AbilityManager;
import dev.lambro.blade.gui.MenuListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class LambroPlugin extends JavaPlugin {

    private BladeItem blade;
    private AbilityManager abilities;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        abilities = new AbilityManager(this);
        blade = new BladeItem(this);
        abilities.registerDefaults();
        writeMissingDefaults();

        getServer().getPluginManager().registerEvents(abilities, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        abilities.start();

        LambroCommand cmd = new LambroCommand(this);
        PluginCommand pc = getCommand("lambro");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getLogger().info("The Blade of Lambro has awakened.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.shutdown();
    }

    /** Ensures every ability setting exists in config.yml so it's visible & editable. */
    private void writeMissingDefaults() {
        boolean changed = false;
        for (var a : abilities.all()) {
            if (!getConfig().isSet(a.path("enabled"))) {
                getConfig().set(a.path("enabled"), true);
                changed = true;
            }
            for (var s : a.settings()) {
                if (!getConfig().isSet(a.path(s.key()))) {
                    getConfig().set(a.path(s.key()), s.integer() ? (Object) (int) s.def() : (Object) s.def());
                    changed = true;
                }
            }
        }
        if (changed) saveConfig();
    }

    public void reload() {
        reloadConfig();
        writeMissingDefaults();
        refreshAllBlades();
    }

    /** Re-renders every blade carried by online players (after config edits). */
    public void refreshAllBlades() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (blade.isBlade(it)) blade.render(it);
            }
        }
    }

    public BladeItem blade() { return blade; }
    public AbilityManager abilities() { return abilities; }
}
