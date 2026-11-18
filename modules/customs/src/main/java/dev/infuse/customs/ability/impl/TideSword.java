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
 * Aquatic Sacred Blade, the Tide Sword: water mobility and crowd control.
 * Passive Ocean's Blessing, on-hit Undertow, F Undertow, Sneak+F Ride the Tide.
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

    private static final int UNDERTOW_TICKS = 100;

    /**
     * Undertow: the tide takes hold of the enemy you aim at for five seconds. It can't sprint,
     * and every block it moves costs it health as the water drags at it; standing still is safe.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 24, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to set the undertow on.");
            return false;
        }
        Location at = target.getLocation();
        fx().beam(dev.infuse.customs.ability.Fx3D.handOrigin(player), Combat.center(target), TEAL, 0.25f, 8);
        fx().wave(Combat.center(target), new Vector(0, 1, 0), TEAL, 2.2f, 8);
        Fx.particle(Combat.center(target), Particle.SPLASH, 40, 0.5, 0.2);
        Fx.sound(at, Sound.AMBIENT_UNDERWATER_ENTER, 1.2f, 0.8f);
        Fx.sound(at, Sound.ENTITY_DROWNED_AMBIENT_WATER, 1.2f, 0.6f);
        org.bukkit.entity.ItemDisplay ring = fx().sigil(at.clone().add(0, 0.1, 0), new Vector(0, 1, 0), TEAL, 1.6f, UNDERTOW_TICKS + 10);
        ring.setTeleportDuration(1);

        new BukkitRunnable() {
            int t;
            Location last = target.getLocation();
            double owed;

            @Override
            public void run() {
                t++;
                if (t > UNDERTOW_TICKS || !target.isValid() || target.isDead() || !alive(player) || target.getWorld() != player.getWorld()) {
                    displays().remove(ring);
                    Fx.particle(target.getLocation().add(0, 0.5, 0), Particle.BUBBLE_POP, 20, 0.5, 0.1);
                    cancel();
                    return;
                }
                if (target instanceof Player p && p.isSprinting()) {
                    p.setSprinting(false);
                }
                Location now = target.getLocation();
                double moved = Math.hypot(now.getX() - last.getX(), now.getZ() - last.getZ())
                        + Math.max(0, now.getY() - last.getY()) * 0.5;
                last = now;
                owed += moved * 1.2;
                Location feet = now.clone().add(0, 0.1, 0);
                feet.setYaw(0);
                feet.setPitch(0);
                if (ring.isValid()) {
                    ring.teleport(feet);
                }
                if (moved > 0.05) {
                    // the water tugs at every step
                    Fx.particle(feet, Particle.SPLASH, 6, 0.3, 0.05);
                    Fx.particle(feet, Particle.BUBBLE_POP, 3, 0.3, 0.02);
                }
                if (owed >= 1) {
                    double hurt = Math.floor(owed);
                    owed -= hurt;
                    combat().damage(player, target, hurt, true);
                    Fx.particle(Combat.center(target), Particle.DUST, 4, 0.3, 0, Fx.dust(DEEP, 1.1f));
                    Fx.sound(now, Sound.ENTITY_PLAYER_HURT_DROWN, 0.6f, 1.2f);
                }
                if (t % 6 == 0) {
                    Fx.particle(Combat.center(target), Particle.DRIPPING_WATER, 4, 0.3, 0);
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
