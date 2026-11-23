package dev.blackclover.smp.command;

import dev.blackclover.smp.gui.SquadMenu;
import dev.blackclover.smp.squad.Squad;
import dev.blackclover.smp.squad.SquadRoster;
import dev.blackclover.smp.squad.SquadService;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** /squad (alias /sq) and /sc for squad chat. */
public final class SquadCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("create", "invite", "join", "leave", "kick", "captain", "color",
            "chat", "info", "top", "disband", "help");
    private final SquadService squads;
    private final SquadMenu menu;

    public SquadCommand(SquadService squads, SquadMenu menu) {
        this.squads = squads;
        this.menu = menu;
    }

    private static String rest(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, Math.min(from, args.length), args.length)).trim();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "<red>Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("sc")) {
            if (args.length == 0) {
                squads.toggleChat(p);
            } else {
                squads.squadChat(p, Component.text(rest(args, 0)));
            }
            return true;
        }
        if (args.length == 0) {
            menu.open(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>Usage: <white>/squad create <name>");
                } else {
                    squads.create(p, rest(args, 1));
                }
            }
            case "invite" -> {
                Player target = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Msg.send(p, "<red>Usage: <white>/squad invite <online player>");
                } else {
                    squads.invite(p, target);
                }
            }
            case "join", "accept" -> squads.join(p, rest(args, 1));
            case "leave" -> squads.leave(p);
            case "kick", "captain" -> {
                Optional<UUID> target = args.length < 2 ? Optional.empty() : squads.memberByName(p, args[1]);
                if (target.isEmpty()) {
                    Msg.send(p, "<red>Usage: <white>/squad " + sub + " <squad member>");
                } else if (sub.equals("kick")) {
                    squads.kick(p, target.get());
                } else {
                    squads.transfer(p, target.get());
                }
            }
            case "color", "colour" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>Usage: <white>/squad color <" + String.join("|", SquadRoster.COLORS) + ">");
                } else {
                    squads.color(p, args[1]);
                }
            }
            case "chat" -> {
                if (args.length < 2) {
                    squads.toggleChat(p);
                } else {
                    squads.squadChat(p, Component.text(rest(args, 1)));
                }
            }
            case "info" -> info(p, args.length < 2 ? squads.squadOf(p) : squads.roster().byName(rest(args, 1)));
            case "top" -> top(p);
            case "disband" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    Msg.send(p, "<red>This removes everyone and loses the stars. Type <white>/squad disband confirm</white>.");
                } else {
                    squads.disband(p);
                }
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        Msg.send(p, "<gold><b>" + Msg.sc("Magic Knight squads") + "</b>");
        for (String line : List.of(
                "/squad <dark_gray>open the squad menu",
                "/squad create <name> <dark_gray>found a squad",
                "/squad invite <player> <dark_gray>invite someone (captain)",
                "/squad join [squad] <dark_gray>accept an invite",
                "/squad leave <dark_gray>leave your squad",
                "/squad kick | captain <member> <dark_gray>manage members (captain)",
                "/squad color <colour> <dark_gray>squad colour (captain)",
                "/sc [message] <dark_gray>squad chat (toggle, or send one message)",
                "/squad info [squad] · /squad top <dark_gray>details and ranking")) {
            p.sendMessage(Msg.mm("  <white>" + line));
        }
    }

    private void info(Player p, Optional<Squad> squad) {
        if (squad.isEmpty()) {
            Msg.send(p, "<red>No such squad. <white>/squad top</white> lists them.");
            return;
        }
        Squad s = squad.get();
        Msg.send(p, SquadService.tag(s) + " <dark_gray>·</dark_gray> <gold>★ " + s.stars() + "</gold> <dark_gray>·</dark_gray> <gray>"
                + Msg.sc("rank") + " <white>#" + squads.roster().rank(s));
        List<String> names = new ArrayList<>();
        for (UUID id : s.members()) {
            String n = squads.nameOf(id);
            names.add((Bukkit.getPlayer(id) != null ? "<green>" : "<gray>") + (id.equals(s.captain()) ? "♛ " : "") + n);
        }
        p.sendMessage(Msg.mm("  " + String.join("<dark_gray>, ", names)));
    }

    private void top(Player p) {
        List<Squad> ranking = squads.roster().ranking();
        if (ranking.isEmpty()) {
            Msg.send(p, "<gray>No squads yet. Found one with <white>/squad create <name></white>.");
            return;
        }
        Msg.send(p, "<gold><b>" + Msg.sc("Squad ranking") + "</b>");
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Squad s = ranking.get(i);
            p.sendMessage(Msg.mm("  <dark_gray>#" + (i + 1) + "</dark_gray> " + SquadService.tag(s) + "  <gold>★ " + s.stars()
                    + "</gold> <dark_gray>· " + s.size() + " " + Msg.sc(s.size() == 1 ? "member" : "members")));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sc") || !(sender instanceof Player p)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "invite" -> filter(Bukkit.getOnlinePlayers().stream()
                        .filter(o -> squads.squadOf(o).isEmpty() && !o.equals(p)).map(Player::getName).toList(), args[1]);
                case "kick", "captain" -> filter(squads.squadOf(p).map(s -> s.members().stream()
                        .filter(id -> !id.equals(p.getUniqueId())).map(squads::nameOf).toList()).orElse(List.of()), args[1]);
                case "color", "colour" -> filter(SquadRoster.COLORS, args[1]);
                case "join", "accept" -> filter(squads.roster().invitesFor(p.getUniqueId(), System.currentTimeMillis())
                        .stream().map(Squad::name).toList(), args[1]);
                case "info" -> filter(squads.roster().ranking().stream().map(Squad::name).toList(), args[1]);
                case "disband" -> List.of("confirm");
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
