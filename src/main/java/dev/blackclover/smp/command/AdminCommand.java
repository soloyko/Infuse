package dev.blackclover.smp.command;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.gui.AdminMenus;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** /bcadmin — opens the admin GUI, or runs the same actions as text commands. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("possess", "unpossess", "give", "take", "setlevel", "addxp", "grace", "reset", "reload", "pack");

    private final BlackCloverPlugin plugin;
    private final AdminActions actions;
    private final AdminMenus menus;

    public AdminCommand(BlackCloverPlugin plugin, AdminActions actions, AdminMenus menus) {
        this.plugin = plugin;
        this.actions = actions;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                menus.openMain(p);
            } else {
                usage(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "possess" -> withPlayer(sender, args, 1, t -> actions.possess(sender, t));
            case "unpossess" -> actions.unpossess(sender);
            case "give" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("antimagic")) {
                    Msg.send(sender, "<gray>Usage: /bcadmin give <player> antimagic");
                } else {
                    withPlayer(sender, args, 1, t -> actions.giveAntiMagic(sender, t));
                }
            }
            case "take" -> {
                if (args.length < 2) {
                    Msg.send(sender, "<gray>Usage: /bcadmin take <player>");
                    return true;
                }
                Player online = plugin.getServer().getPlayerExact(args[1]);
                if (online != null) {
                    actions.take(sender, online.getUniqueId(), online.getName());
                } else {
                    plugin.players().values().stream().filter(pd -> pd.name().equalsIgnoreCase(args[1])).findFirst()
                            .ifPresentOrElse((PlayerData pd) -> actions.take(sender, pd.id(), pd.name()),
                                    () -> Msg.send(sender, "<red>Unknown player <name>.", Msg.arg("name", args[1])));
                }
            }
            case "setlevel" -> withNumber(sender, args, n -> withPlayer(sender, args, 1, t -> actions.setLevel(sender, t, n)));
            case "addxp" -> withNumber(sender, args, n -> withPlayer(sender, args, 1, t -> actions.addXp(sender, t, n)));
            case "grace" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("end")) {
                    actions.endGrace(sender);
                } else {
                    Msg.send(sender, "<gray>Usage: /bcadmin grace end");
                }
            }
            case "reset" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    actions.reset(sender);
                } else {
                    Msg.send(sender, "<red>This returns every grimoire and clears all levels. Run <white>/bcadmin reset confirm</white> to do it.");
                }
            }
            case "reload" -> actions.reload(sender);
            case "pack" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("resend")) {
                    plugin.pack().resendAll();
                    Msg.send(sender, "<green>Offered the resource pack to everyone online.");
                } else {
                    plugin.pack().describe(sender);
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender sender) {
        Msg.send(sender, "<gray>/bcadmin <white>possess <player></white> | <white>unpossess</white> | <white>give <player> antimagic</white>"
                + " | <white>take <player></white> | <white>setlevel <player> <level></white> | <white>addxp <player> <amount></white>"
                + " | <white>grace end</white> | <white>reset confirm</white> | <white>reload</white> | <white>pack [resend]</white>");
    }

    private void withPlayer(CommandSender sender, String[] args, int index, java.util.function.Consumer<Player> action) {
        if (args.length <= index) {
            usage(sender);
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[index]);
        if (target == null) {
            Msg.send(sender, "<red><name> is not online.", Msg.arg("name", args[index]));
            return;
        }
        action.accept(target);
    }

    private void withNumber(CommandSender sender, String[] args, java.util.function.IntConsumer action) {
        if (args.length < 3) {
            usage(sender);
            return;
        }
        try {
            action.accept(Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red><v> is not a whole number.", Msg.arg("v", args[2]));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "possess", "give", "take", "setlevel", "addxp" -> filter(onlineNames(), args[1]);
                case "grace" -> List.of("end");
                case "reset" -> List.of("confirm");
                case "pack" -> List.of("resend");
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "give" -> List.of("antimagic");
                case "setlevel" -> filter(IntStream.rangeClosed(1, plugin.settings().levelCurve().cap()).mapToObj(String::valueOf).toList(), args[2]);
                case "addxp" -> List.of("100", "500", "1000");
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        plugin.getServer().getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
