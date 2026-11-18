package dev.bloodbound.ability.kits;

import dev.bloodbound.ability.Abilities;
import dev.bloodbound.ability.Ctx;
import dev.bloodbound.ability.WeaponKit;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Seeker passive (homing), Volley active, Shadowstep (teleport to arrow) ultimate. */
public final class PhantomBowKit implements WeaponKit {
    private static final String SHADOWSTEP = "shadowstep";
    private static final String VOLLEY = "volley";

    private final Map<UUID, Long> shadowstepUntil = new HashMap<>();

    @Override
    public WeaponType type() {
        return WeaponType.PHANTOM_BOW;
    }

    @Override
    public void onShoot(Ctx ctx, AbstractArrow arrow, float force) {
        Player user = ctx.player();
        Long until = shadowstepUntil.get(user.getUniqueId());
        if (until != null && System.currentTimeMillis() <= until) {
            shadowstepUntil.remove(user.getUniqueId());
            arrow.getPersistentDataContainer().set(ctx.plugin().keys().projectileFlag, PersistentDataType.STRING, SHADOWSTEP);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            trail(ctx, arrow);
        }
        if (force >= 0.9f) seek(ctx, arrow);
    }

    private void seek(Ctx ctx, AbstractArrow arrow) {
        Player user = ctx.player();
        double strength = ctx.cfg().num("seeker-strength-per-tier", 0.03) * ctx.tier();
        double range = ctx.cfg().num("seeker-range", 14.0);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (++ticks > 40 || !arrow.isValid() || arrow.isInBlock() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                Vector vel = arrow.getVelocity();
                double speed = vel.length();
                if (speed < 0.2) return;
                Player best = null;
                double bestDist = Double.MAX_VALUE;
                for (Entity e : arrow.getNearbyEntities(range, range, range)) {
                    if (!(e instanceof Player p) || !Abilities.isTarget(user, p)) continue;
                    Vector to = p.getLocation().add(0, 1, 0).toVector().subtract(arrow.getLocation().toVector());
                    if (to.clone().normalize().dot(vel.clone().normalize()) < 0.6) continue;
                    double d = to.lengthSquared();
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
                if (best == null) return;
                Vector to = best.getLocation().add(0, 1, 0).toVector().subtract(arrow.getLocation().toVector()).normalize();
                arrow.setVelocity(vel.normalize().multiply(1 - strength).add(to.multiply(strength)).normalize().multiply(speed));
            }
        }.runTaskTimer(ctx.plugin(), 1L, 1L);
    }

    private void trail(Ctx ctx, AbstractArrow arrow) {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (++ticks > 100 || !arrow.isValid()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.PORTAL, arrow.getLocation(), 6, 0.1, 0.1, 0.1, 0.2);
            }
        }.runTaskTimer(ctx.plugin(), 1L, 1L);
    }

    @Override
    public void onProjectileHit(Ctx ctx, Projectile projectile, ProjectileHitEvent event) {
        String flag = projectile.getPersistentDataContainer().get(ctx.plugin().keys().projectileFlag, PersistentDataType.STRING);
        if (!SHADOWSTEP.equals(flag)) {
            if (VOLLEY.equals(flag)) Abilities.later(ctx.plugin(), 1L, projectile::remove);
            return;
        }
        Player user = ctx.player();
        if (!projectile.getWorld().equals(user.getWorld())) return;
        Vector back = projectile.getVelocity().clone();
        Location dest = projectile.getLocation();
        if (back.lengthSquared() > 0.0001) dest = dest.subtract(back.normalize().multiply(0.6));
        dest.setYaw(user.getLocation().getYaw());
        dest.setPitch(user.getLocation().getPitch());
        if (dest.getBlock().isSolid()) dest.add(0, 1, 0);
        user.getWorld().spawnParticle(Particle.REVERSE_PORTAL, user.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
        user.teleport(dest);
        ctx.plugin().abilities().grantFallImmunity(user, 3000);
        user.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
        user.getWorld().spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
        Abilities.later(ctx.plugin(), 1L, projectile::remove);
    }

    @Override
    public boolean active(Ctx ctx) {
        Player user = ctx.player();
        int arrows = Math.max(1, ctx.cfg().integer("volley-arrows", 5));
        double spread = ctx.cfg().num("volley-spread", 8.0);
        Location eye = user.getEyeLocation();
        for (int i = 0; i < arrows; i++) {
            double offset = (i - (arrows - 1) / 2.0) * spread;
            Vector dir = eye.getDirection().rotateAroundY(Math.toRadians(-offset)).multiply(2.6);
            Arrow arrow = user.launchProjectile(Arrow.class, dir);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            arrow.getPersistentDataContainer().set(ctx.plugin().keys().projectileWeapon, PersistentDataType.STRING, type().key());
            arrow.getPersistentDataContainer().set(ctx.plugin().keys().projectileFlag, PersistentDataType.STRING, VOLLEY);
        }
        user.getWorld().playSound(user.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.7f);
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.2f);
        return true;
    }

    @Override
    public boolean ultimate(Ctx ctx) {
        Player user = ctx.player();
        shadowstepUntil.put(user.getUniqueId(), System.currentTimeMillis() + ctx.cfg().millis("shadowstep-duration", 8000));
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1f);
        user.getWorld().spawnParticle(Particle.PORTAL, user.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        return true;
    }

    @Override
    public void clear(Player player) {
        shadowstepUntil.remove(player.getUniqueId());
    }
}
