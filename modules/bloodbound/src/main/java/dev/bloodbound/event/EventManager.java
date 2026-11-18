package dev.bloodbound.event;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.Messages;
import dev.bloodbound.config.Settings;
import dev.bloodbound.data.BlockKey;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import static dev.bloodbound.config.Messages.comp;
import static dev.bloodbound.config.Messages.text;

/** Runs at most one event at a time during a session. */
public final class EventManager {
    private final BloodboundPlugin plugin;
    private EventType current;
    private long endsAt;
    private UUID bountyHolder;
    private long bountyHeldSince;
    private long lastBountyPing;
    private long lastSurge;
    private BukkitTask ticker;

    public EventManager(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<EventType> current() {
        return Optional.ofNullable(current);
    }

    public Component displayName(EventType type) {
        return plugin.messages().get("event.names." + type.key());
    }

    public BossBar.Color barColor(EventType type) {
        return type.color();
    }

    public double bloodMultiplier() {
        return current == EventType.BLOOD_MOON ? plugin.settings().bloodMoonMultiplier() : 1.0;
    }

    public double cooldownMultiplier() {
        return current == EventType.FRENZY ? plugin.settings().frenzyCooldownMultiplier() : 1.0;
    }

    public boolean isBountyHolder(UUID id) {
        return current == EventType.BOUNTY && id.equals(bountyHolder);
    }

    public enum StartResult { OK, NO_SESSION, NO_BOUNTY_TARGET }

    public StartResult start(EventType type, long durationMillis, Player bountyTarget) {
        if (!plugin.sessions().isActive()) return StartResult.NO_SESSION;
        Player target = null;
        if (type == EventType.BOUNTY) {
            target = bountyTarget != null ? bountyTarget : topOnline(null);
            if (target == null) return StartResult.NO_BOUNTY_TARGET;
        }
        stopCurrent(false);

        current = type;
        endsAt = System.currentTimeMillis() + durationMillis;
        lastSurge = System.currentTimeMillis();
        Messages m = plugin.messages();
        Component name = displayName(type);
        m.send(Bukkit.getServer(), "event.started", comp("event", name),
                text("description", m.raw("event.descriptions." + type.key())));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(net.kyori.adventure.title.Title.title(name,
                    m.render("<gray>" + m.raw("event.descriptions." + type.key()))));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1.0f);
        }

        switch (type) {
            case BLOOD_MOON -> {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getEnvironment() == World.Environment.NORMAL) w.setTime(13_000);
                }
            }
            case BOUNTY -> assignBounty(target, false);
            default -> { }
        }

        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.sessions().refreshBar();
        return StartResult.OK;
    }

    /** Ends the current event, clearing every effect it applied. */
    public boolean stopCurrent(boolean announce) {
        if (current == null) return false;
        EventType ended = current;
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        clearBountyGlow();
        bountyHolder = null;
        current = null;
        if (announce) plugin.messages().send(Bukkit.getServer(), "event.ended", comp("event", displayName(ended)));
        plugin.sessions().refreshBar();
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (now >= endsAt) {
            stopCurrent(true);
            return;
        }
        Settings s = plugin.settings();
        if (current == EventType.BOUNTY) tickBounty(now, s);
        if (current == EventType.ALTAR_SURGE && now - lastSurge >= s.surgeIntervalMillis()) {
            lastSurge = now;
            tickSurge(s);
        }
    }

    private void tickBounty(long now, Settings s) {
        Player holder = bountyHolder == null ? null : Bukkit.getPlayer(bountyHolder);
        if (holder == null) {
            reassignBounty(null);
            return;
        }
        holder.setGlowing(true);
        if (now - bountyHeldSince >= 60_000) {
            bountyHeldSince = now;
            plugin.blood().add(holder, s.bountySurvivePerMinute(), "blood.reason-survive");
        }
        if (now - lastBountyPing >= s.bountyBroadcastMillis()) {
            lastBountyPing = now;
            Location l = holder.getLocation();
            plugin.messages().send(Bukkit.getServer(), "event.bounty-ping", text("player", holder.getName()),
                    text("x", l.getBlockX()), text("y", l.getBlockY()), text("z", l.getBlockZ()),
                    text("world", l.getWorld().getName()));
        }
    }

    private void tickSurge(Settings s) {
        double r2 = s.surgeRadius() * s.surgeRadius();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            for (BlockKey key : plugin.altars().all()) {
                Location c = key.center();
                if (c != null && c.getWorld().equals(p.getWorld()) && c.distanceSquared(p.getLocation()) <= r2) {
                    plugin.blood().add(p, s.surgeBlood(), "blood.reason-surge");
                    break;
                }
            }
        }
    }

    /** Called when the bounty holder is killed by a player: the killer inherits the bounty. */
    public void onBountyKilled(Player victim, Player killer) {
        if (!isBountyHolder(victim.getUniqueId())) return;
        plugin.blood().add(killer, plugin.settings().bountyKillBonus(), "blood.reason-bounty");
        plugin.messages().send(Bukkit.getServer(), "event.bounty-claimed",
                text("killer", killer.getName()), text("player", victim.getName()));
        clearBountyGlow();
        assignBounty(killer, false);
    }

    /** Moves the bounty to the top online player other than {@code leaving}, or ends the event. */
    public void reassignBounty(Player leaving) {
        if (current != EventType.BOUNTY) return;
        clearBountyGlow();
        Player next = topOnline(leaving);
        if (next == null) {
            stopCurrent(true);
            return;
        }
        assignBounty(next, true);
    }

    private void assignBounty(Player target, boolean announce) {
        bountyHolder = target.getUniqueId();
        bountyHeldSince = System.currentTimeMillis();
        lastBountyPing = 0;
        target.setGlowing(true);
        if (announce) plugin.messages().send(Bukkit.getServer(), "event.bounty-moved", text("player", target.getName()));
    }

    private void clearBountyGlow() {
        if (bountyHolder == null) return;
        Player p = Bukkit.getPlayer(bountyHolder);
        if (p != null) p.setGlowing(false);
    }

    private Player topOnline(Player exclude) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> exclude == null || !p.equals(exclude))
                .filter(p -> p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                .max(Comparator.comparingInt(p -> plugin.players().get(p).blood()))
                .map(p -> (Player) p)
                .orElse(null);
    }

    public long remainingMillis() {
        return current == null ? 0 : Math.max(0, endsAt - System.currentTimeMillis());
    }

}
