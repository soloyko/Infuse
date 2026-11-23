package dev.blackclover.smp.squad;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.mage.CombatListener;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.squad.SquadRoster.Result;
import dev.blackclover.smp.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Magic Knight squads in game: commands, squad chat, friendly fire and stars. */
public final class SquadService implements Listener {

    private final BlackCloverPlugin plugin;
    private final SquadRoster roster;
    private final Set<UUID> chatMode = ConcurrentHashMap.newKeySet();

    public SquadService(BlackCloverPlugin plugin, SquadRoster roster) {
        this.plugin = plugin;
        this.roster = roster;
    }

    public SquadRoster roster() {
        return roster;
    }

    public Optional<Squad> squadOf(Player p) {
        return roster.squadOf(p.getUniqueId());
    }

    public boolean allies(Player a, Player b) {
        return roster.allies(a.getUniqueId(), b.getUniqueId());
    }

    public static NamedTextColor color(Squad squad) {
        NamedTextColor c = NamedTextColor.NAMES.value(squad.color());
        return c == null ? NamedTextColor.WHITE : c;
    }

    /** {@code <red>Black Bulls</red>} style MiniMessage for a squad's name. */
    public static String tag(Squad squad) {
        return "<" + squad.color() + ">" + squad.name() + "</" + squad.color() + ">";
    }

    public String nameOf(UUID id) {
        PlayerData pd = plugin.players().get(id);
        if (pd != null) {
            return pd.name();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    public boolean inChatMode(Player p) {
        return chatMode.contains(p.getUniqueId());
    }

    // ---------------------------------------------------------------- messages

    /** Sends to every online member, prefixed with the squad's name. */
    public void tellSquad(Squad squad, String text, TagResolver... args) {
        Component msg = Msg.mm("<" + squad.color() + ">☘ " + Msg.sc(squad.name()) + "</" + squad.color() + "> <dark_gray>»</dark_gray> <gray>" + text, args);
        for (UUID id : squad.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(msg);
            }
        }
    }

    private void explain(CommandSender to, Result r, String target) {
        String text = switch (r) {
            case OK -> null;
            case ALREADY_IN_SQUAD -> "<red>You're already in a squad. Leave it first with <white>/squad leave</white>.";
            case NOT_IN_SQUAD -> "<red>You're not in a squad. Found one with <white>/squad create <name></white>.";
            case NOT_CAPTAIN -> "<red>Only the squad captain can do that.";
            case BAD_NAME -> "<red>Squad names are " + SquadRoster.NAME_MIN + "–" + SquadRoster.NAME_MAX
                    + " letters and numbers, with single spaces.";
            case NAME_TAKEN -> "<red>A squad with that name already exists.";
            case FULL -> "<red>That squad is full (" + roster.maxMembers() + " members).";
            case NO_INVITE -> "<red>You don't have an invite from that squad.";
            case TARGET_IN_SQUAD -> "<red><white><t></white> is already in a squad.";
            case TARGET_NOT_MEMBER -> "<red><white><t></white> isn't in your squad.";
            case SELF -> "<red>You can't do that to yourself.";
            case UNKNOWN_SQUAD -> "<red>There's no squad called <white><t></white>.";
            case BAD_COLOR -> "<red>Pick one of: <white>" + String.join(", ", SquadRoster.COLORS) + "</white>.";
        };
        if (text != null) {
            Msg.send(to, text, Msg.arg("t", target == null ? "" : target));
        }
    }

    private static void ding(Player p, float pitch) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, pitch);
    }

    // ---------------------------------------------------------------- actions

    public void create(Player p, String name) {
        Result r = roster.create(p.getUniqueId(), name.trim(), System.currentTimeMillis());
        if (r != Result.OK) {
            explain(p, r, name);
            return;
        }
        Squad s = roster.squadOf(p.getUniqueId()).orElseThrow();
        Msg.broadcast("<white><p></white> founded the Magic Knight squad " + tag(s) + "!", Msg.arg("p", p.getName()));
        Msg.send(p, "<gray>Invite members with <white>/squad invite <player></white>. Open the squad menu with <white>/squad</white>.");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        plugin.saveAsync();
    }

    public void invite(Player p, Player target) {
        Result r = roster.invite(p.getUniqueId(), target.getUniqueId(), System.currentTimeMillis());
        if (r != Result.OK) {
            explain(p, r, target.getName());
            return;
        }
        Squad s = roster.squadOf(p.getUniqueId()).orElseThrow();
        long minutes = Math.max(1, plugin.settings().squadInviteMillis() / 60_000);
        Msg.send(p, "<green>Invited <white><t></white> to " + tag(s) + ". The invite lasts " + minutes + " min.",
                Msg.arg("t", target.getName()));
        Msg.send(target, "<white><p></white> invited you to join " + tag(s) + "  "
                        + "<click:run_command:'/squad join " + s.name() + "'><hover:show_text:'<green>Join " + s.name() + "'>"
                        + "<green><b>[ᴊᴏɪɴ]</b></green></hover></click>",
                Msg.arg("p", p.getName()));
        ding(target, 1.6f);
    }

    public void join(Player p, String name) {
        long now = System.currentTimeMillis();
        if (name == null || name.isBlank()) {
            List<Squad> invites = roster.invitesFor(p.getUniqueId(), now);
            if (invites.size() != 1) {
                Msg.send(p, invites.isEmpty() ? "<red>You have no squad invites." : "<red>You have several invites: <white>/squad join <squad></white>.");
                return;
            }
            name = invites.get(0).name();
        }
        Result r = roster.join(p.getUniqueId(), name, now);
        if (r != Result.OK) {
            explain(p, r, name);
            return;
        }
        chatMode.remove(p.getUniqueId());
        Squad s = roster.squadOf(p.getUniqueId()).orElseThrow();
        tellSquad(s, "<white><p></white> joined the squad!", Msg.arg("p", p.getName()));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        plugin.saveAsync();
    }

    public void leave(Player p) {
        Optional<Squad> before = roster.squadOf(p.getUniqueId());
        if (before.isEmpty()) {
            explain(p, Result.NOT_IN_SQUAD, null);
            return;
        }
        Squad s = before.get();
        boolean wasCaptain = s.captain().equals(p.getUniqueId());
        roster.leave(p.getUniqueId());
        chatMode.remove(p.getUniqueId());
        if (s.size() == 0) {
            Msg.broadcast(tag(s) + " <gray>has been disbanded.");
        } else {
            tellSquad(s, "<white><p></white> left the squad.", Msg.arg("p", p.getName()));
            if (wasCaptain) {
                tellSquad(s, "<white><c></white> is the new captain.", Msg.arg("c", nameOf(s.captain())));
            }
        }
        Msg.send(p, "<gray>You left " + tag(s) + ".");
        plugin.saveAsync();
    }

    /** Finds a member of the player's squad by name (works for offline members). */
    public Optional<UUID> memberByName(Player p, String name) {
        return roster.squadOf(p.getUniqueId()).flatMap(s -> s.members().stream()
                .filter(id -> nameOf(id).equalsIgnoreCase(name)).findFirst());
    }

    public void kick(Player p, UUID target) {
        String name = nameOf(target);
        Result r = roster.kick(p.getUniqueId(), target);
        if (r != Result.OK) {
            explain(p, r, name);
            return;
        }
        chatMode.remove(target);
        Squad s = roster.squadOf(p.getUniqueId()).orElseThrow();
        tellSquad(s, "<white><t></white> was removed from the squad.", Msg.arg("t", name));
        Player t = Bukkit.getPlayer(target);
        if (t != null) {
            Msg.send(t, "<red>You were removed from " + tag(s) + ".");
        }
        plugin.saveAsync();
    }

    public void transfer(Player p, UUID target) {
        Result r = roster.transfer(p.getUniqueId(), target);
        if (r != Result.OK) {
            explain(p, r, nameOf(target));
            return;
        }
        Squad s = roster.squadOf(p.getUniqueId()).orElseThrow();
        tellSquad(s, "<white><t></white> is the new captain.", Msg.arg("t", nameOf(target)));
        plugin.saveAsync();
    }

    public void color(Player p, String color) {
        Result r = roster.setColor(p.getUniqueId(), color);
        if (r != Result.OK) {
            explain(p, r, color);
            return;
        }
        Squad s = roster.squadOf(p.getUniqueId()).orElseThrow();
        tellSquad(s, "The squad colour is now <" + s.color() + ">" + s.color().replace('_', ' ') + "</" + s.color() + ">.");
        plugin.saveAsync();
    }

    public void disband(Player p) {
        Optional<Squad> s = roster.squadOf(p.getUniqueId());
        Result r = roster.disband(p.getUniqueId());
        if (r != Result.OK) {
            explain(p, r, null);
            return;
        }
        for (UUID m : s.get().members()) {
            chatMode.remove(m);
        }
        Msg.broadcast(tag(s.get()) + " <gray>has been disbanded.");
        plugin.saveAsync();
    }

    /** Admin: delete a squad by name. */
    public boolean adminDisband(CommandSender sender, String name) {
        Optional<Squad> s = roster.byName(name);
        if (s.isEmpty()) {
            explain(sender, Result.UNKNOWN_SQUAD, name);
            return false;
        }
        roster.delete(s.get());
        Msg.broadcast(tag(s.get()) + " <gray>was disbanded by an admin.");
        plugin.saveAsync();
        return true;
    }

    /** Admin: add (or remove) stars. */
    public boolean adminStars(CommandSender sender, String name, int amount) {
        Optional<Squad> s = roster.byName(name);
        if (s.isEmpty()) {
            explain(sender, Result.UNKNOWN_SQUAD, name);
            return false;
        }
        int total = roster.addStars(s.get(), amount);
        Msg.send(sender, tag(s.get()) + " <gray>now has <gold>★ " + total + "</gold>.");
        plugin.saveAsync();
        return true;
    }

    public void toggleChat(Player p) {
        if (roster.squadOf(p.getUniqueId()).isEmpty()) {
            explain(p, Result.NOT_IN_SQUAD, null);
            return;
        }
        if (chatMode.remove(p.getUniqueId())) {
            Msg.send(p, "<gray>Squad chat <red>off</red>. Your messages go to everyone.");
        } else {
            chatMode.add(p.getUniqueId());
            Msg.send(p, "<gray>Squad chat <green>on</green>. Only your squad sees your messages. <white>/sc</white> to switch back.");
        }
    }

    public void squadChat(Player p, Component message) {
        Optional<Squad> s = roster.squadOf(p.getUniqueId());
        if (s.isEmpty()) {
            chatMode.remove(p.getUniqueId());
            explain(p, Result.NOT_IN_SQUAD, null);
            return;
        }
        Squad squad = s.get();
        Component line = Msg.mm("<" + squad.color() + ">[" + Msg.sc("Squad") + "]</" + squad.color() + "> <white><p></white> <dark_gray>»</dark_gray> <msg>",
                Msg.arg("p", p.getName()), Placeholder.component("msg", message.colorIfAbsent(NamedTextColor.GREEN)));
        for (UUID id : squad.members()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) {
                m.sendMessage(line);
            }
        }
        plugin.getLogger().info("[Squad " + squad.name() + "] " + p.getName() + ": "
                + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message));
    }

    // ---------------------------------------------------------------- stars

    /** A member killed someone outside the squad (not a repeat kill). */
    public void onKill(Player killer, Player victim) {
        if (allies(killer, victim)) {
            return;
        }
        Optional<Squad> s = squadOf(killer);
        if (s.isEmpty()) {
            return;
        }
        int stars = plugin.settings().starsPerKill();
        if (stars <= 0) {
            return;
        }
        roster.addStars(s.get(), stars);
        tellSquad(s.get(), "<gold>+" + stars + " ★</gold> <white><k></white> defeated <white><v></white>.",
                Msg.arg("k", killer.getName()), Msg.arg("v", victim.getName()));
    }

    public void onGrimoireChosen(Player p) {
        award(p, plugin.settings().starsPerGrimoire(), "was chosen by a grimoire");
    }

    public void onDemonSlain(Player killer) {
        award(killer, plugin.settings().starsPerDemon(), "slew the Demon Possessed");
    }

    private void award(Player p, int stars, String why) {
        Optional<Squad> s = squadOf(p);
        if (s.isEmpty() || stars <= 0) {
            return;
        }
        roster.addStars(s.get(), stars);
        tellSquad(s.get(), "<gold>+" + stars + " ★</gold> <white><p></white> " + why + ".", Msg.arg("p", p.getName()));
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (plugin.settings().friendlyFire() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatListener.attackerOf(event.getDamager());
        if (attacker != null && allies(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!chatMode.contains(p.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Component message = event.message();
        Bukkit.getScheduler().runTask(plugin, () -> squadChat(p, message));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        List<Squad> invites = roster.invitesFor(p.getUniqueId(), System.currentTimeMillis());
        if (!invites.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Squad s : invites) {
                names.add(tag(s));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> Msg.send(p, "<gray>Squad invites waiting: " + String.join(", ", names)
                    + " <dark_gray>(/squad)"), 40);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatMode.remove(event.getPlayer().getUniqueId());
    }

    public static String colorLabel(String color) {
        return color.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
