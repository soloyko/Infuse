package dev.infuse.customs.ability.impl;

import dev.infuse.customs.ability.Displays;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Aeon, the Chrono Sword: control over time.
 * Passive Quickened, on-hit Time Lock, RC Stasis Field, Sneak+RC Rewind.
 */
public final class ChronoSword extends SwordAbility implements Listener {

    private static final int CYAN = 0x7FE0D0;
    private static final int GOLD = 0xE8C35A;
    private static final int REWIND_TICKS = 80;

    private record Snapshot(Location location, double health, int tick) {
    }

    /** Where each holder was recently, sampled every 10 ticks while the sword is held. */
    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();

    public ChronoSword(CustomsPlugin plugin) {
        super(plugin, SwordType.CHRONO);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ATTACK_SPEED, "attack_speed", 0.25, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    @Override
    public void tickHeld(Player player, long tick) {
        Deque<Snapshot> past = history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        past.addLast(new Snapshot(player.getLocation(), player.getHealth(), Bukkit.getCurrentTick()));
        while (past.size() > 14) {
            past.removeFirst();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.15)) {
            return;
        }
        applyEffect(target, PotionEffectType.SLOWNESS, 25, 6);
        Location at = Combat.center(target);
        Fx.ring(at, 0.8, 16, p -> Fx.particle(p, Particle.END_ROD, 1, 0, 0));
        Fx.particle(at, Particle.ENCHANT, 25, 0.4, 0.5);
        Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!target.isValid() || ticks++ >= 25) {
                    cancel();
                    return;
                }
                Combat.setVelocity(target, new Vector(0, Math.min(0, target.getVelocity().getY()), 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Stasis Field: 4 seconds of frozen time around the target point. */
    @Override
    public boolean primary(Player player) {
        Location eye = player.getEyeLocation();
        Location aim = combat().aimPoint(player, 20);
        Location center = (aim == null ? Combat.ground(eye.clone().add(eye.getDirection().multiply(10))) : aim).add(0, 1, 0);
        double radius = 5;
        Fx.sound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 2f);
        Fx.sound(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1.4f);
        fx().castCircle(center.clone().subtract(0, 1, 0), 0x5FE3E0, 5.2f);
        fx().shockwave(center.clone().subtract(0, 1, 0), 0xC9FFFB, 5f, 10);
        Map<Projectile, Vector> frozen = new HashMap<>();
        Map<Fireball, Vector> frozenPower = new HashMap<>();
        // the hands of a giant clock turn on the ground under the frozen moment
        Location face = center.clone().subtract(0, 0.9, 0);
        face.setYaw(0);
        face.setPitch(0);
        org.bukkit.entity.ItemDisplay longHand = fx().spawn(face, dev.infuse.customs.ability.Fx3D.Shape.BEAM, 0x9FF5F0,
                clockHand(0, 4.6f, 0.9f), org.bukkit.entity.Display.Billboard.FIXED, 92);
        org.bukkit.entity.ItemDisplay shortHand = fx().spawn(face, dev.infuse.customs.ability.Fx3D.Shape.BEAM, 0x5FE3E0,
                clockHand(1.2, 2.8f, 1.2f), org.bukkit.entity.Display.Billboard.FIXED, 92);
        org.bukkit.entity.ItemDisplay pivot = fx().orb(face.clone().add(0, 0.2, 0), 0xC9FFFB, 1.2f, 92);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != center.getWorld() || ticks++ >= 80) {
                    fx().fade(longHand, dev.infuse.customs.ability.Fx3D.Shape.BEAM, 0x9FF5F0, 1, 2);
                    fx().fade(shortHand, dev.infuse.customs.ability.Fx3D.Shape.BEAM, 0x5FE3E0, 1, 2);
                    fx().fade(pivot, dev.infuse.customs.ability.Fx3D.Shape.GLOW, 0xC9FFFB, 1, 2);
                    release();
                    cancel();
                    return;
                }
                drawClock(center, radius, ticks);
                if (ticks % 2 == 0) {
                    // ticking: the long hand jumps each second, the short one creeps
                    double longAngle = Math.floor(ticks / 20.0) * Math.PI / 6 + (ticks % 20 < 2 ? 0 : 0);
                    Displays.animate(longHand, clockHand(longAngle, 4.6f, 0.9f), ticks % 20 == 0 ? 3 : 2);
                    Displays.animate(shortHand, clockHand(1.2 + ticks * 0.012, 2.8f, 1.2f), 2);
                }
                if (ticks % 10 == 0) {
                    Fx.sound(center, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, ticks % 20 == 0 ? 1.8f : 1.4f);
                }
                for (LivingEntity target : combat().nearby(player, center, radius)) {
                    Combat.setVelocity(target, target.getVelocity().multiply(0.15));
                    applyEffect(target, PotionEffectType.SLOWNESS, 20, 3);
                    applyEffect(target, PotionEffectType.MINING_FATIGUE, 20, 2);
                }
                for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (e instanceof Projectile projectile && projectile.getShooter() != player
                            && e.getLocation().distanceSquared(center) <= radius * radius) {
                        frozen.computeIfAbsent(projectile, p -> {
                            p.setGravity(false);
                            return p.getVelocity();
                        });
                        if (projectile instanceof Fireball fireball && !frozenPower.containsKey(fireball)) {
                            frozenPower.put(fireball, fireball.getAcceleration());
                            fireball.setAcceleration(new Vector());
                        }
                        projectile.setVelocity(new Vector());
                    }
                }
            }

            private void release() {
                for (Iterator<Map.Entry<Projectile, Vector>> it = frozen.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Projectile, Vector> entry = it.next();
                    Projectile projectile = entry.getKey();
                    if (projectile.isValid()) {
                        projectile.setGravity(true);
                        projectile.setVelocity(entry.getValue());
                        if (projectile instanceof Fireball fireball && frozenPower.containsKey(fireball)) {
                            fireball.setAcceleration(frozenPower.get(fireball));
                        }
                    }
                    it.remove();
                }
                Fx.sound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.8f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void drawClock(Location center, double radius, int ticks) {
        Location ground = center.clone().subtract(0, 0.9, 0);
        if (ticks % 2 == 0) {
            Fx.ring(ground, radius, 40, p -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(CYAN, 1.2f)));
            for (int h = 0; h < 12; h++) {
                double a = h * Math.PI / 6;
                Fx.particle(ground.clone().add(Math.cos(a) * (radius - 0.5), 0, Math.sin(a) * (radius - 0.5)),
                        Particle.DUST, 1, 0, 0, Fx.dust(GOLD, 1.5f));
            }
        }
        // minute and hour hands sweeping round
        double minute = ticks * 0.25;
        double hour = ticks * 0.02;
        Fx.line(ground, ground.clone().add(Math.cos(minute) * (radius - 0.8), 0, Math.sin(minute) * (radius - 0.8)), 0.5,
                p -> Fx.particle(p, Particle.END_ROD, 1, 0, 0));
        Fx.line(ground, ground.clone().add(Math.cos(hour) * (radius - 2), 0, Math.sin(hour) * (radius - 2)), 0.5,
                p -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(GOLD, 1.3f)));
        // a dome of slowly falling motes
        double a = ticks * 0.6;
        for (int i = 0; i < 3; i++) {
            double theta = a + i * 2.1;
            double phi = (ticks * 0.15 + i) % Math.PI;
            Fx.particle(center.clone().add(Math.cos(theta) * Math.sin(phi) * radius, Math.cos(phi) * radius * 0.8,
                    Math.sin(theta) * Math.sin(phi) * radius), Particle.ENCHANT, 1, 0, 0);
        }
    }

    /** Rewind: back to where you were 4 seconds ago, with that moment's health. */
    @Override
    public boolean secondary(Player player) {
        Deque<Snapshot> past = history.get(player.getUniqueId());
        int now = Bukkit.getCurrentTick();
        Snapshot target = null;
        if (past != null) {
            for (Snapshot snap : past) {             // oldest first
                if (now - snap.tick() <= REWIND_TICKS + 10) {
                    target = snap;
                    break;
                }
            }
        }
        if (target == null || now - target.tick() < 20 || target.location().getWorld() != player.getWorld()) {
            tell(player, "Hold Aeon a little longer before rewinding.");
            return false;
        }
        Location dest = target.location();
        if (!Combat.fits(dest)) {
            tell(player, "Something is in the way of your past self.");
            return false;
        }
        Location from = player.getLocation();
        Fx.line(from.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), 0.35, p -> {
            Fx.particle(p, Particle.END_ROD, 1, 0.02, 0);
            Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(CYAN, 1.2f));
        });
        Fx.particle(from.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 40, 0.4, 0.1);
        Fx.sound(from, Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.6f);
        if (!player.teleport(dest)) {
            return false;
        }
        player.setFallDistance(0);
        player.setFireTicks(0);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        if (target.health() > player.getHealth()) {
            player.setHealth(Math.min(cap, target.health()));
        }
        past.clear();
        Fx.particle(dest.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 40, 0.4, 0.1);
        Fx.sound(dest, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1.6f);
        fx().beam(from.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), 0x5FE3E0, 0.6f, 8);
        fx().rift(from.clone().add(0, 1, 0), dest.toVector().subtract(from.toVector()), 0x5FE3E0, 2.0f, 24);
        fx().flash(dest.clone().add(0, 1, 0), 0xC9FFFB, 2.4f);
        fx().castCircle(dest, 0x5FE3E0, 1.8f);
        return true;
    }

    @Override
    public void shutdown() {
        history.clear();
    }

    /** A clock hand lying on the ground: a beam from the centre outwards at {@code angle}. */
    private static org.bukkit.util.Transformation clockHand(double angle, float length, float width) {
        float x = (float) Math.cos(angle);
        float z = (float) Math.sin(angle);
        Quaternionf flat = new Quaternionf().rotationTo(0, 1, 0, x, 0, z);
        return new org.bukkit.util.Transformation(new Vector3f(x * length / 2, 0, z * length / 2), flat,
                new Vector3f(width, length, width), new Quaternionf());
    }
}
