package dev.infuse.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * InfuseCore - the hub plugin of the Infuse Ultimate suite.
 *
 * <p>It adds no weapons, effects or relics of its own. It discovers the four module plugins
 * (Customs, Bloodbound, Infuse, Limitless) at runtime, presents them as one {@code /iu} menu,
 * welcomes players with the list of what is installed, and can push the shared resource pack.
 */
public final class InfuseCorePlugin extends JavaPlugin {

    private HubMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.menu = new HubMenu(this);

        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        HubCommand command = new HubCommand(this, menu);
        var iu = getCommand("iu");
        if (iu != null) {
            iu.setExecutor(command);
            iu.setTabCompleter(command);
        }

        logBanner();
    }

    public HubMenu menu() {
        return menu;
    }

    /** Prints the suite logo and a module matrix so operators can see what loaded. */
    private void logBanner() {
        int installed = 0;
        for (Module module : Module.values()) {
            if (module.installed()) installed++;
        }
        getComponentLogger().info(Component.text("  Infuse Ultimate", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        for (Module module : Module.values()) {
            boolean on = module.installed();
            getComponentLogger().info(Component.text("   " + (on ? "✔ " : "✖ "),
                            on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                    .append(Component.text(module.displayName(), on ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                    .append(Component.text(on ? "  v" + module.version() : "  (not installed)",
                            NamedTextColor.GRAY)));
        }
        getComponentLogger().info(Component.text("   " + installed + " / " + Module.values().length
                + " modules online. Players: /iu", NamedTextColor.GRAY));
    }
}
