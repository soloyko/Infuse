package dev.blackclover.smp.command;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.LevelCurve;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.MageService;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** /magic and /magic top */
public final class MagicCommand implements TabExecutor {

    /** Extra lines (mana, spells) supplied by the magic system. */
    public interface Details {
        void describe(Player player, GrimoireType type, int level, CommandSender to);
    }

    private final BlackCloverPlugin plugin;
    private final MageService mage;
    private Details details = (p, t, l, to) -> { };
    private java.util.function.Consumer<Player> openBook = p -> { };

    public void setOpenBook(java.util.function.Consumer<Player> openBook) {
        this.openBook = openBook;
    }

    public MagicCommand(BlackCloverPlugin plugin, MageService mage) {
        this.plugin = plugin;
        this.mage = mage;
    }

    public void setDetails(Details details) {
        this.details = details;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            List<Map.Entry<String, Integer>> rows = mage.top(10);
            Msg.send(sender, "<gold>ꜱᴛʀᴏɴɢᴇꜱᴛ ᴍᴀɢᴇꜱ");
            if (rows.isEmpty()) {
                Msg.send(sender, "<gray>Nobody carries a grimoire yet.");
            }
            int rank = 1;
            for (Map.Entry<String, Integer> row : rows) {
                Msg.send(sender, "<yellow>#<rank></yellow>  <white><name></white>  <dark_gray>ʟᴠ</dark_gray> <gray><lv>",
                        Msg.arg("rank", rank++), Msg.arg("name", row.getKey()), Msg.arg("lv", row.getValue()));
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<gray>Usage: /magic top");
            return true;
        }
        if (args.length == 0 && plugin.grimoires().carried(player).isPresent()) {
            openBook.accept(player);
            return true;
        }
        LevelCurve curve = plugin.settings().levelCurve();
        PlayerData pd = mage.data(player.getUniqueId());
        Optional<GrimoireType> carried = plugin.grimoires().carried(player);
        if (carried.isEmpty()) {
            Msg.send(player, "<gray>You carry no grimoire. Find a library past 1,000 blocks from spawn, or take one from a fallen mage.");
        } else {
            GrimoireType type = carried.get();
            int xp = pd.xp(type);
            int level = curve.levelForXp(xp);
            String progress = level >= curve.cap() ? "ᴍᴀx ʟᴇᴠᴇʟ"
                    : (xp - curve.totalXpForLevel(level)) + "/" + curve.xpToNext(level) + " xᴘ";
            Msg.send(player, "<c><b><name> Grimoire</b></c>  <dark_gray>ʟᴠ</dark_gray> <white><lv></white>  <dark_gray><progress>",
                    Msg.color("c", type.rgb()), Msg.arg("name", type.displayName()), Msg.arg("lv", level), Msg.arg("progress", progress));
            details.describe(player, type, level, player);
        }
        StringBuilder others = new StringBuilder();
        for (Map.Entry<GrimoireType, Integer> e : pd.xpView().entrySet()) {
            if (carried.isPresent() && e.getKey() == carried.get()) {
                continue;
            }
            if (!others.isEmpty()) {
                others.append(", ");
            }
            others.append(e.getKey().displayName()).append(" Lv ").append(curve.levelForXp(e.getValue()));
        }
        if (!others.isEmpty()) {
            Msg.send(player, "<dark_gray>ꜱᴛᴏʀᴇᴅ ʟᴇᴠᴇʟꜱ</dark_gray>  <gray><list>", Msg.arg("list", others));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("top", "info") : List.of();
    }
}
