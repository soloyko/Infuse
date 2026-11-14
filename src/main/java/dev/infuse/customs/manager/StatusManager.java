package dev.infuse.customs.manager;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived status effects that aren't potion effects: fall immunity, bleeding, void rescue spots. */
public final class StatusManager {

    private record Bleed(UUID source, int secondsLeft) {
    }

    private final CustomsPlugin plugin;
    private final Map<UUID, Integer> noFallUntil = new HashMap<>();
    private final Map<UUID, Bleed> bleeding = new HashMap<>();
    private final Map<UUID, Location> lastSafe = new HashMap<>();
    private BukkitTask bleedTask;

    public StatusManager(CustomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        bleedTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBleed, 20L, 20L);
    }

    public void shutdown() {
        if (bleedTask != null) {
            bleedTask.cancel();
        }
        noFallUntil.clear();
        bleeding.clear();
        lastSafe.clear();
    }

    // ---- fall immunity ----

    public void protectFromFall(Player player, int ticks) {
        int until = Bukkit.getCurrentTick() + ticks;
        noFallUntil.merge(player.getUniqueId(), until, Math::max);
    }

    public void setFallProtection(Player player, int ticks) {
        noFallUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + ticks);
    }

    public boolean isFallProtected(Player player) {
        Integer until = noFallUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < Bukkit.getCurrentTick()) {
            noFallUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    // ---- bleeding ----

    public void bleed(Player source, LivingEntity target, int seconds) {
        bleeding.merge(target.getUniqueId(), new Bleed(source.getUniqueId(), seconds),
                (old, fresh) -> new Bleed(fresh.source(), Math.max(old.secondsLeft(), fresh.secondsLeft())));
    }

    public void clearBleed(LivingEntity entity) {
        bleeding.remove(entity.getUniqueId());
    }

    private void tickBleed() {
        // Snapshot: damage events may start new bleeds while we iterate.
        for (UUID id : new ArrayList<>(bleeding.keySet())) {
            Bleed bleed = bleeding.get(id);
            Entity entity = Bukkit.getEntity(id);
            if (bleed == null) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || living.isDead() || bleed.secondsLeft() <= 0) {
                bleeding.remove(id);
                continue;
            }
            bleeding.put(id, new Bleed(bleed.source(), bleed.secondsLeft() - 1));
            Location mid = Combat.center(living);
            Fx.particle(mid, Particle.DUST, 12, 0.3, 0, Fx.dust(0x8B0000, 1.2f));
            Fx.particle(mid, Particle.BLOCK, 6, 0.25, 0, Material.REDSTONE_BLOCK.createBlockData());
            Player source = Bukkit.getPlayer(bleed.source());
            if (source != null) {
                plugin.combat().damage(source, living, 1.5);
            } else {
                living.damage(1.5);
            }
        }
    }

    // ---- void rescue ----

    public void recordSafeSpot(Player player) {
        lastSafe.put(player.getUniqueId(), player.getLocation());
    }

    public @Nullable Location safeSpot(Player player) {
        Location loc = lastSafe.get(player.getUniqueId());
        return loc != null && loc.getWorld() == player.getWorld() ? loc.clone() : null;
    }

    public void forget(Player player) {
        noFallUntil.remove(player.getUniqueId());
        lastSafe.remove(player.getUniqueId());
    }
}
