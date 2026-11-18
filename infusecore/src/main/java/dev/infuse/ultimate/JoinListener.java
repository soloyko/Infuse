package dev.infuse.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Welcomes players with the installed-module list and optionally pushes the resource pack. */
public final class JoinListener implements Listener {

    private final InfuseCorePlugin plugin;

    public JoinListener(InfuseCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getConfig().getBoolean("welcome.enabled", true)) {
            StringBuilder modules = new StringBuilder();
            for (Module module : Module.values()) {
                if (module.installed()) {
                    if (modules.length() > 0) modules.append(", ");
                    modules.append(module.displayName());
                }
            }
            if (modules.length() > 0) {
                player.sendMessage(Component.text("Welcome to ", NamedTextColor.GRAY)
                        .append(Component.text("Infuse Ultimate", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("Active: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(modules.toString(), NamedTextColor.GRAY)));
                player.sendMessage(Component.text("Open the hub with ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("/iu", NamedTextColor.YELLOW)
                                .clickEvent(ClickEvent.runCommand("/iu"))));
            }
        }

        if (plugin.getConfig().getBoolean("resource-pack.enabled")
                && plugin.getConfig().getBoolean("resource-pack.on-join")
                && !plugin.getConfig().getString("resource-pack.url", "").isBlank()
                && player.hasPermission("infusecore.pack")) {
            player.performCommand("iu pack");
        }
    }
}
