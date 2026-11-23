package dev.blackclover.smp.command;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.DurationParser;
import dev.blackclover.smp.game.GameService;
import dev.blackclover.smp.util.Msg;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.OptionalLong;

/** /start &lt;border size&gt; &lt;grace period&gt; */
public final class StartCommand implements TabExecutor {

    private final BlackCloverPlugin plugin;
    private final GameService game;

    public StartCommand(BlackCloverPlugin plugin, GameService game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            Msg.send(sender, "<gray>Usage: <white>/start <border size> <grace period></white> <dark_gray>e.g. /start 6000 30m");
            return true;
        }
        int border;
        try {
            border = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red>Border size must be a whole number of blocks, e.g. 6000.");
            return true;
        }
        OptionalLong grace = DurationParser.parseSeconds(args[1]);
        if (grace.isEmpty()) {
            Msg.send(sender, "<red>Grace period must look like <white>30m</white>, <white>1h</white> or <white>90s</white>.");
            return true;
        }
        World overworld = plugin.getServer().getWorlds().get(0);
        game.start(sender, overworld, border, grace.getAsLong());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return switch (args.length) {
            case 1 -> List.of("4000", "6000", "10000");
            case 2 -> List.of("10m", "30m", "1h");
            default -> List.of();
        };
    }
}
