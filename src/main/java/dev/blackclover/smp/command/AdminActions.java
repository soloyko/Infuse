package dev.blackclover.smp.command;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.LevelCurve;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Admin operations shared by /bcadmin and the admin GUI. Each reports its result to {@code sender}. */
public final class AdminActions {

    private final BlackCloverPlugin plugin;

    public AdminActions(BlackCloverPlugin plugin) {
        this.plugin = plugin;
    }

    public void possess(CommandSender sender, Player target) {
        String error = plugin.demon().possess(target);
        if (error != null) {
            Msg.send(sender, "<red><e>", Msg.arg("e", error));
        } else {
            Msg.send(sender, "<green><name> is now the Demon Possessed.", Msg.arg("name", target.getName()));
        }
    }

    public void unpossess(CommandSender sender) {
        if (plugin.demon().demon().isEmpty()) {
            Msg.send(sender, "<gray>Nobody is possessed right now.");
            return;
        }
        plugin.demon().end(true);
        Msg.send(sender, "<green>Possession ended. The Five-Leaf Grimoire is back in the admin pool.");
    }

    public void giveAntiMagic(CommandSender sender, Player target) {
        GrimoireState state = plugin.registry().get(GrimoireType.ANTI_MAGIC);
        if (state.status() != GrimoireStatus.ADMIN_POOL) {
            Msg.send(sender, "<red>Anti-Magic isn't in the admin pool (<status>). Take it back first.",
                    Msg.arg("status", describe(state)));
            return;
        }
        Optional<GrimoireType> carried = plugin.grimoires().carried(target);
        if (carried.isEmpty() && target.getInventory().firstEmpty() < 0) {
            Msg.send(sender, "<red><name> needs a free inventory slot.", Msg.arg("name", target.getName()));
            return;
        }
        if (carried.isPresent() && carried.get() == GrimoireType.FIVE_LEAF && target.getUniqueId().equals(plugin.demonRef().get())) {
            Msg.send(sender, "<red>The Demon Possessed can't receive Anti-Magic. End the possession first.");
            return;
        }
        carried.ifPresent(t -> plugin.grimoires().returnHome(t, "replaced by Anti-Magic"));
        plugin.registry().giveFromPool(GrimoireType.ANTI_MAGIC, target.getUniqueId());
        plugin.grimoires().give(target, GrimoireType.ANTI_MAGIC);
        Msg.titleAll("<gray><b>Anti-Magic</b>", "<white><name></white> <gray>now wields the Anti-Magic grimoire", Msg.arg("name", target.getName()));
        Msg.send(sender, "<green>Gave Anti-Magic to <name>.", Msg.arg("name", target.getName()));
        plugin.saveAsync();
    }

    public void take(CommandSender sender, UUID target, String name) {
        Optional<GrimoireType> held = plugin.registry().heldBy(target);
        if (held.isEmpty()) {
            Msg.send(sender, "<gray><name> doesn't carry a grimoire.", Msg.arg("name", name));
            return;
        }
        plugin.grimoires().returnHome(held.get(), "taken by an admin");
        Msg.send(sender, "<green>The <type> grimoire was returned home.", Msg.arg("type", held.get().displayName()));
    }

    public void setLevel(CommandSender sender, Player target, int level) {
        Optional<GrimoireType> held = plugin.grimoires().carried(target);
        if (held.isEmpty()) {
            Msg.send(sender, "<red><name> doesn't carry a grimoire. Levels belong to a grimoire.", Msg.arg("name", target.getName()));
            return;
        }
        LevelCurve curve = plugin.settings().levelCurve();
        int clamped = Math.max(1, Math.min(curve.cap(), level));
        plugin.mage().setXp(target, held.get(), curve.totalXpForLevel(clamped));
        Msg.send(sender, "<green><name> is now level <lv> on <type>.", Msg.arg("name", target.getName()),
                Msg.arg("lv", clamped), Msg.arg("type", held.get().displayName()));
        plugin.saveAsync();
    }

    public void addXp(CommandSender sender, Player target, int amount) {
        Optional<GrimoireType> held = plugin.grimoires().carried(target);
        if (held.isEmpty()) {
            Msg.send(sender, "<red><name> doesn't carry a grimoire.", Msg.arg("name", target.getName()));
            return;
        }
        plugin.mage().addXp(target, held.get(), amount);
        Msg.send(sender, "<green>Gave <n> XP to <name>.", Msg.arg("n", amount), Msg.arg("name", target.getName()));
        plugin.saveAsync();
    }

    public void clearXp(CommandSender sender, Player target) {
        plugin.grimoires().carried(target).ifPresentOrElse(type -> {
            plugin.mage().setXp(target, type, 0);
            Msg.send(sender, "<green>Cleared <name>'s XP on <type>.", Msg.arg("name", target.getName()), Msg.arg("type", type.displayName()));
            plugin.saveAsync();
        }, () -> Msg.send(sender, "<red><name> doesn't carry a grimoire.", Msg.arg("name", target.getName())));
    }

    public void endGrace(CommandSender sender) {
        if (!plugin.gameService().isGrace()) {
            Msg.send(sender, "<gray>There is no grace period running.");
            return;
        }
        plugin.gameService().endGrace();
        Msg.send(sender, "<green>Grace period ended.");
    }

    public void reset(CommandSender sender) {
        plugin.gameService().reset(sender);
    }

    public void reload(CommandSender sender) {
        plugin.reloadSettings();
        Msg.send(sender, "<green>Configuration reloaded.");
    }

    /** Human-readable status of a grimoire. */
    public String describe(GrimoireState s) {
        return switch (s.status()) {
            case LIBRARY -> plugin.game().libraries().containsKey(s.type()) ? "waiting in its library" : "not placed yet (run /start)";
            case ADMIN_POOL -> "in the admin pool";
            case HELD -> "held by " + plugin.mage().data(s.holder()).name()
                    + (plugin.getServer().getPlayer(s.holder()) == null ? " (offline)" : "");
            case GROUND -> s.ground() == null ? "on the ground"
                    : String.format("on the ground at %d %d %d", (int) s.ground().x(), (int) s.ground().y(), (int) s.ground().z());
        };
    }
}
