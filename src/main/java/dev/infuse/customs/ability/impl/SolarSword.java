package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Solar Sword: burning light, extra strong against undead.
 * Passive Radiance, on-hit Sunfire, RC Solar Beam, Sneak+RC Supernova.
 */
public final class SolarSword extends SwordAbility {

    private static final int GOLD = 0xECBA44;
    private static final int PALE = 0xFFF4C2;

    public SolarSword(CustomsPlugin plugin) {
        super(plugin, SwordType.SOLAR);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.HASTE, 0, 1));
    }

    @Override
    public void tickHeld(Player player, long tick) {
        if (tick % 2 != 0) {
            return; // once a second
        }
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 12)) {
            if (target instanceof Enemy || target instanceof Player) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, true, false, false));
            }
        }
    }

    private static boolean undead(LivingEntity entity) {
        return Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(entity.getType());
    }

    private double scaled(LivingEntity target, double damage) {
        return undead(target) ? damage * 1.5 : damage;
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        target.setFireTicks(Math.max(target.getFireTicks(), 80));
        Location at = Combat.center(target);
        Fx.particle(at, Particle.DUST, 6, 0.3, 0, Fx.dust(GOLD, 1.1f));
        if (undead(target)) {
            event.setDamage(event.getDamage() * 1.5);
            Fx.particle(at, Particle.END_ROD, 10, 0.3, 0.05);
        }
    }

    /**
     * Solar Beam: a beam of sunlight that burns for 1.5 seconds and follows your aim, so it can be
     * swept across enemies. Each enemy is burned at most every half second.
     */
    @Override
    public boolean primary(Player player) {
        Fx.sound(player.getEyeLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
        Fx.sound(player.getEyeLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 0.8f, 1.6f);
        java.util.Map<java.util.UUID, Integer> lastHit = new java.util.HashMap<>();
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || ticks >= 30 || plugin.items().heldType(player) != type) {
                    cancel();
                    return;
                }
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection();
                double length = Combat.clearDistance(player, 30);
                Location end = eye.clone().add(dir.clone().multiply(length));
                if (ticks % 2 == 0) {
                    Location hand = dev.infuse.customs.ability.Fx3D.handOrigin(player);
                    fx().beam(hand, end, 0xFFC93A, ticks == 0 ? 2.4f : 1.5f, 3);
                    fx().beam(hand, end, 0xFFF6D0, ticks == 0 ? 0.9f : 0.55f, 3);
                    fx().flash(end, 0xFFB52E, 1.3f);
                    Fx.particle(end, Particle.FLAME, 6, 0.25, 0.04);
                    for (double d = 2.5; d <= length; d += 2.2) {
                        Fx.particle(eye.clone().add(dir.clone().multiply(d)), Particle.END_ROD, 1, 0.05, 0.01);
                    }
                }
                if (ticks % 6 == 0) {
                    Location ground = Combat.ground(end.clone().add(0, 0.5, 0));
                    if (ground.distanceSquared(end) < 4) {
                        fx().flamePillar(ground, 0.8f, 1.4f, 12, false);
                    }
                    Fx.sound(end, Sound.BLOCK_FIRE_AMBIENT, 1f, 1.4f);
                }
                Location mid = eye.clone().add(dir.clone().multiply(length / 2));
                for (LivingEntity target : combat().nearby(player, mid, length / 2 + 2)) {
                    if (target.getBoundingBox().expand(0.6).rayTrace(eye.toVector(), dir, length) == null) {
                        continue;
                    }
                    Integer last = lastHit.get(target.getUniqueId());
                    if (last != null && ticks - last < 10) {
                        continue;
                    }
                    lastHit.put(target.getUniqueId(), ticks);
                    combat().damage(player, target, scaled(target, last == null ? 6 : 3), true);
                    target.setFireTicks(Math.max(target.getFireTicks(), 80));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
                    fx().burst(Combat.center(target), 0xFFE08A, 1.3f);
                    Fx.particle(Combat.center(target), Particle.FLAME, 10, 0.3, 0.05);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Supernova: 1 second charge, then a radius 10 blast. */
    @Override
    public boolean secondary(Player player) {
        Fx.sound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player)) {
                    cancel();
                    return;
                }
                ticks++;
                Location center = player.getLocation().add(0, 1, 0);
                if (ticks <= 20) {
                    // sunbeams converge on you from all around while the light gathers
                    if (ticks % 3 == 1) {
                        double a = ticks * 1.3;
                        Location from = center.clone().add(Math.cos(a) * 7, 1.5 + Math.sin(ticks) * 2, Math.sin(a) * 7);
                        fx().beam(from, center, 0xFFE08A, 0.5f, 5);
                    }
                    if (ticks == 6 || ticks == 14) {
                        fx().flash(center, 0xFFD24A, 1.2f + ticks * 0.08f);
                    }
                    double radius = 3.0 * (1 - ticks / 20.0) + 0.4;
                    double y = ticks / 20.0 * 1.5 - 0.8;
                    Fx.ring(center.clone().add(0, y, 0), radius, 14, p -> Fx.particle(p, Particle.END_ROD, 1, 0, 0));
                    Fx.ring(center.clone().add(0, -y, 0), radius, 10, p -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(GOLD, 1.3f)));
                    if (ticks % 5 == 0) {
                        Fx.sound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.5f + ticks / 20f);
                    }
                    return;
                }
                explode(player, center);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void explode(Player player, Location center) {
        double radius = 10;
        Fx.particle(center, Particle.FLASH, 1, 0, 0, Color.fromRGB(0xFFE08A));
        Fx.burst(center, Particle.END_ROD, 120, 0.7);
        Fx.burst(center, Particle.FLAME, 80, 0.5);
        for (double r = 2; r <= radius; r += 2) {
            Fx.ring(center.clone().subtract(0, 0.8, 0), r, (int) (r * 8),
                    p -> Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(GOLD, 1.6f)));
        }
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.3f);
        Fx.sound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 1f);
        fx().shockwave(center.clone().subtract(0, 0.9, 0), 0xFFD24A, 10f, 10);
        fx().shockwave(center.clone().subtract(0, 0.9, 0), 0xFFF6D0, 6f, 7);
        fx().burst(center, 0xFFE08A, 5f);
        fx().flash(center, 0xFFB52E, 4.5f);
        // a small sun swells where you stood, then burns out, throwing rays in every direction
        org.bukkit.entity.ItemDisplay sun = fx().orb(center.clone().add(0, 0.5, 0), 0xFFC93A, 1.5f, 30);
        displays().later(1, () -> dev.infuse.customs.ability.Displays.animate(sun, new org.bukkit.util.Transformation(
                new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(7f), new org.joml.Quaternionf()), 6));
        fx().fade(sun, dev.infuse.customs.ability.Fx3D.Shape.GLOW, 0xFFC93A, 10, 4);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            Location tip = center.clone().add(Math.cos(a) * 9, 0.4 + (i % 2) * 1.6, Math.sin(a) * 9);
            fx().beam(center.clone().add(0, 0.5, 0), tip, 0xFFF0B0, 0.7f, 8);
        }
        fx().crack(center.clone().subtract(0, 1, 0), 0xFFB52E, 9f, 50);
        for (LivingEntity target : combat().nearby(player, center, radius)) {
            combat().damage(player, target, scaled(target, 10));
            applyEffect(target, PotionEffectType.BLINDNESS, 60, 0);
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
            Combat.knockback(target, center, 1.3, 0.5);
        }
        applyEffect(player, PotionEffectType.REGENERATION, 100, 1);
    }
}
