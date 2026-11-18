package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Power Fuse Hammer, the Fusion Maul: magnetism and stored power.
 * Passive Polarity, on-hit Static, F Repulsor Field, Sneak+F Overcharge.
 */
public final class MaulWeapon extends SwordAbility implements Listener {

    private static final int TEAL = 0x2AD4C4;
    private static final int ORANGE = 0xFF9F1C;
    private static final int ARC = 0xBFF8FF;
    private static final int MAGNET_TICKS = 100;
    private static final int FIELD_TICKS = 100;
    private static final double FIELD_RADIUS = 3.4;
    private static final int CHARGE_TICKS = 40;
    private static final int CHARGED_TICKS = 160;

    /** Magnetized entity -> tick it wears off. */
    private final Map<UUID, Integer> magnetized = new HashMap<>();
    /** Player -> tick their stored charge runs out. */
    private final Map<UUID, Integer> charged = new HashMap<>();

    public MaulWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.MAUL);
    }

    private boolean isMagnetized(Entity entity) {
        Integer until = magnetized.get(entity.getUniqueId());
        return until != null && until >= Bukkit.getCurrentTick();
    }

    private List<LivingEntity> magnetizedNear(Player player, Location at, double radius, LivingEntity except) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity near : combat().nearby(player, at, radius)) {
            if (near != except && isMagnetized(near)) {
                out.add(near);
            }
        }
        return out;
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        int now = Bukkit.getCurrentTick();
        Location at = Combat.center(target);
        Integer charge = charged.remove(player.getUniqueId());
        if (charge != null && charge >= now) {
            release(player, target, event, at);
        } else if (chance(0.25)) {
            // Static: a spark jumps to every magnetized enemy nearby
            for (LivingEntity other : magnetizedNear(player, at, 6, target)) {
                fx().bolt(at, Combat.center(other), ARC, 0.25f, 4, 1);
                combat().damage(player, other, 3, true);
                Fx.particle(Combat.center(other), Particle.ELECTRIC_SPARK, 10, 0.3, 0.1);
            }
            Fx.sound(at, Sound.BLOCK_COPPER_BULB_TURN_ON, 1f, 1.6f);
        }
        // Polarity: the struck enemy is magnetized
        magnetized.put(target.getUniqueId(), now + MAGNET_TICKS);
        Fx.particle(at, Particle.ELECTRIC_SPARK, 6, 0.3, 0.1);
        Fx.particle(at, Particle.DUST, 4, 0.3, 0, Fx.dust(TEAL, 1.0f));
    }

    /** The stored charge goes into one blow, and arcs through every magnetized enemy around. */
    private void release(Player player, LivingEntity target, EntityDamageByEntityEvent event, Location at) {
        event.setDamage(event.getDamage() + 14);
        Combat.knockback(target, player.getLocation(), 1.4, 0.5);
        fx().burst(at, ORANGE, 3f);
        fx().flash(at, ARC, 3f);
        fx().wave(at, player.getEyeLocation().getDirection(), TEAL, 2.4f, 7);
        fx().crack(Combat.ground(target.getLocation().add(0, 0.5, 0)), TEAL, 3f, 30);
        Fx.particle(at, Particle.ELECTRIC_SPARK, 40, 0.6, 0.4);
        Fx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.2f);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        for (LivingEntity other : magnetizedNear(player, at, 10, target)) {
            fx().bolt(at, Combat.center(other), ARC, 0.35f, 6, 2);
            combat().damage(player, other, 6, true);
            Combat.knockback(other, at, 0.8, 0.3);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        charged.remove(event.getPlayer().getUniqueId());
    }

    // ---- Repulsor Field ----

    /**
     * Repulsor Field: a magnetic field wraps around you for five seconds. Enemies who come within
     * reach are thrown back out of it, and arrows and other projectiles are turned around in the
     * air and sent back the way they came.
     */
    @Override
    public boolean primary(Player player) {
        Location at = player.getLocation().add(0, 1, 0);
        Fx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.4f);
        Fx.sound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
        fx().wave(at, new Vector(0, 1, 0), TEAL, (float) FIELD_RADIUS, 6);
        Map<UUID, Integer> repelled = new HashMap<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > FIELD_TICKS) {
                    if (alive(player)) {
                        Fx.sound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
                    }
                    cancel();
                    return;
                }
                Location center = player.getLocation().add(0, 1, 0);
                // three rings turning on different axes: the field lines
                if (ticks % 2 == 0) {
                    double a = ticks * 0.15;
                    for (int k = 0; k < 16; k++) {
                        double t = k * Math.PI * 2 / 16 + a;
                        double c = Math.cos(t) * FIELD_RADIUS;
                        double s = Math.sin(t) * FIELD_RADIUS;
                        Fx.particle(center.clone().add(c, 0, s), Particle.DUST, 1, 0, 0, Fx.dust(TEAL, 0.9f));
                        if (k % 2 == 0) {
                            Fx.particle(center.clone().add(c * Math.cos(a), s, c * Math.sin(a)), Particle.DUST, 1, 0, 0, Fx.dust(ARC, 0.8f));
                            Fx.particle(center.clone().add(s * Math.sin(a), c, s * Math.cos(a)), Particle.DUST, 1, 0, 0, Fx.dust(ORANGE, 0.8f));
                        }
                    }
                }
                if (ticks % 20 == 0) {
                    Fx.sound(center, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
                }
                for (Entity e : player.getNearbyEntities(FIELD_RADIUS + 2, FIELD_RADIUS + 2, FIELD_RADIUS + 2)) {
                    Vector off = e.getLocation().toVector().subtract(center.toVector());
                    if (e instanceof Projectile projectile) {
                        if (projectile.getShooter() == player || off.length() > FIELD_RADIUS + 1) {
                            continue;
                        }
                        Vector v = projectile.getVelocity();
                        if (v.dot(off) < 0) {
                            // flying in towards you: turn it around
                            projectile.setVelocity(v.multiply(-0.9));
                            projectile.setShooter(player);
                            Fx.particle(e.getLocation(), Particle.ELECTRIC_SPARK, 8, 0.1, 0.1);
                            Fx.sound(e.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.6f);
                        }
                        continue;
                    }
                    if (!(e instanceof LivingEntity target) || !combat().canHit(player, target)
                            || Combat.distanceToBody(target, center) > FIELD_RADIUS) {
                        continue;
                    }
                    Integer last = repelled.get(target.getUniqueId());
                    if (last != null && ticks - last < 8) {
                        continue;
                    }
                    repelled.put(target.getUniqueId(), ticks);
                    Combat.knockback(target, center, 1.3, 0.4);
                    if (last == null || ticks - last > 20) {
                        combat().damage(player, target, 2, true);
                    }
                    magnetized.put(target.getUniqueId(), Bukkit.getCurrentTick() + MAGNET_TICKS);
                    fx().bolt(center, Combat.center(target), ARC, 0.2f, 3, 0);
                    Fx.sound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 1.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ---- Overcharge ----

    /**
     * Overcharge: you plant your feet and the maul's fuse draws in power for two seconds (you're
     * slowed while it charges). Your next hit within eight seconds releases all of it.
     */
    @Override
    public boolean secondary(Player player) {
        if (charged.containsKey(player.getUniqueId())) {
            tell(player, "The maul is already charged.");
            return false;
        }
        applyEffect(player, PotionEffectType.SLOWNESS, CHARGE_TICKS, 2);
        Fx.sound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.6f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player)) {
                    cancel();
                    return;
                }
                Location hand = dev.infuse.customs.ability.Fx3D.handOrigin(player);
                if (ticks <= CHARGE_TICKS) {
                    // power spirals in to the maul's head, faster as it fills
                    double t = ticks / (double) CHARGE_TICKS;
                    for (int k = 0; k < 2; k++) {
                        double a = ticks * (0.4 + t) + k * Math.PI;
                        double r = 1.8 * (1 - t) + 0.3;
                        Location p = hand.clone().add(Math.cos(a) * r, (1 - t) * 1.2, Math.sin(a) * r);
                        Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(k == 0 ? TEAL : ORANGE, 1.1f));
                    }
                    if (ticks % 8 == 0) {
                        Fx.sound(hand, Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, (float) (0.6 + t * 1.3));
                    }
                    if (ticks == CHARGE_TICKS) {
                        charged.put(player.getUniqueId(), Bukkit.getCurrentTick() + CHARGED_TICKS);
                        fx().flash(hand, ORANGE, 1.8f);
                        Fx.sound(hand, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
                        Fx.particle(hand, Particle.ELECTRIC_SPARK, 20, 0.3, 0.2);
                    }
                    return;
                }
                Integer until = charged.get(player.getUniqueId());
                if (until == null) {
                    cancel(); // released on a hit
                    return;
                }
                if (until < Bukkit.getCurrentTick()) {
                    charged.remove(player.getUniqueId());
                    Fx.sound(hand, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.6f);
                    Fx.particle(hand, Particle.SMOKE, 10, 0.2, 0.02);
                    cancel();
                    return;
                }
                // charged: the head crackles
                if (ticks % 3 == 0) {
                    Fx.particle(hand, Particle.ELECTRIC_SPARK, 2, 0.15, 0.05);
                    Fx.particle(hand, Particle.DUST, 1, 0.1, 0, Fx.dust(ORANGE, 1.0f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        magnetized.clear();
        charged.clear();
    }
}
