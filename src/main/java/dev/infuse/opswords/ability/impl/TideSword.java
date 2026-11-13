package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tidebreaker, the Tide Sword: water mobility and crowd control.
 * Passive Ocean's Blessing, on-hit Undertow, RC Riptide Surge, Sneak+RC Tidal Wave.
 */
public final class TideSword extends SwordAbility {

    private static final org.bukkit.block.data.BlockData WATER = org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
    private static final org.bukkit.block.data.BlockData FOAM_BLOCK = org.bukkit.Material.WHITE_STAINED_GLASS.createBlockData();

    private static final int TEAL = 0x2EC4B6;
    private static final int FOAM = 0xE6FFFB;
    private static final int DEEP = 0x14707A;

    public TideSword(OPSwordsPlugin plugin) {
        super(plugin, SwordType.TIDE);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.CONDUIT_POWER, 0, 0));
        out.add(effect(PotionEffectType.DOLPHINS_GRACE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (pull.lengthSquared() > 1.0E-4) {
            Combat.setVelocity(target, pull.normalize().multiply(0.7).setY(0.25));
        }
        applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SPLASH, 30, 0.4, 0.1);
        Fx.particle(at, Particle.BUBBLE_POP, 15, 0.4, 0.05);
        Fx.sound(at, Sound.ENTITY_PLAYER_SPLASH, 0.6f, 1.2f);
    }

    /** Riptide Surge: launch forward with the riptide spin, hitting everything along the way. */
    @Override
    public boolean primary(Player player) {
        Vector dir = player.getLocation().getDirection();
        dir.setY(Math.max(-0.2, Math.min(0.45, dir.getY())));
        dir.normalize();
        Combat.setVelocity(player, dir.clone().multiply(2.2).add(new Vector(0, 0.3, 0)));
        player.startRiptideAttack(14, 0f, player.getInventory().getItemInMainHand());
        status().protectFromFall(player, 60);
        Fx.sound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1f);
        Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 1f);
        fx().shockwave(player.getLocation(), 0x3FA9F5, 2.4f, 6);
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || ticks++ >= 14) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 0.8, 0);
                Fx.particle(at, Particle.SPLASH, 12, 0.4, 0.1);
                Fx.particle(at, Particle.FALLING_WATER, 6, 0.4, 0);
                Fx.particle(at, Particle.DUST, 4, 0.35, 0, Fx.dust(TEAL, 1.5f));
                for (LivingEntity target : combat().nearby(player, at, 2.2)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 6);
                        applyEffect(target, PotionEffectType.SLOWNESS, 30, 1);
                        Vector side = Fx.right(dir).multiply(sideOf(at, dir, target) * 0.9).setY(0.35);
                        Combat.setVelocity(target, side.add(dir.clone().multiply(0.4)));
                        Fx.particle(Combat.center(target), Particle.SPLASH, 20, 0.3, 0.2);
                        Fx.sound(target.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.3f);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** +1 if the target is to the right of the path, -1 if to the left. */
    private static double sideOf(Location pathPoint, Vector dir, LivingEntity target) {
        Vector offset = target.getLocation().toVector().subtract(pathPoint.toVector());
        return Fx.right(dir).dot(offset) >= 0 ? 1 : -1;
    }

    /** Tidal Wave: a wall of water rolls 16 blocks forward, carrying enemies with it. */
    @Override
    public boolean secondary(Player player) {
        Location origin = player.getLocation();
        Vector dir = origin.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector right = Fx.right(dir);
        Fx.sound(origin, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);
        Fx.sound(origin, Sound.ITEM_BUCKET_EMPTY, 1.2f, 0.5f);
        fx().shockwave(origin, 0x8FD3FF, 3f, 6);
        Set<UUID> hit = new HashSet<>();
        Vector forward = dir;
        // the wall of water: glass columns across the front, tallest in the middle, capped with foam
        org.joml.Quaternionf facing = new org.joml.Quaternionf().rotateY((float) Math.atan2(forward.getX(), forward.getZ()));
        java.util.List<org.bukkit.entity.BlockDisplay> wall = new java.util.ArrayList<>();
        java.util.List<double[]> slots = new java.util.ArrayList<>();
        Location spawnAt = origin.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        for (double w = -3.5; w <= 3.51; w += 0.875) {
            double h = 2.6 * (1 - Math.abs(w) / 4.6);
            org.bukkit.entity.BlockDisplay column = displays().block(spawnAt, WATER, new org.joml.Vector3f(0.95f, (float) h, 0.55f),
                    facing, 30);
            column.setBrightness(new org.bukkit.entity.Display.Brightness(12, 15));
            column.setTeleportDuration(1);
            org.bukkit.entity.BlockDisplay foam = displays().block(spawnAt, FOAM_BLOCK, new org.joml.Vector3f(0.98f, 0.28f, 0.62f),
                    facing, 30);
            foam.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            foam.setTeleportDuration(1);
            wall.add(column);
            wall.add(foam);
            slots.add(new double[]{w, h});
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != origin.getWorld() || ticks++ >= 16) {
                    for (org.bukkit.entity.BlockDisplay d : wall) {
                        if (d.isValid()) {
                            Fx.particle(d.getLocation(), Particle.SPLASH, 12, 0.4, 0.1);
                        }
                        displays().remove(d);
                    }
                    cancel();
                    return;
                }
                double front = 1 + ticks;
                Location center = origin.clone().add(forward.clone().multiply(front));
                for (int i = 0; i < slots.size(); i++) {
                    double w = slots.get(i)[0];
                    double h = slots.get(i)[1] * (0.75 + 0.25 * Math.sin(ticks * 0.6 + i));
                    Location base = Combat.ground(center.clone().add(right.clone().multiply(w)).add(0, 1.5, 0));
                    org.bukkit.entity.BlockDisplay column = wall.get(i * 2);
                    org.bukkit.entity.BlockDisplay foam = wall.get(i * 2 + 1);
                    if (column.isValid()) {
                        column.teleport(base.clone().add(0, h / 2, 0));
                        dev.infuse.opswords.ability.Displays.animateBlock(column, new org.joml.Vector3f(0.95f, (float) h, 0.55f), facing,
                                new org.joml.Vector3f(), 1);
                    }
                    if (foam.isValid()) {
                        foam.teleport(base.clone().add(0, h + 0.1, 0).add(forward.clone().multiply(0.12)));
                    }
                }
                for (double w = -3.5; w <= 3.5; w += 0.5) {
                    double height = 2.4 * (1 - Math.abs(w) / 4.2);
                    Location base = center.clone().add(right.clone().multiply(w));
                    for (double y = 0; y <= height; y += 0.45) {
                        Location p = base.clone().add(0, y, 0);
                        Fx.particle(p, Particle.DUST, 1, 0.08, 0, Fx.dust(y > height - 0.5 ? FOAM : (y < 0.5 ? DEEP : TEAL), 1.7f));
                    }
                    Fx.particle(base.clone().add(0, height + 0.2, 0), Particle.SPLASH, 3, 0.15, 0.1);
                    if (((int) (w * 2)) % 3 == 0) {
                        Fx.particle(base.clone().add(0, height, 0), Particle.FALLING_WATER, 2, 0.2, 0);
                    }
                }
                if (ticks % 4 == 0) {
                    Fx.sound(center, Sound.BLOCK_WATER_AMBIENT, 1.5f, 1.4f);
                }
                for (LivingEntity target : combat().nearby(player, center, 5)) {
                    Vector rel = target.getLocation().toVector().subtract(origin.toVector());
                    double along = rel.dot(forward);
                    double across = Math.abs(rel.dot(right));
                    if (along < front - 1.5 || along > front + 0.8 || across > 3.8 || Math.abs(rel.getY()) > 3) {
                        continue;
                    }
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 7);
                        target.setFireTicks(0);
                        applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
                    }
                    Combat.setVelocity(target, forward.clone().multiply(1.05).setY(0.35));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
