package dev.lambro.blade;

import dev.lambro.blade.gui.MainMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static dev.lambro.blade.util.Text.*;

public final class LambroCommand implements TabExecutor {

    private final LambroPlugin plugin;

    public LambroCommand(LambroPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(mm(GRADIENT + "<bold>" + tc("lambro") + "</bold></gradient> <" + SHADOW + ">» <" + STEEL + ">" + text));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("lambro.admin")) {
            msg(sender, tc("you can't wield this command."));
            return true;
        }
        String sub = args.length == 0 ? "gui" : args[0].toLowerCase();
        switch (sub) {
            case "gui", "config", "menu" -> {
                if (!(sender instanceof Player p)) { msg(sender, tc("players only.")); return true; }
                new MainMenu(plugin, p).open();
            }
            case "give" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                if (target == null) { msg(sender, tc("player not found.")); return true; }
                target.getInventory().addItem(plugin.blade().create());
                msg(sender, tc("gave the blade to ") + "<" + WHITE + ">" + target.getName());
            }
            case "setkills" -> {
                if (!(sender instanceof Player p) || args.length < 2) { msg(sender, tc("usage: /lambro setkills <amount>")); return true; }
                ItemStack held = p.getInventory().getItemInMainHand();
                if (!plugin.blade().isBlade(held)) { msg(sender, tc("hold the blade first.")); return true; }
                int amount;
                try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException ex) { msg(sender, tc("not a number.")); return true; }
                plugin.blade().setKills(held, amount);
                msg(sender, tc("kills set to ") + "<" + WHITE + ">" + Math.max(0, amount));
            }
            case "reload" -> {
                plugin.reload();
                msg(sender, tc("config reloaded."));
            }
            default -> msg(sender, tc("/lambro [gui | give [player] | setkills <n> | reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("lambro.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("gui", "give", "setkills", "reload")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setkills")) {
            out.addAll(List.of("0", "3", "6", "9", "12", "15", "18", "21", "24"));
        }
        return out;
    }
}
