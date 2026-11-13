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
 * Passive Ocean's Blessing, on-hit Undertow, F Riptide Surge, Sneak+F Ride the Tide.
 */
public final class TideSword extends SwordAbility {

    private static final org.bukkit.block.data.BlockData WATER = org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
    private static final org.bukkit.block.data.BlockData FOAM_BLOCK = org.bukkit.Material.WHITE_STAINED_GLASS.createBlockData();

    private static final int TEAL = 0x2EC4B6;
    private static final int FOAM = 0xE6FFFB;
    private static final int DEEP = 0x14707A;

    public TideSword(CustomsPlugin plugin) {
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
                // a spiralling wake of water left behind the surge
                if (ticks % 2 == 0) {
                    fx().wave(at.clone().subtract(dir.clone().multiply(0.8)), dir, ticks % 4 == 0 ? 0x3FA9F5 : 0x9FE0FF, 1.6f, 6);
                }
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

    /** Ride the Tide: how long you surf, how fast, and how sharply you can carve. */
    private static final int RIDE_TICKS = 70;
    private static final double RIDE_SPEED = 0.8;
    private static final double RIDE_TURN = 0.16;

    /**
     * Ride the Tide: a wall of water rises behind you and you surf its face for three and a half
     * seconds, steering with your crosshair. Enemies in your path are swept up and carried along
     * in front of the wave until it breaks. Sneak to jump off early.
     */
    @Override
    public boolean secondary(Player player) {
        Location origin = player.getLocation();
        Fx.sound(origin, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);
        Fx.sound(origin, Sound.ITEM_BUCKET_EMPTY, 1.2f, 0.5f);
        Fx.sound(origin, Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 0.8f);
        Fx.particle(origin.clone().add(0, 0.5, 0), Particle.SPLASH, 60, 1.2, 0.3);
        status().protectFromFall(player, RIDE_TICKS + 60);
        Set<UUID> hit = new HashSet<>();
        java.util.Map<UUID, Integer> carried = new java.util.HashMap<>();
        // the wave: a board of water under your feet and a V of water curling up in front of you
        // (low enough that you can see over it), each column capped with foam
        java.util.List<org.bukkit.entity.BlockDisplay> wall = new java.util.ArrayList<>();
        java.util.List<double[]> slots = new java.util.ArrayList<>();
        Location spawnAt = origin.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        org.bukkit.entity.BlockDisplay board = displays().block(spawnAt, WATER, new org.joml.Vector3f(1.5f, 0.3f, 2.4f),
                new org.joml.Quaternionf(), RIDE_TICKS + 20);
        board.setBrightness(new org.bukkit.entity.Display.Brightness(12, 15));
        board.setTeleportDuration(1);
        for (int k = -3; k <= 3; k++) {
            double side = k * 0.6;
            double ahead = 1.5 - Math.abs(k) * 0.5;
            double h = 0.45 + Math.abs(k) * 0.2;
            org.bukkit.entity.BlockDisplay column = displays().block(spawnAt, WATER, new org.joml.Vector3f(0.6f, 0.1f, 0.5f),
                    new org.joml.Quaternionf(), RIDE_TICKS + 20);
            column.setBrightness(new org.bukkit.entity.Display.Brightness(12, 15));
            column.setTeleportDuration(1);
            org.bukkit.entity.BlockDisplay foam = displays().block(spawnAt, FOAM_BLOCK, new org.joml.Vector3f(0.62f, 0.2f, 0.55f),
                    new org.joml.Quaternionf(), RIDE_TICKS + 20);
            foam.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            foam.setTeleportDuration(1);
            wall.add(column);
            wall.add(foam);
            slots.add(new double[]{side, ahead, h});
        }
        wall.add(board);

        new BukkitRunnable() {
            int ticks;
            Vector heading = dev.infuse.customs.ability.Aim.flatLook(player);

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || player.getWorld() != origin.getWorld() || ticks > RIDE_TICKS
                        || (ticks > 8 && player.isSneaking())) {
                    breakWave();
                    return;
                }
                // carve towards your crosshair
                heading = dev.infuse.customs.ability.Aim.turnToward(heading, dev.infuse.customs.ability.Aim.flatLook(player), RIDE_TURN);
                Vector right = Fx.right(heading);
                Location feet = player.getLocation();
                double vy;
                boolean wallAhead = !feet.clone().add(heading.clone().multiply(0.8)).getBlock().isPassable();
                if (wallAhead && feet.clone().add(heading.clone().multiply(0.8)).add(0, 1, 0).getBlock().isPassable()) {
                    vy = 0.5; // ride up a step
                } else if (!feet.clone().subtract(0, 0.08, 0).getBlock().isPassable()) {
                    vy = 0.12;
                } else {
                    vy = Math.max(-0.35, player.getVelocity().getY() - 0.05);
                }
                Combat.setVelocity(player, heading.clone().multiply(RIDE_SPEED).setY(vy));

                // the wave rides with you, turned the way you're going
                float rise = Math.min(1f, ticks / 5f);
                org.joml.Quaternionf facing = new org.joml.Quaternionf().rotateY((float) Math.atan2(heading.getX(), heading.getZ()));
                Location ground = Combat.ground(feet.clone().add(0, 0.6, 0));
                if (board.isValid()) {
                    board.teleport(ground.clone().add(0, 0.15, 0));
                    dev.infuse.customs.ability.Displays.animateBlock(board, new org.joml.Vector3f(1.5f, 0.3f, 2.4f), facing,
                            new org.joml.Vector3f(), 1);
                }
                for (int i = 0; i < slots.size(); i++) {
                    double[] slot = slots.get(i);
                    double h = slot[2] * rise * (0.8 + 0.2 * Math.sin(ticks * 0.6 + i));
                    Location base = Combat.ground(feet.clone().add(right.clone().multiply(slot[0]))
                            .add(heading.clone().multiply(slot[1])).add(0, 0.6, 0));
                    org.bukkit.entity.BlockDisplay column = wall.get(i * 2);
                    org.bukkit.entity.BlockDisplay foam = wall.get(i * 2 + 1);
                    if (column.isValid()) {
                        column.teleport(base.clone().add(0, h / 2, 0));
                        dev.infuse.customs.ability.Displays.animateBlock(column, new org.joml.Vector3f(0.6f, (float) Math.max(0.1, h), 0.5f),
                                facing, new org.joml.Vector3f(), 1);
                    }
                    if (foam.isValid()) {
                        foam.teleport(base.clone().add(0, h + 0.05, 0));
                        dev.infuse.customs.ability.Displays.animateBlock(foam, new org.joml.Vector3f(0.62f, 0.2f, 0.55f), facing,
                                new org.joml.Vector3f(), 1);
                    }
                }
                Location spray = feet.clone().add(0, 0.3, 0);
                Fx.particle(spray, Particle.SPLASH, 10, 0.6, 0.1);
                Fx.particle(spray.clone().subtract(heading.clone().multiply(1.2)), Particle.FALLING_WATER, 4, 1.2, 0);
                Fx.particle(feet.clone().add(0, 0.1, 0), Particle.BUBBLE_POP, 4, 0.5, 0.02);
                if (ticks % 2 == 0) {
                    fx().slash(feet.clone().add(0, 0.15, 0).subtract(heading.clone().multiply(0.6)), heading.clone().multiply(-1), 0,
                            ticks % 4 == 0 ? 0x3FA9F5 : 0x9FE0FF, 1.8f, 2);
                }
                if (ticks % 5 == 0) {
                    Fx.sound(feet, Sound.BLOCK_WATER_AMBIENT, 1.5f, 1.6f);
                }
                // sweep up whatever is in front of you and carry it along
                Location front = feet.clone().add(heading.clone().multiply(1.2)).add(0, 0.8, 0);
                for (LivingEntity target : combat().nearby(player, front, 2.3)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 6);
                        target.setFireTicks(0);
                        applyEffect(target, PotionEffectType.SLOWNESS, 60, 1);
                        Fx.particle(Combat.center(target), Particle.SPLASH, 25, 0.4, 0.2);
                        Fx.sound(target.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 1.2f);
                    }
                    carried.put(target.getUniqueId(), ticks);
                    Combat.setVelocity(target, heading.clone().multiply(RIDE_SPEED + 0.2).setY(0.28));
                }
            }

            private void breakWave() {
                cancel();
                Location feet = player.getLocation();
                Fx.sound(feet, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.5f);
                Fx.sound(feet, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.7f);
                Fx.particle(feet.clone().add(0, 1, 0), Particle.SPLASH, 80, 1.6, 0.4);
                for (org.bukkit.entity.BlockDisplay d : wall) {
                    if (d.isValid()) {
                        Fx.particle(d.getLocation(), Particle.FALLING_WATER, 6, 0.4, 0.1);
                    }
                    displays().remove(d);
                }
                // the wave crashes down on whatever it was carrying
                for (LivingEntity target : combat().nearby(player, feet, 6)) {
                    if (carried.containsKey(target.getUniqueId())) {
                        combat().damage(player, target, 4, true);
                        Combat.setVelocity(target, heading.clone().multiply(1.1).setY(0.55));
                        fx().burst(Combat.center(target), 0x9FE0FF, 1.6f);
                    }
                }
                if (alive(player)) {
                    Combat.setVelocity(player, heading.clone().multiply(0.5).setY(0.35));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
