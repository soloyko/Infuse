package dev.blackclover.smp.magic;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Spell statuses: silence, freeze, root, spell immunity, strippable buffs, Venom Blade, Rewind history, Recall marks. */
public final class StatusService implements Listener {

    public record Snapshot(Location location, double health, long time) {
    }

    private record Buff(long until, List<PotionEffectType> effects) {
    }

    private record Venom(int charges, long until) {
    }

    private record Mark(Location location, long until) {
    }

    private static final long HISTORY_MILLIS = 5_000;

    private final BlackCloverPlugin plugin;
    private final Map<UUID, Long> silenced = new HashMap<>();
    private final Map<UUID, Long> frozen = new HashMap<>();
    private final Map<UUID, Long> rooted = new HashMap<>();
    private final Map<UUID, Long> immune = new HashMap<>();
    private final Map<UUID, Map<String, Buff>> buffs = new HashMap<>();
    private final Map<UUID, Venom> venom = new HashMap<>();
    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();
    private final Map<UUID, Mark> marks = new HashMap<>();

    public StatusService(BlackCloverPlugin plugin) {
        this.plugin = plugin;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long until(int ticks) {
        return now() + ticks * 50L;
    }

    private static boolean active(Map<UUID, Long> map, UUID id) {
        Long t = map.get(id);
        return t != null && t > now();
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::recordHistory, 5L, 5L);
    }

    // ---------------------------------------------------------------- control effects

    public void silence(LivingEntity e, int ticks) {
        silenced.merge(e.getUniqueId(), until(ticks), Math::max);
    }

    public void freeze(LivingEntity e, int ticks) {
        frozen.merge(e.getUniqueId(), until(ticks), Math::max);
        holdMob(e, ticks);
    }

    public void root(LivingEntity e, int ticks) {
        rooted.merge(e.getUniqueId(), until(ticks), Math::max);
        holdMob(e, ticks);
    }

    private static void holdMob(LivingEntity e, int ticks) {
        e.setVelocity(new org.bukkit.util.Vector(0, Math.min(0, e.getVelocity().getY()), 0));
        if (!(e instanceof Player)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 127, false, false, false));
        }
    }

    public void immune(LivingEntity e, int ticks) {
        immune.merge(e.getUniqueId(), until(ticks), Math::max);
    }

    public boolean isSilenced(UUID id) {
        return active(silenced, id);
    }

    public boolean isFrozen(UUID id) {
        return active(frozen, id);
    }

    public boolean isRooted(UUID id) {
        return active(rooted, id);
    }

    public boolean isImmune(UUID id) {
        return active(immune, id);
    }

    // ---------------------------------------------------------------- buffs

    /** Applies potion effects and remembers them as a spell buff that Anti-Magic can strip. */
    public void addBuff(LivingEntity e, String key, int ticks, PotionEffect... effects) {
        List<PotionEffectType> types = new ArrayList<>();
        for (PotionEffect pe : effects) {
            e.addPotionEffect(pe);
            types.add(pe.getType());
        }
        buffs.computeIfAbsent(e.getUniqueId(), k -> new HashMap<>()).put(key, new Buff(until(ticks), types));
    }

    public boolean hasBuff(UUID id, String key) {
        Map<String, Buff> m = buffs.get(id);
        Buff b = m == null ? null : m.get(key);
        return b != null && b.until() > now();
    }

    /** Removes every active spell buff (and its potion effects) from {@code e}. True if any were removed. */
    public boolean stripBuffs(LivingEntity e) {
        Map<String, Buff> m = buffs.remove(e.getUniqueId());
        boolean any = false;
        if (m != null) {
            for (Buff b : m.values()) {
                if (b.until() > now()) {
                    any = true;
                    b.effects().forEach(e::removePotionEffect);
                }
            }
        }
        immune.remove(e.getUniqueId());
        venom.remove(e.getUniqueId());
        return any;
    }

    public void setVenom(Player p, int charges, int ticks) {
        venom.put(p.getUniqueId(), new Venom(charges, until(ticks)));
    }

    // ---------------------------------------------------------------- Rewind / Recall

    private void recordHistory() {
        long t = now();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.grimoires().carried(p).orElse(null) != GrimoireType.TIME) {
                history.remove(p.getUniqueId());
                continue;
            }
            Deque<Snapshot> d = history.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
            d.addLast(new Snapshot(p.getLocation(), p.getHealth(), t));
            while (!d.isEmpty() && t - d.peekFirst().time() > HISTORY_MILLIS + 500) {
                d.pollFirst();
            }
        }
    }

    /** Where the player was about five seconds ago (or the oldest point recorded). */
    public Optional<Snapshot> history5s(UUID id) {
        Deque<Snapshot> d = history.get(id);
        if (d == null || d.isEmpty()) {
            return Optional.empty();
        }
        long target = now() - HISTORY_MILLIS;
        Snapshot best = d.peekFirst();
        for (Snapshot s : d) {
            if (s.time() <= target) {
                best = s;
            }
        }
        return Optional.of(best);
    }

    public void setMark(UUID id, Location loc, int ticks) {
        marks.put(id, new Mark(loc.clone(), until(ticks)));
    }

    public Optional<Location> takeMark(UUID id) {
        Mark m = marks.remove(id);
        return m == null || m.until() <= now() ? Optional.empty() : Optional.of(m.location());
    }

    // ---------------------------------------------------------------- listeners

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        boolean freeze = isFrozen(id);
        boolean root = !freeze && isRooted(id);
        if (!freeze && !root) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        Location fixed = from.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        if (root && to.getY() < from.getY()) {
            fixed.setY(to.getY());
        }
        event.setTo(fixed);
    }

    /** Melee riders: Venom Blade, Thunder God, Black Form. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (Fx.spellDamage || Fx.probing || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        UUID id = attacker.getUniqueId();
        Venom v = venom.get(id);
        if (v != null && v.until() > now() && v.charges() > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
            venom.put(id, new Venom(v.charges() - 1, v.until()));
        }
        if (hasBuff(id, "thunder_god")) {
            event.setDamage(event.getDamage() + 4 * (1 + 0.01 * (plugin.mage().carriedLevel(attacker) - 1)));
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }
        if (hasBuff(id, "black_form")) {
            silence(victim, 40);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void clear(UUID id) {
        silenced.remove(id);
        frozen.remove(id);
        rooted.remove(id);
        immune.remove(id);
        buffs.remove(id);
        venom.remove(id);
        history.remove(id);
        marks.remove(id);
    }
}
