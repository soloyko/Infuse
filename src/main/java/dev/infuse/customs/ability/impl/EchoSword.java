package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Deepwhisper, the Echo Sword: the warden's power.
 * Passive Child of the Deep, on-hit Resonance, RC Sonic Boom, Sneak+RC Shriek.
 */
public final class EchoSword extends SwordAbility implements Listener {

    private static final int SCULK = 0x22C2C7;
    private static final int DEEP = 0x0B2A33;

    public EchoSword(CustomsPlugin plugin) {
        super(plugin, SwordType.ECHO);
    }

    @Override
    public void tickHeld(Player player, long tick) {
        if (player.hasPotionEffect(PotionEffectType.DARKNESS)) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    /** Sculk sensors and shriekers don't hear the holder. */
    @EventHandler(ignoreCancelled = true)
    public void onSculkHear(BlockReceiveGameEvent event) {
        if (event.getEntity() instanceof Player player && plugin.items().heldType(player) == type
                && plugin.canUse(player, type)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        if (target instanceof Player) {
            applyEffect(target, PotionEffectType.DARKNESS, 60, 0);
        } else {
            applyEffect(target, PotionEffectType.SLOWNESS, 60, 1);
        }
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SCULK_CHARGE_POP, 12, 0.35, 0.02);
        Fx.particle(at, Particle.SCULK_SOUL, 4, 0.3, 0.02);
        Fx.sound(at, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1f, 1f);
    }

    /** Sonic Boom: a one second charge, then a piercing warden blast up to 16 blocks. */
    @Override
    public boolean primary(Player player) {
        Fx.sound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1f);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player)) {
                    cancel();
                    return;
                }
                ticks++;
                Location chest = player.getLocation().add(0, 1.2, 0);
                if (ticks < 20) {
                    // sculk energy gathering in front of the caster
                    Location focus = chest.clone().add(player.getLocation().getDirection().multiply(1.8));
                    Fx.directional(focus.clone().add(Fx.randomUnit().multiply(1.5)), Particle.SCULK_SOUL,
                            focus.toVector().subtract(chest.toVector()).normalize(), 0.02);
                    Fx.particle(focus, Particle.SCULK_CHARGE_POP, 2, 0.4, 0.01);
                    return;
                }
                cancel();
                blast(player);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void blast(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double length = Combat.clearDistance(player, 16);
        for (double d = 2.5; d <= length; d += 1.0) { // the ring is big; keep it out of the caster's face
            Fx.particle(eye.clone().add(dir.clone().multiply(d)), Particle.SONIC_BOOM, 1, 0, 0);
        }
        Fx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 1f);
        Location boomStart = dev.infuse.customs.ability.Fx3D.handOrigin(player);
        Location boomEnd = eye.clone().add(dir.clone().multiply(length));
        fx().beam(boomStart, boomEnd, 0x1FB5B0, 2.4f, 12);
fx().beam(boomStart, boomEnd, 0xB8FFF8, 0.6f, 7);
        for (double d = 5; d <= length; d += 4) {
            fx().wave(eye.clone().add(dir.clone().multiply(d)), dir, 0x7FF3EA, 1.6f, 5);
        }
        Location mid = eye.clone().add(dir.clone().multiply(length / 2));
        for (LivingEntity target : combat().nearby(player, mid, length / 2 + 2)) {
            if (target.getBoundingBox().expand(0.8).rayTrace(eye.toVector(), dir, length) == null) {
                continue;
            }
            combat().damage(player, target, 10, true);
            Combat.setVelocity(target, dir.clone().multiply(1.6).setY(0.5));
            Fx.particle(Combat.center(target), Particle.SCULK_CHARGE_POP, 15, 0.4, 0.05);
        }
    }

    /** Shriek: darken and reveal every enemy within 14 blocks, and quicken yourself. */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Fx.sound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 1f);
        Fx.sound(center, Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.2f);
        fx().shockwave(center, 0x0F7C78, 14f, 16);
        fx().shockwave(center, 0x1FB5B0, 9f, 12);
        for (int i = 0; i < 4; i++) {
            int delay = i * 4;
            displays().later(1 + delay, () -> fx().wave(player.getLocation().add(0, 1.2 + delay * 0.08, 0),
                    new org.bukkit.util.Vector(0, 1, 0.0001), 0x7FF3EA, 3.5f + delay * 0.2f, 8));
        }
        for (int delay = 0; delay < 20; delay += 5) {
            Fx.particle(center.clone().add(0, 0.6, 0), Particle.SHRIEK, 1, 0, 0, delay);
        }
        new BukkitRunnable() {
            double radius = 1;

            @Override
            public void run() {
                if (radius > 14 || player.getWorld() != center.getWorld()) {
                    cancel();
                    return;
                }
                Location ring = center.clone().add(0, 0.25, 0);
                Fx.ring(ring, radius, (int) (radius * 7), p -> Fx.particle(p, Particle.SCULK_SOUL, 1, 0.05, 0.01));
                Fx.ring(ring, radius - 0.5, (int) (radius * 3), p -> Fx.particle(p, Particle.DUST, 1, 0.05, 0,
                        Fx.dust(radius % 2 < 1 ? SCULK : DEEP, 1.6f)));
                radius += 1.4;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        for (LivingEntity target : combat().nearby(player, center, 14)) {
            combat().damage(player, target, 3);
            applyEffect(target, PotionEffectType.GLOWING, 120, 0);
            if (target instanceof Player) {
                applyEffect(target, PotionEffectType.DARKNESS, 120, 0);
            } else {
                applyEffect(target, PotionEffectType.SLOWNESS, 80, 1);
            }
            Fx.particle(Combat.center(target), Particle.SCULK_CHARGE_POP, 8, 0.3, 0.02);
        }
        applyEffect(player, PotionEffectType.SPEED, 120, 1);
        applyEffect(player, PotionEffectType.STRENGTH, 120, 0);
        return true;
    }
}
