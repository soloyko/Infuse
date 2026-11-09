package me.infuse.command;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.effect.EffectType;
import me.infuse.effect.Equipped;
import me.infuse.effect.ItemFactory;
import me.infuse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Handles every command in plugin.yml. */
public final class Commands implements TabExecutor {
    private final InfusePlugin plugin;

    public Commands(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "infuses" -> infuses(sender);
            case "controls" -> player(sender, this::controls);
            case "lspark" -> player(sender, p -> plugin.sparks().use(p, PlayerData.PRIMARY));
            case "rspark" -> player(sender, p -> plugin.sparks().use(p, PlayerData.SECONDARY));
            case "ldrain" -> player(sender, p -> drain(p, PlayerData.PRIMARY));
            case "rdrain" -> player(sender, p -> drain(p, PlayerData.SECONDARY));
            case "swap" -> player(sender, this::swap);
            case "cleareffects" -> clearEffects(sender, args);
            case "cooldown" -> resetCooldown(sender, args);
            case "infuse" -> infuse(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private interface PlayerAction { void run(Player p); }

    private void player(CommandSender sender, PlayerAction action) {
        if (sender instanceof Player p) action.run(p);
        else Msg.send(sender, "only-players");
    }

    private void infuses(CommandSender sender) {
        Msg.send(sender, "infuses-header");
        for (EffectType t : EffectType.values()) {
            if (!t.enabled()) continue;
            Component line = ItemFactory.name(t, false);
            Component hover = ItemFactory.name(t, false).append(Component.newline()).append(Component.text("Passive:", t.color()));
            for (String s : t.passives()) hover = hover.append(Component.newline()).append(Component.text(" • " + s, NamedTextColor.GRAY));
            hover = hover.append(Component.newline()).append(Component.text("Spark:", t.color()));
            for (String s : t.sparks()) hover = hover.append(Component.newline()).append(Component.text(" • " + s, NamedTextColor.GRAY));
            sender.sendMessage(line.append(Component.text("  " + t.passives().get(0) + " | " + t.sparks().get(0), NamedTextColor.DARK_GRAY))
                    .hoverEvent(hover));
        }
        Msg.send(sender, "infuses-footer");
    }

    private void controls(Player p) {
        PlayerData d = plugin.data().get(p);
        d.setOffhandControls(!d.offhandControls());
        plugin.data().save(d);
        Msg.send(p, d.offhandControls() ? "controls-offhand" : "controls-commands");
    }

    private void drain(Player p, int slot) {
        Equipped e = plugin.effects().unequip(p, slot);
        if (e == null) {
            Msg.send(p, "drain-empty");
            return;
        }
        ItemStack bottle = plugin.items().create(e.type(), e.augmented());
        for (ItemStack left : p.getInventory().addItem(bottle).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        Msg.send(p, "drained", Msg.comp("effect", ItemFactory.name(e.type(), e.augmented())));
    }

    private void swap(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.isEmpty()) {
            Msg.send(p, "swap-empty");
            return;
        }
        d.swap();
        plugin.data().save(d);
        Msg.send(p, "swapped");
    }

    private Player target(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            if (sender instanceof Player p) return p;
            Msg.send(sender, "specify-player");
            return null;
        }
        Player t = Bukkit.getPlayerExact(args[index]);
        if (t == null) Msg.send(sender, "player-not-found", Msg.text("player", args[index]));
        return t;
    }

    private void clearEffects(CommandSender sender, String[] args) {
        Player t = target(sender, args, 0);
        if (t == null) return;
        plugin.effects().clearAll(t);
        Msg.send(sender, "cleared", Msg.text("player", t.getName()));
    }

    private void resetCooldown(CommandSender sender, String[] args) {
        Player t = target(sender, args, 0);
        if (t == null) return;
        PlayerData d = plugin.data().get(t);
        d.resetCooldowns();
        plugin.data().save(d);
        Msg.send(sender, "cooldown-reset", Msg.text("player", t.getName()));
    }

    private void infuse(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "only-players"); return; }
                if (!p.hasPermission("infuse.gui")) { Msg.send(p, "no-permission"); return; }
                plugin.gui().open(p);
            }
            case "give" -> {
                if (!sender.hasPermission("infuse.admin")) { Msg.send(sender, "no-permission"); return; }
                if (args.length < 3) { Msg.send(sender, "give-usage"); return; }
                Player t = Bukkit.getPlayerExact(args[1]);
                EffectType type = EffectType.fromId(args[2]);
                if (t == null || type == null) { Msg.send(sender, t == null ? "specify-player" : "unknown-effect"); return; }
                boolean aug = args.length > 3 && Boolean.parseBoolean(args[3]);
                for (ItemStack left : t.getInventory().addItem(plugin.items().create(type, aug)).values()) {
                    t.getWorld().dropItemNaturally(t.getLocation(), left);
                }
                Msg.send(sender, "gave", Msg.text("player", t.getName()), Msg.comp("effect", ItemFactory.name(type, aug)));
            }
            case "crafts" -> {
                if (!sender.hasPermission("infuse.admin")) { Msg.send(sender, "no-permission"); return; }
                for (EffectType t : EffectType.values()) {
                    Msg.send(sender, "crafts-line", Msg.comp("effect", ItemFactory.name(t, false)),
                            Msg.text("count", plugin.data().craftCount(t)), Msg.text("max", plugin.crafting().maxCrafts(t)));
                }
            }
            case "resetcrafts" -> {
                if (!sender.hasPermission("infuse.admin")) { Msg.send(sender, "no-permission"); return; }
                if (args.length > 1) {
                    EffectType t = EffectType.fromId(args[1]);
                    if (t == null) { Msg.send(sender, "unknown-effect"); return; }
                    plugin.data().setCraftCount(t, 0);
                } else {
                    for (EffectType t : EffectType.values()) plugin.data().setCraftCount(t, 0);
                }
                Msg.send(sender, "crafts-reset");
            }
            case "reload" -> {
                if (!sender.hasPermission("infuse.admin")) { Msg.send(sender, "no-permission"); return; }
                plugin.reload();
                Msg.send(sender, "reloaded");
            }
            default -> {
                Msg.send(sender, "help");
                if (sender.hasPermission("infuse.gui")) Msg.send(sender, "help-gui");
                if (sender.hasPermission("infuse.admin")) {
                    Msg.send(sender, "help-admin");
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("cleareffects") || name.equals("cooldown")) {
            return args.length == 1 ? players(args[0]) : List.of();
        }
        if (!name.equals("infuse")) return List.of();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("gui"));
            if (sender.hasPermission("infuse.admin")) subs.addAll(List.of("give", "crafts", "resetcrafts", "reload"));
            return filter(subs, args[0]);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) return players(args[1]);
            if (args.length == 3) return filter(effectIds(), args[2]);
            if (args.length == 4) return filter(List.of("true", "false"), args[3]);
        }
        if (args[0].equalsIgnoreCase("resetcrafts") && args.length == 2) return filter(effectIds(), args[1]);
        return List.of();
    }

    private static List<String> effectIds() {
        return Arrays.stream(EffectType.values()).map(EffectType::id).toList();
    }

    private static List<String> players(String prefix) {
        return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), prefix);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
