package dev.infuse.customs.command;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.gui.RecipeMenu;
import dev.infuse.customs.gui.SwordMenu;
import dev.infuse.customs.sword.AbilitySlot;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
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

/** /customs give | menu | list | info | recipes | pack | arc | session | faction | resetcooldowns | unforge | reload */
public final class CustomsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "customs.admin";

    private final CustomsPlugin plugin;

    public CustomsCommand(CustomsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, label, args);
            case "menu", "gui" -> menu(sender);
            case "settings", "config" -> settings(sender);
            case "recipes", "recipe", "craft" -> recipes(sender, args);
            case "unforge" -> unforge(sender, args);
            case "altar", "altars" -> altar(sender, label, args);
            case "list" -> list(sender);
            case "arc", "arcs" -> arc(sender, label, args);
            case "faction", "factions", "order", "orders" -> faction(sender, label, args);
            case "session", "sessions" -> session(sender, label, args);
            case "info" -> info(sender, args);
            case "pack" -> pack(sender, args);
            case "resetcooldowns", "resetcd" -> resetCooldowns(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Text.send(sender, "<gradient:#ECBA44:#C4415B><bold>Customs</bold></gradient> <dark_gray>· <gray>"
                + plugin.getPluginMeta().getVersion() + " <dark_gray>· <gray>click a command to fill it in");
        line(sender, "/" + label + " list", "Show all weapons");
        line(sender, "/" + label + " recipes [weapon]", "How to craft each weapon and the altar");
        line(sender, "/" + label + " info [weapon]", "Show what a weapon does");
        line(sender, "/" + label + " pack", "Re-download the resource pack");
        line(sender, "/" + label + " arc", "Which arc the server is in, and its twist");
        line(sender, "/" + label + " session", "Whether a session is running, and the top killers");
        line(sender, "/" + label + " faction", "Arc II: the factions, their leaders and points");
        if (sender.hasPermission(ADMIN)) {
            line(sender, "/" + label + " give <player> <weapon|all>", "Give weapons");
            line(sender, "/" + label + " menu", "Take weapons and altars from a menu");
            line(sender, "/" + label + " settings", "Change settings, weapon stats and cooldowns in game");
            line(sender, "/" + label + " resetcooldowns [player]", "Clear ability cooldowns");
            line(sender, "/" + label + " unforge <weapon>", "Legendary mode: allow a weapon to be crafted again");
            line(sender, "/" + label + " altar give [player] [amount]", "Give weapon altars");
            line(sender, "/" + label + " altar set <weapon>", "Make the altar you look at forge a weapon (or place one)");
            line(sender, "/" + label + " arc start|next|set <1-5>|end", "Run the server's arcs");
            line(sender, "/" + label + " session start [minutes]|end", "Start or end a session (twists run in sessions)");
            line(sender, "/" + label + " faction set <player> <blade|hammer|spear|none> [leader]", "Arc II: move a player between factions");
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
            Text.send(sender, "<gray>Usage: <white>/" + label + " give <player> <weapon|all>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, "<red>Player '" + args[1] + "' isn't online.");
            return;
        }
        List<SwordType> types = new ArrayList<>();
        if (args[2].equalsIgnoreCase("all")) {
            // faction weapons belong to the factions' leaders; give them by name
            Arrays.stream(SwordType.values()).filter(t -> !t.isFactionWeapon()).forEach(types::add);
        } else {
            SwordType type = SwordType.byId(args[2]);
            if (type == null) {
                Text.send(sender, "<red>Unknown weapon '" + args[2] + "'. Try: " + String.join(", ", ids()));
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
                ? Text.sword(types.get(0))
                : Component.text("all " + types.size() + " weapons", NamedTextColor.GOLD);
        Text.send(sender, Component.text("Gave ", NamedTextColor.GRAY).append(what)
                .append(Component.text(" to " + target.getName() + ".", NamedTextColor.GRAY)));
        if (target != sender) {
            Text.send(target, Component.text("You received ", NamedTextColor.GRAY).append(what)
                    .append(Component.text("!", NamedTextColor.GRAY)));
        }
    }

    private void settings(CommandSender sender) {
        if (denied(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can open the settings.");
            return;
        }
        new dev.infuse.customs.gui.SettingsMenu(plugin).open(player);
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

    private void recipes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Only players can open the recipe book.");
            return;
        }
        SwordType type = args.length >= 2 ? SwordType.byId(args[1]) : null;
        (type == null ? RecipeMenu.overview(plugin) : RecipeMenu.recipe(plugin, type)).open(player);
    }

    private void unforge(CommandSender sender, String[] args) {
        if (denied(sender)) {
            return;
        }
        SwordType type = args.length >= 2 ? SwordType.byId(args[1]) : null;
        if (type == null) {
            Text.send(sender, "<gray>Usage: <white>/customs unforge <weapon></white>. Weapons: " + String.join(", ", ids()));
            return;
        }
        if (plugin.forges().clear(type)) {
            plugin.altars().refreshAll();
            Text.send(sender, "<gray>" + type.swordName() + " can be forged again.");
        } else {
            Text.send(sender, "<gray>" + type.swordName() + " hasn't been forged.");
        }
    }

    /** /customs faction [set <player> <faction|none> [leader]]: Arc II's factions. */
    private void faction(CommandSender sender, String label, String[] args) {
        dev.infuse.customs.arc.Factions factions = plugin.arcs().factions();
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        if (!factions.active()) {
            Text.send(sender, "<gray>The factions only exist during a session in Arc II.");
            return;
        }
        switch (action) {
            case "set" -> {
                if (denied(sender)) {
                    return;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                boolean none = args.length > 3 && args[3].equalsIgnoreCase("none");
                dev.infuse.customs.arc.Factions.Faction f = args.length > 3 ? dev.infuse.customs.arc.Factions.Faction.parse(args[3]) : null;
                if (target == null || (f == null && !none)) {
                    Text.send(sender, "<red>Usage: <white>/" + label + " faction set <player> <blade|hammer|spear|none> [leader]");
                    return;
                }
                boolean leader = args.length > 4 && args[4].equalsIgnoreCase("leader");
                factions.assign(target, f, leader);
                Text.send(sender, Text.plain(target.getName() + " is now ", NamedTextColor.GRAY)
                        .append(f == null ? Text.plain("unsworn", NamedTextColor.WHITE)
                                : (leader ? Text.plain("the leader of the ", NamedTextColor.GRAY) : Text.plain("in the ", NamedTextColor.GRAY)).append(f.label()))
                        .append(Text.plain(".", NamedTextColor.GRAY)));
            }
            default -> {
                if (!factions.formed()) {
                    Text.send(sender, "<gray>The factions form when at least " + dev.infuse.customs.arc.Factions.MIN_PLAYERS
                            + " players in survival are online.");
                    return;
                }
                factions.status().forEach(sender::sendMessage);
                if (sender instanceof Player player) {
                    dev.infuse.customs.arc.Factions.Faction mine = factions.of(player);
                    sender.sendMessage(mine == null
                            ? Text.plain("You are unsworn. Fall to a faction's weapon and you join that faction.", NamedTextColor.GRAY)
                            : Text.plain(factions.isLeader(player) ? "You lead the " : "You are sworn to the ", NamedTextColor.GRAY)
                                    .append(mine.label()).append(Text.plain(".", NamedTextColor.GRAY)));
                }
            }
        }
    }

    /** /customs session [start [minutes]|end]: sessions, which admins start and end. */
    private void session(CommandSender sender, String label, String[] args) {
        var arcs = plugin.arcs();
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        if (action.equals("status") || action.equals("info")) {
            arcs.sessionStatus().forEach(sender::sendMessage);
            return;
        }
        if (denied(sender)) {
            return;
        }
        switch (action) {
            case "start", "begin" -> {
                int minutes = plugin.getConfig().getInt("arcs.session-minutes", 0);
                if (args.length > 2) {
                    try {
                        minutes = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        Text.send(sender, "<red>Usage: <white>/" + label + " session start [minutes]");
                        return;
                    }
                }
                if (!arcs.startSession(minutes)) {
                    Text.send(sender, "<red>A session is already running. End it with <white>/" + label + " session end</white>.");
                }
            }
            case "end", "stop" -> {
                if (!arcs.endSession()) {
                    Text.send(sender, "<red>No session is running.");
                }
            }
            default -> Text.send(sender, "<gray>Usage: <white>/" + label + " session [start [minutes]|end]");
        }
    }

    /** /customs arc [start|next|set <n>|end]: the server's arcs. */
    private void arc(CommandSender sender, String label, String[] args) {
        var arcs = plugin.arcs();
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        if (action.equals("status") || action.equals("info")) {
            arcs.status().forEach(sender::sendMessage);
            return;
        }
        if (denied(sender)) {
            return;
        }
        dev.infuse.customs.sword.Arc current = arcs.current();
        switch (action) {
            case "start" -> {
                if (current != null) {
                    Text.send(sender, "<red>The arcs are already running (" + current.label() + "). Use <white>/" + label
                            + " arc next</white> to move on.");
                    return;
                }
                arcs.begin(dev.infuse.customs.sword.Arc.I);
            }
            case "next" -> {
                if (current == null) {
                    arcs.begin(dev.infuse.customs.sword.Arc.I);
                } else if (current.next() == null) {
                    Text.send(sender, "<red>" + current.label() + " is the last arc. Use <white>/" + label
                            + " arc end</white> to finish the arcs.");
                } else {
                    arcs.begin(current.next());
                }
            }
            case "set" -> {
                dev.infuse.customs.sword.Arc arc = args.length > 2 ? dev.infuse.customs.sword.Arc.parse(args[2]) : null;
                if (arc == null) {
                    Text.send(sender, "<red>Usage: <white>/" + label + " arc set <1-5>");
                    return;
                }
                arcs.begin(arc);
            }
            case "end", "stop" -> {
                if (current == null) {
                    Text.send(sender, "<red>No arc is running.");
                    return;
                }
                arcs.end();
            }
            default -> Text.send(sender, "<gray>Usage: <white>/" + label + " arc [start|next|set <1-5>|end]");
        }
    }

    private void altar(CommandSender sender, String label, String[] args) {
        if (denied(sender)) {
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "give" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    Text.send(sender, "<red>Player not found.");
                    return;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        Text.send(sender, "<red>'" + args[3] + "' isn't a number.");
                        return;
                    }
                }
                target.getInventory().addItem(plugin.altars().createAltarItem(amount)).values()
                        .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
                Text.send(sender, "<gray>Gave <white>" + amount + "</white> weapon altar(s) to <white>" + target.getName() + "</white>.");
            }
            case "set", "shrine" -> {
                if (!(sender instanceof Player player)) {
                    Text.send(sender, "<red>Only players can do that.");
                    return;
                }
                SwordType type = args.length >= 3 ? SwordType.byId(args[2]) : null;
                if (type == null) {
                    Text.send(sender, "<gray>Usage: <white>/" + label + " altar set <weapon></white>. Weapons: " + String.join(", ", ids()));
                    return;
                }
                if (type.isFactionWeapon()) {
                    Text.send(sender, "<red>" + type.swordName() + " belongs to a faction leader in Arc II and is never forged.");
                    return;
                }
                org.bukkit.block.Block looked = player.getTargetBlockExact(8);
                if (looked == null) {
                    Text.send(sender, "<red>Look at an altar, or at the block to put a new one on.");
                    return;
                }
                if (plugin.altars().setWeapon(looked, type)) {
                    Text.send(sender, Component.text("The altar now forges ", NamedTextColor.GRAY)
                            .append(Text.sword(type)).append(Component.text(".", NamedTextColor.GRAY)));
                } else {
                    Text.send(sender, "<red>The space above that block must be empty.");
                }
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    Text.send(sender, "<red>Only players can do that.");
                    return;
                }
                org.bukkit.block.Block looked = player.getTargetBlockExact(8);
                if (looked == null || !plugin.altars().remove(looked, true)) {
                    Text.send(sender, "<red>Look at an altar to remove it.");
                } else {
                    Text.send(sender, "<gray>Removed the altar.");
                }
            }
            case "list" -> {
                var all = plugin.altars().weapons();
                Text.send(sender, "<gray>" + all.size() + " altar(s):");
                all.forEach((loc, type) -> sender.sendMessage(Component.text("  " + loc.getWorld().getName() + " "
                                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "  ", NamedTextColor.WHITE)
                        .append(type == null ? Component.text("(dormant)", NamedTextColor.DARK_GRAY) : Text.sword(type))));
            }
            default -> Text.send(sender, "<gray>Usage: <white>/" + label + " altar <give|set|remove|list></white>");
        }
    }

    private void list(CommandSender sender) {
        Text.send(sender, "<gray>Hover a weapon to see what it does, click for details:");
        for (dev.infuse.customs.sword.Arc arc : dev.infuse.customs.sword.Arc.values()) {
            sender.sendMessage(Text.plain(arc.label(), arc.color()).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .append(Text.plain(" · " + arc.title(), NamedTextColor.GRAY)));
            for (SwordType type : arc.members()) {
                listLine(sender, type);
            }
        }
    }

    private void listLine(CommandSender sender, SwordType type) {
        boolean enabled = plugin.settings().sword(type).enabled();
        Component name = Text.sword(type)
                .hoverEvent(plugin.items().create(type).asHoverEvent())
                .clickEvent(ClickEvent.runCommand("/customs info " + type.id()));
        Component line = Component.text("  ").append(name)
                .append(Component.text("  " + type.title(), NamedTextColor.DARK_GRAY))
                .append(Component.text("  (" + type.id() + ")", NamedTextColor.DARK_GRAY));
        if (!enabled) {
            line = line.append(Component.text(" (disabled)", NamedTextColor.RED));
        } else if (plugin.settings().uniqueSwords) {
            var forged = plugin.forges().get(type);
            line = line.append(forged == null
                    ? Component.text(" (not yet forged)", NamedTextColor.GOLD)
                    : Component.text(" (forged by " + forged.name() + ")", NamedTextColor.GRAY));
        }
        sender.sendMessage(line);
    }

    private void info(CommandSender sender, String[] args) {
        SwordType type = args.length >= 2 ? SwordType.byId(args[1])
                : sender instanceof Player player ? plugin.items().heldType(player) : null;
        if (type == null) {
            Text.send(sender, "<gray>Hold a weapon or use <white>/customs info <weapon></white>. Weapons: " + String.join(", ", ids()));
            return;
        }
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  ").append(Text.sword(type).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text("  " + type.title(), type.accent())));
        for (String line : Text.wrap(type.bio(), 55)) {
            sender.sendMessage(Component.text("  " + line, NamedTextColor.GRAY).decorate(net.kyori.adventure.text.format.TextDecoration.ITALIC));
        }
        infoLine(sender, type, Component.text("Passive"), type.passive().name(), type.passive().description(), -1);
        infoLine(sender, type, Component.text("On Hit"), type.onHit().name(), type.onHit().description(), -1);
        for (AbilitySlot slot : AbilitySlot.values()) {
            infoLine(sender, type, slot.label(plugin.settings().trigger), type.ability(slot).name(), type.ability(slot).description(),
                    plugin.settings().sword(type).cooldown(slot));
        }
    }

    private static void infoLine(CommandSender sender, SwordType type, Component label, String name, String description, int cooldown) {
        Component line = Component.text("  ▸ ", NamedTextColor.GRAY).append(label.color(NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.GRAY))
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
            if (!player.hasPermission("customs.pack")) {
                Text.send(sender, "<red>You don't have permission to do that.");
                return;
            }
            target = player;
        } else {
            Text.send(sender, "<gray>Usage: /customs pack <player>");
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
            Text.send(sender, "<red>Player not found. Usage: /customs resetcooldowns <player|*>");
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

    private static boolean isFaction(String arg) {
        String a = arg.toLowerCase(Locale.ROOT);
        return a.startsWith("faction") || a.startsWith("order");
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
            options.addAll(List.of("list", "info", "recipes", "pack", "arc", "session", "faction"));
            if (admin) {
                options.addAll(List.of("give", "menu", "settings", "resetcooldowns", "unforge", "altar", "reload"));
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
                case "info", "recipes", "recipe" -> options.addAll(ids());
                case "unforge" -> {
                    if (admin) {
                        options.addAll(ids());
                    }
                }
                case "altar", "altars" -> {
                    if (admin) {
                        options.addAll(List.of("give", "set", "remove", "list"));
                    }
                }
                case "arc", "arcs" -> {
                    if (admin) {
                        options.addAll(List.of("start", "next", "set", "end"));
                    }
                }
                case "faction", "factions", "order", "orders" -> {
                    options.add("list");
                    if (admin) {
                        options.add("set");
                    }
                }
                case "session", "sessions" -> {
                    if (admin) {
                        options.addAll(List.of("start", "end"));
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && admin) {
            options.addAll(ids());
            options.add("all");
        } else if (args.length == 3 && isFaction(args[0]) && args[1].equalsIgnoreCase("set") && admin) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (args.length == 4 && isFaction(args[0]) && args[1].equalsIgnoreCase("set") && admin) {
            options.addAll(List.of("blade", "hammer", "spear", "none"));
        } else if (args.length == 5 && isFaction(args[0]) && args[1].equalsIgnoreCase("set") && admin) {
            options.add("leader");
        } else if (args.length == 3 && args[0].toLowerCase(Locale.ROOT).startsWith("session") && args[1].equalsIgnoreCase("start") && admin) {
            options.addAll(List.of("30", "60", "90", "120"));
        } else if (args.length == 3 && args[0].toLowerCase(Locale.ROOT).startsWith("arc") && args[1].equalsIgnoreCase("set") && admin) {
            options.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (args.length == 3 && args[0].toLowerCase(Locale.ROOT).startsWith("altar") && admin) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "set", "shrine" -> options.addAll(ids());
                case "give" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
