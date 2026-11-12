package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Inferno Sword: fire damage everywhere.
 * Passive Heart of Fire, on-hit Searing Edge, RC Fireball Barrage, Sneak+RC Inferno Nova.
 */
public final class InfernoSword extends SwordAbility implements Listener {

    private final NamespacedKey fireballKey;

    public InfernoSword(OPSwordsPlugin plugin) {
        super(plugin, SwordType.INFERNO);
        this.fireballKey = new NamespacedKey(plugin, "inferno_fireball");
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.FIRE_RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        target.setFireTicks(Math.max(target.getFireTicks(), 100));
        if (!chance(0.15)) {
            return;
        }
        event.setDamage(event.getDamage() + 3);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.FLAME, 40, 0.4, 0.12);
        Fx.particle(at, Particle.LAVA, 8, 0.3, 0);
        Fx.sound(at, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.7f);
        for (LivingEntity nearby : combat().nearby(player, at, 3)) {
            if (nearby != target) {
                combat().damage(player, nearby, 3);
                nearby.setFireTicks(Math.max(nearby.getFireTicks(), 80));
            }
        }
    }

    /** Fireball Barrage: three small fireballs that also burst on impact. */
    @Override
    public boolean primary(Player player) {
        Vector dir = player.getEyeLocation().getDirection();
        for (int angle : new int[]{-12, 0, 12}) {
            Vector shot = dir.clone().rotateAroundY(Math.toRadians(angle));
            SmallFireball fireball = player.launchProjectile(SmallFireball.class, shot.clone().multiply(1.4));
            fireball.setIsIncendiary(false); // no fire blocks / griefing
            fireball.setAcceleration(shot.clone().multiply(0.1));
            fireball.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        }
        Fx.sound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        Fx.sound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.8f);
        return true;
    }

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getPersistentDataContainer().has(fireballKey) || !(projectile.getShooter() instanceof Player player)) {
            return;
        }
        Location at = projectile.getLocation();
        Fx.particle(at, Particle.FLAME, 30, 0.5, 0.1);
        Fx.particle(at, Particle.EXPLOSION, 1, 0, 0);
        Fx.sound(at, Sound.ENTITY_GENERIC_BURN, 0.8f, 1.2f);
        for (LivingEntity target : combat().nearby(player, at, 2.5)) {
            combat().damage(player, target, 4);
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
        }
    }

    /** Inferno Nova: an expanding ring of fire around the caster. */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Fx.sound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
        Fx.sound(center, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
        Fx.particle(center.clone().add(0, 1, 0), Particle.FLAME, 60, 0.4, 0.2);
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            double radius = 1;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != center.getWorld() || radius > 9) {
                    cancel();
                    return;
                }
                Location ring = center.clone().add(0, 0.2, 0);
                Fx.ring(ring, radius, (int) (radius * 12), p -> Fx.particle(p, Particle.FLAME, 1, 0.05, 0.02));
                Fx.ring(ring.clone().add(0, 0.8, 0), radius, (int) (radius * 5), p -> Fx.particle(p, Particle.SOUL_FIRE_FLAME, 1, 0.05, 0.01));
                if (((int) (radius * 2)) % 3 == 0) {
                    Fx.ring(ring, radius, 6, p -> Fx.particle(p, Particle.LAVA, 1, 0, 0));
                }
                for (LivingEntity target : combat().nearby(player, center, radius + 0.6)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 8);
                        target.setFireTicks(Math.max(target.getFireTicks(), 120));
                        Combat.knockback(target, center, 1.0, 0.45);
                    }
                }
                radius += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
