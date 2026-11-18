package dev.infuse.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Handles {@code /iu} and its sub-commands. */
public final class HubCommand implements TabExecutor {

    private final InfuseCorePlugin plugin;
    private final HubMenu menu;

    public HubCommand(InfuseCorePlugin plugin, HubMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                menu.open(player);
            } else {
                status(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status", "modules" -> status(sender);
            case "pack" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can request the pack.", NamedTextColor.RED));
                    return true;
                }
                if (!player.hasPermission("infusecore.pack")) {
                    player.sendMessage(Component.text("You may not request the resource pack.", NamedTextColor.RED));
                    return true;
                }
                sendPack(player);
            }
            case "reload" -> {
                if (!sender.hasPermission("infusecore.admin")) {
                    sender.sendMessage(Component.text("You may not reload InfuseCore.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(Component.text("InfuseCore config reloaded.", NamedTextColor.GREEN));
            }
            case "help" -> help(sender);
            default -> help(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(Component.text("Infuse Ultimate", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(" - module status", NamedTextColor.GRAY)));
        for (Module module : Module.values()) {
            boolean on = module.installed();
            Component line = Component.text(on ? "  ✔ " : "  ✖ ",
                            on ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text(module.displayName(), on ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                    .append(Component.text("  " + module.tagline(), NamedTextColor.DARK_GRAY));
            if (on) {
                line = line.append(Component.text("  v" + module.version(), NamedTextColor.GRAY))
                        .append(Component.text("  [/" + module.command() + "]", NamedTextColor.YELLOW)
                                .hoverEvent(HoverEvent.showText(Component.text("Run /" + module.command())))
                                .clickEvent(ClickEvent.runCommand("/" + module.command())));
            }
            sender.sendMessage(line);
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("Infuse Ultimate commands", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  /iu", NamedTextColor.YELLOW)
                .append(Component.text(" - open the hub menu", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /iu status", NamedTextColor.YELLOW)
                .append(Component.text(" - list installed modules", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /iu pack", NamedTextColor.YELLOW)
                .append(Component.text(" - get the shared resource pack", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /iu reload", NamedTextColor.YELLOW)
                .append(Component.text(" - reload config (admin)", NamedTextColor.GRAY)));
    }

    @SuppressWarnings("deprecation")
    private void sendPack(Player player) {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled")) {
            player.sendMessage(Component.text("The server has not enabled the Infuse Ultimate pack.", NamedTextColor.RED));
            return;
        }
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url.isBlank()) {
            player.sendMessage(Component.text("The server has not set a resource-pack URL.", NamedTextColor.RED));
            return;
        }
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "Install the Infuse Ultimate pack.");
        boolean required = plugin.getConfig().getBoolean("resource-pack.required");
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = parseHex(sha1);
        if (hash != null) {
            player.setResourcePack(url, hash, prompt, required);
        } else {
            player.setResourcePack(url, (byte[]) null, prompt, required);
        }
        player.sendMessage(Component.text("Sending the Infuse Ultimate pack...", NamedTextColor.AQUA));
    }

    /** Parses a 40-char SHA-1 hex string into bytes, or returns null if absent/invalid. */
    static byte[] parseHex(String hex) {
        if (hex == null) return null;
        String clean = hex.trim();
        if (clean.length() != 40) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int hi = Character.digit(clean.charAt(i * 2), 16);
            int lo = Character.digit(clean.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> all = new ArrayList<>(List.of("status", "modules", "pack", "help"));
            if (sender.hasPermission("infusecore.admin")) all.add("reload");
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : all) if (s.startsWith(prefix)) out.add(s);
            return out;
        }
        return List.of();
    }
}
