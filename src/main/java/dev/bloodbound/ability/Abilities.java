package dev.bloodbound.ability;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for ability targeting, damage and movement. */
public final class Abilities {
    private static boolean dealingAbilityDamage;

    private Abilities() {}

    /** True while an ability is dealing damage, so damage listeners don't re-trigger passives. */
    public static boolean isAbilityDamage() {
        return dealingAbilityDamage;
    }

    /** Damages {@code target}, credited to {@code user} so kills count. */
    public static void damage(Player user, LivingEntity target, double amount) {
        if (amount <= 0 || target.isDead()) return;
        dealingAbilityDamage = true;
        try {
            target.damage(amount, user);
        } finally {
            dealingAbilityDamage = false;
        }
    }

    /** Players (in PvP-enabled worlds, in survival/adventure) and hostile mobs; never the user. */
    public static boolean isTarget(Player user, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.equals(user) || living.isDead()) return false;
        if (entity instanceof Player p) {
            GameMode gm = p.getGameMode();
            return (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE)
                    && !Boolean.FALSE.equals(p.getWorld().getGameRuleValue(GameRules.PVP));
        }
        return entity instanceof Enemy;
    }

    public static List<LivingEntity> targetsAround(Player user, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (isTarget(user, e) && e.getLocation().distanceSquared(center) <= radius * radius) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** Targets within {@code range} and {@code halfAngleDegrees} of where the user is looking. */
    public static List<LivingEntity> targetsInCone(Player user, double range, double halfAngleDegrees) {
        Location eye = user.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        double minDot = Math.cos(Math.toRadians(halfAngleDegrees));
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : user.getNearbyEntities(range, range, range)) {
            if (!isTarget(user, e)) continue;
            Vector to = ((LivingEntity) e).getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist > range || dist < 0.01) continue;
            if (to.normalize().dot(look) >= minDot) out.add((LivingEntity) e);
        }
        return out;
    }

    /** Pushes {@code target} away from {@code from} horizontally, plus an upward kick. */
    public static void knockAway(Location from, LivingEntity target, double horizontal, double up) {
        Vector dir = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 0);
        else dir.normalize().multiply(horizontal);
        target.setVelocity(dir.setY(up));
    }

    /**
     * Runs {@code onLand} the first tick the player is back on the ground after leaving it,
     * or gives up after {@code maxTicks}.
     */
    public static void whenLanded(Plugin plugin, Player player, int maxTicks, Runnable onLand) {
        new BukkitRunnable() {
            int ticks;
            boolean airborne;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead() || ticks > maxTicks) {
                    cancel();
                    return;
                }
                @SuppressWarnings("deprecation")
                boolean onGround = player.isOnGround();
                if (!onGround) airborne = true;
                else if (airborne || ticks > 10) {
                    cancel();
                    onLand.run();
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    public static void later(Plugin plugin, long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }
}
