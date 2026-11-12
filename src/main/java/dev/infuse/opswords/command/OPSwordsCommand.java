package dev.infuse.opswords.command;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.gui.SwordMenu;
import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /opswords give | menu | list | info | pack | resetcooldowns | reload */
public final class OPSwordsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "opswords.admin";

    private final OPSwordsPlugin plugin;

    public OPSwordsCommand(OPSwordsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, label, args);
            case "menu", "gui" -> menu(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "pack" -> pack(sender, args);
            case "resetcooldowns", "resetcd" -> resetCooldowns(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Text.send(sender, "<gray>Commands:");
        line(sender, "/" + label + " list", "Show all swords");
        line(sender, "/" + label + " info [sword]", "Show what a sword does");
        line(sender, "/" + label + " pack", "Re-download the resource pack");
        if (sender.hasPermission(ADMIN)) {
            line(sender, "/" + label + " give <player> <sword|all>", "Give swords");
            line(sender, "/" + label + " menu", "Take swords from a menu");
            line(sender, "/" + label + " resetcooldowns [player]", "Clear ability cooldowns");
            line(sender, "/" + label + " reload", "Reload config.yml");
        }
    }

    private static void line(CommandSender sender, String usage, String description) {
        sender.sendMessage(Component.text("  " + usage, NamedTextColor.WHITE)
                .clickEvent(ClickEvent.suggestCommand(usage.replaceAll(" [<\\[].*", " ")))
                .append(Component.text(" - " + description, NamedTextColor.GRAY)));
    }

    private boolean denied(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return false;
        }
        Text.send(sender, "<red>You don't have permission to do that.");
        return true;
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (denied(sender)) {
            return;
        }
        if (args.length < 3) {
            Text.send(sender, "<gray>Usage: <white>/" + label + " give <player> <sword|all>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, "<red>Player '" + args[1] + "' isn't online.");
            return;
        }
        List<SwordType> types = new ArrayList<>();
        if (args[2].equalsIgnoreCase("all")) {
            types.addAll(Arrays.asList(SwordType.values()));
        } else {
            SwordType type = SwordType.byId(args[2]);
            if (type == null) {
                Text.send(sender, "<red>Unknown sword '" + args[2] + "'. Try: " + String.join(", ", ids()));
                return;
            }
            types.add(type);
        }
        for (SwordType type : types) {
            ItemStack item = plugin.items().create(type);
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
            leftover.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        Component what = types.size() == 1
                ? Text.gradient(types.get(0).emoji() + " " + types.get(0).displayName(), types.get(0).color(), types.get(0).accent())
                : Component.text("all " + types.size() + " OP swords", NamedTextColor.GOLD);
        Text.send(sender, Component.text("Gave ", NamedTextColor.GRAY).append(what)
                .append(Component.text(" to " + target.getName() + ".", NamedTextColor.GRAY)));
        if (target != sender) {
            Text.send(target, Component.text("You received ", NamedTextColor.GRAY).append(what)
                    .append(Component.text("!", NamedTextColor.GRAY)));
        }
    }

    private void menu(CommandSender sender) {
        if (denied(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can open the menu.");
            return;
        }
        new SwordMenu(plugin).open(player);
    }

    private void list(CommandSender sender) {
        Text.send(sender, "<gray>Hover a sword to see what it does:");
        for (SwordType type : SwordType.values()) {
            boolean enabled = plugin.settings().sword(type).enabled();
            Component name = Text.gradient(type.emoji() + " " + type.displayName(), type.color(), type.accent())
                    .hoverEvent(plugin.items().create(type).asHoverEvent())
                    .clickEvent(ClickEvent.runCommand("/opswords info " + type.id()));
            Component line = Component.text("  ").append(name)
                    .append(Component.text("  " + type.ability(AbilitySlot.PRIMARY).name() + " / "
                            + type.ability(AbilitySlot.SECONDARY).name(), NamedTextColor.DARK_GRAY));
            if (!enabled) {
                line = line.append(Component.text(" (disabled)", NamedTextColor.RED));
            }
            sender.sendMessage(line);
        }
    }

    private void info(CommandSender sender, String[] args) {
        SwordType type = args.length >= 2 ? SwordType.byId(args[1])
                : sender instanceof Player player ? plugin.items().heldType(player) : null;
        if (type == null) {
            Text.send(sender, "<gray>Hold a sword or use <white>/opswords info <sword></white>. Swords: " + String.join(", ", ids()));
            return;
        }
        sender.sendMessage(Component.empty());
        sender.sendMessage(Text.gradient("  " + type.emoji() + " " + type.displayName(), type.color(), type.accent()));
        infoLine(sender, type, "Passive", type.passive().name(), type.passive().description(), -1);
        infoLine(sender, type, "On Hit", type.onHit().name(), type.onHit().description(), -1);
        for (AbilitySlot slot : AbilitySlot.values()) {
            infoLine(sender, type, slot.trigger(), type.ability(slot).name(), type.ability(slot).description(),
                    plugin.settings().sword(type).cooldown(slot));
        }
    }

    private static void infoLine(CommandSender sender, SwordType type, String label, String name, String description, int cooldown) {
        Component line = Component.text("  ▸ " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(name, type.color()));
        if (cooldown >= 0) {
            line = line.append(Component.text(" (" + cooldown + "s)", NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(line);
        sender.sendMessage(Component.text("     " + description, NamedTextColor.WHITE));
    }

    private void pack(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2 && sender.hasPermission(ADMIN)) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Text.send(sender, "<red>Player '" + args[1] + "' isn't online.");
                return;
            }
        } else if (sender instanceof Player player) {
            if (!player.hasPermission("opswords.pack")) {
                Text.send(sender, "<red>You don't have permission to do that.");
                return;
            }
            target = player;
        } else {
            Text.send(sender, "<gray>Usage: /opswords pack <player>");
            return;
        }
        if (plugin.resourcePack().send(target)) {
            Text.send(sender, "<gray>Sent the resource pack to <white>" + target.getName() + "</white>.");
        } else {
            Text.send(sender, "<red>The resource pack isn't available right now. Check the console and config.yml.");
        }
    }

    private void resetCooldowns(CommandSender sender, String[] args) {
        if (denied(sender)) {
            return;
        }
        if (args.length >= 2 && args[1].equals("*")) {
            plugin.cooldowns().resetAll();
            Text.send(sender, "<gray>Cleared everyone's cooldowns.");
            return;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            Text.send(sender, "<red>Player not found. Usage: /opswords resetcooldowns <player|*>");
            return;
        }
        plugin.cooldowns().reset(target.getUniqueId());
        Text.send(sender, "<gray>Cleared cooldowns for <white>" + target.getName() + "</white>.");
    }

    private void reload(CommandSender sender) {
        if (denied(sender)) {
            return;
        }
        plugin.reload();
        Text.send(sender, "<green>Reloaded config.yml.");
    }

    private static List<String> ids() {
        return Arrays.stream(SwordType.values()).map(SwordType::id).toList();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("list", "info", "pack"));
            if (admin) {
                options.addAll(List.of("give", "menu", "resetcooldowns", "reload"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "pack", "resetcooldowns", "resetcd" -> {
                    if (admin) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                        if (!args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("pack")) {
                            options.add("*");
                        }
                    }
                }
                case "info" -> options.addAll(ids());
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && admin) {
            options.addAll(ids());
            options.add("all");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
