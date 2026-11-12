package me.infuse.command;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /trust <player>, /trust list, /untrust <player>: trusted players are treated as allies by your abilities. */
public final class TrustCommand implements TabExecutor {
    private final InfusePlugin plugin;

    public TrustCommand(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "only-players");
            return true;
        }
        boolean untrust = command.getName().equalsIgnoreCase("untrust");
        PlayerData d = plugin.data().get(p);
        if (!untrust && (args.length == 0 || args[0].equalsIgnoreCase("list"))) {
            list(p, d);
            return true;
        }
        if (args.length == 0) {
            Msg.send(p, "untrust-usage");
            return true;
        }
        OfflinePlayer target = find(args[0]);
        if (target == null) {
            Msg.send(p, "player-not-found", Msg.text("player", args[0]));
            return true;
        }
        String name = target.getName() == null ? args[0] : target.getName();
        if (target.getUniqueId().equals(p.getUniqueId())) {
            Msg.send(p, "trust-self");
            return true;
        }
        if (untrust) {
            if (!d.trusted().remove(target.getUniqueId())) {
                Msg.send(p, "trust-not-trusted", Msg.text("player", name));
                return true;
            }
            plugin.data().save(d);
            Msg.send(p, "untrusted", Msg.text("player", name));
            if (target.getPlayer() != null) Msg.send(target.getPlayer(), "untrusted-notify", Msg.text("player", p.getName()));
        } else {
            if (d.trusts(target.getUniqueId())) {
                Msg.send(p, "trust-already", Msg.text("player", name));
                return true;
            }
            int max = plugin.getConfig().getInt("trust.max", 0);
            if (max > 0 && d.trusted().size() >= max) {
                Msg.send(p, "trust-full", Msg.text("max", max));
                return true;
            }
            d.trusted().add(target.getUniqueId());
            plugin.data().save(d);
            Msg.send(p, "trusted", Msg.text("player", name));
            if (target.getPlayer() != null) Msg.send(target.getPlayer(), "trusted-notify", Msg.text("player", p.getName()));
        }
        // Thief tablist/vanish visibility depends on trust
        plugin.effects().syncThieves();
        return true;
    }

    private void list(Player p, PlayerData d) {
        if (d.trusted().isEmpty()) {
            Msg.send(p, "trust-list-empty");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : d.trusted()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            names.add(op.getName() == null ? id.toString().substring(0, 8) : op.getName());
        }
        Msg.send(p, "trust-list", Msg.text("count", names.size()), Msg.text("players", String.join(", ", names)));
    }

    private static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player p)) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("untrust")) {
            for (UUID id : plugin.data().get(p).trusted()) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                if (n != null) out.add(n);
            }
        } else {
            out.add("list");
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) out.add(o.getName());
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
