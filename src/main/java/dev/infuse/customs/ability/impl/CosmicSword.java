package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cosmic Sword: gravity and meteors.
 * Passive Zero-G, on-hit Starstrike, F Telekinesis, Sneak+F Orbit.
 */
public final class CosmicSword extends SwordAbility {

    private static final int INDIGO = 0x6C60C4;
    private static final int PINK = 0xF2C9FF;

    public CosmicSword(CustomsPlugin plugin) {
        super(plugin, SwordType.COSMIC);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.GRAVITY, "gravity", -0.3, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        addModifier(meta, Attribute.JUMP_STRENGTH, "jump", 0.05, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(meta, Attribute.FALL_DAMAGE_MULTIPLIER, "fall_damage", -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        applyEffect(target, PotionEffectType.LEVITATION, 30, 1);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.END_ROD, 15, 0.3, 0.05);
        Fx.particle(at, Particle.REVERSE_PORTAL, 10, 0.3, 0.05);
        Fx.sound(at, Sound.ENTITY_SHULKER_BULLET_HIT, 0.8f, 1.2f);
    }

    /** Ticks you can hold an enemy before it is hurled. */
    private static final int HOLD_TICKS = 60;
    /** Enemies held or in orbit right now (their gravity is off), so a reload or shutdown can let them go. */
    private final java.util.Set<LivingEntity> floating = new java.util.HashSet<>();

    @Override
    public void shutdown() {
        for (LivingEntity entity : floating) {
            if (entity.isValid()) {
                entity.setGravity(true);
            }
        }
        floating.clear();
    }

    /**
     * Telekinesis: seize the enemy under your crosshair. For three seconds it floats wherever you
     * aim, smashing into other enemies and walls on the way, then it is hurled along your aim.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity held = combat().lookTarget(player, 24, 0.6);
        if (held == null) {
            tell(player, "Look at an enemy to seize it.");
            return false;
        }
        double reach = Math.max(3.5, Math.min(7, player.getEyeLocation().distance(Combat.center(held))));
        boolean hadGravity = held.hasGravity();
        Location grabbed = Combat.center(held);
        Fx.sound(grabbed, Sound.ENTITY_SHULKER_SHOOT, 1.2f, 0.5f);
        Fx.sound(grabbed, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        fx().burst(grabbed, PINK, 2f);
        fx().wave(grabbed, player.getEyeLocation().getDirection(), 0x8E7BFF, 1.6f, 6);
        org.bukkit.entity.ItemDisplay aura = fx().orb(grabbed, 0x7B6CFF, 2.4f, HOLD_TICKS + 40);
        java.util.Map<java.util.UUID, Integer> struck = new java.util.HashMap<>();
        held.setGravity(false);
        floating.add(held);

        new BukkitRunnable() {
            int ticks;
            Location last = grabbed.clone();
            int lastSlam = -20;

            @Override
            public void run() {
                ticks++;
                boolean done = ticks >= HOLD_TICKS;
                if (!alive(player) || !held.isValid() || held.isDead() || held.getWorld() != player.getWorld()
                        || !combat().canHit(player, held)) {
                    release(false);
                    return;
                }
                Location eye = player.getEyeLocation();
                Location hold = eye.clone().add(eye.getDirection().multiply(reach));
                Location body = Combat.center(held);
                if (done) {
                    release(true);
                    return;
                }
                // pull it smoothly towards the point you're aiming at
                Vector pull = hold.toVector().subtract(body.toVector());
                double distance = pull.length();
                if (distance > 1.0E-3) {
                    pull.multiply(Math.min(1.4, distance * 0.45) / distance);
                }
                Combat.setVelocity(held, pull);
                Vector moved = body.toVector().subtract(last.toVector());
                double speed = moved.length();
                last = body.clone();
                aura.teleport(body);

                // a violet thread from your hand to it, and motes orbiting it
                if (ticks % 2 == 0) {
                    fx().beam(dev.infuse.customs.ability.Fx3D.handOrigin(player), body, 0xB8A8FF, 0.18f, 3);
                }
                double a = ticks * 0.5;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * Math.PI * 2 / 3;
                    Location mote = body.clone().add(Math.cos(ang) * 0.9, Math.sin(ticks * 0.2 + k) * 0.5, Math.sin(ang) * 0.9);
                    Fx.particle(mote, Particle.END_ROD, 1, 0, 0);
                }
                if (ticks % 10 == 0) {
                    Fx.sound(body, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
                }

                // swung fast into other enemies, it bowls them over
                if (speed > 0.45) {
                    for (LivingEntity other : combat().nearby(player, body, 1.4)) {
                        Integer when = struck.get(other.getUniqueId());
                        if (other == held || (when != null && ticks - when < 12)) {
                            continue;
                        }
                        struck.put(other.getUniqueId(), ticks);
                        combat().damage(player, other, 5, true);
                        combat().damage(player, held, 2, true);
                        Combat.setVelocity(other, moved.clone().normalize().multiply(1.1).setY(0.45));
                        fx().burst(Combat.center(other), PINK, 1.8f);
                        Fx.sound(body, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
                    }
                }
                // ...and slammed into a wall or the floor, it hurts
                if (speed > 0.5 && ticks - lastSlam > 10) {
                    RayTraceResult wall = held.getWorld().rayTraceBlocks(body, moved, 0.9 + held.getWidth() / 2,
                            FluidCollisionMode.NEVER, true);
                    if (wall != null) {
                        lastSlam = ticks;
                        combat().damage(player, held, 4, true);
                        Location hit = wall.getHitPosition().toLocation(held.getWorld());
                        fx().burst(hit, 0x7B6CFF, 2f);
                        Fx.particle(hit, Particle.BLOCK, 18, 0.3, 0.1, wall.getHitBlock() == null
                                ? Material.STONE.createBlockData() : wall.getHitBlock().getBlockData());
                        Fx.sound(hit, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.6f);
                    }
                }
            }

            private void release(boolean hurl) {
                cancel();
                held.setGravity(hadGravity);
                floating.remove(held);
                displays().remove(aura);
                if (!hurl || !held.isValid()) {
                    return;
                }
                Vector look = player.getEyeLocation().getDirection();
                Combat.setVelocity(held, look.clone().multiply(2.1).add(new Vector(0, 0.25, 0)));
                combat().damage(player, held, 4, true);
                Location at = Combat.center(held);
                fx().wave(at, look, PINK, 2.2f, 7);
                fx().flash(at, 0x8E7BFF, 2.2f);
                Fx.sound(at, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 0.6f);
                Fx.sound(at, Sound.ENTITY_SHULKER_BULLET_HIT, 1f, 0.6f);
                fx().follow(held, 0xB8A8FF, 1.4f, 20);
                watchLanding(player, held);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** After a hurl: whatever it hits first (the ground, a wall, another enemy) takes the impact. */
    private void watchLanding(Player player, LivingEntity thrown) {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!thrown.isValid() || thrown.isDead() || ticks > 40) {
                    cancel();
                    return;
                }
                Location at = Combat.center(thrown);
                List<LivingEntity> near = combat().nearby(player, at, 1.3);
                near.remove(thrown);
                boolean wall = thrown.getWorld().rayTraceBlocks(at, thrown.getVelocity().lengthSquared() < 1.0E-4
                        ? new Vector(0, -1, 0) : thrown.getVelocity(), 1.0, FluidCollisionMode.NEVER, true) != null;
                if (ticks < 3 || (!thrown.isOnGround() && !wall && near.isEmpty())) {
                    return;
                }
                cancel();
                combat().damage(player, thrown, 6, true);
                for (LivingEntity other : near) {
                    combat().damage(player, other, 5, true);
                    Combat.knockback(other, at, 1.0, 0.4);
                }
                Location ground = Combat.ground(at);
                fx().crack(ground, 0x7B6CFF, 2.6f, 30);
                fx().burst(at, PINK, 2.4f);
                displays().eruptGround(ground, 0.55f, 0.6f);
                Fx.particle(at, Particle.REVERSE_PORTAL, 30, 0.4, 0.1);
                Fx.sound(at, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 1.1f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Orbit: how many enemies, how far out, for how long. */
    private static final int ORBIT_MAX = 5;
    private static final double ORBIT_RADIUS = 3.3;
    private static final int ORBIT_TICKS = 100;

    /**
     * Orbit: up to five nearby enemies are torn off their feet and swung into orbit around you for
     * five seconds. They circle you as you move, smashing into anything else in the way, and when
     * the orbit breaks they are flung off into the distance.
     */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> moons = new java.util.ArrayList<>();
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 9)) {
            if (player.hasLineOfSight(target)) {
                moons.add(target);
            }
            if (moons.size() >= ORBIT_MAX) {
                break;
            }
        }
        if (moons.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        Location center = player.getLocation().add(0, 1, 0);
        Fx.sound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.6f);
        Fx.sound(center, Sound.ENTITY_SHULKER_SHOOT, 1.2f, 0.5f);
        fx().wave(center, new Vector(0, 1, 0), 0x8E7BFF, (float) ORBIT_RADIUS, 8);
        java.util.Map<LivingEntity, Boolean> hadGravity = new java.util.HashMap<>();
        for (LivingEntity moon : moons) {
            hadGravity.put(moon, moon.hasGravity());
            moon.setGravity(false);
            floating.add(moon);
            combat().damage(player, moon, 2, true);
            fx().beam(center, Combat.center(moon), PINK, 0.2f, 8);
        }
        java.util.Map<java.util.UUID, Integer> bumped = new java.util.HashMap<>();

        new BukkitRunnable() {
            int ticks;
            double spin;

            @Override
            public void run() {
                ticks++;
                moons.removeIf(m -> {
                    if (!m.isValid() || m.isDead() || m.getWorld() != player.getWorld()) {
                        release(m);
                        return true;
                    }
                    return false;
                });
                if (!alive(player) || moons.isEmpty() || ticks > ORBIT_TICKS) {
                    fling();
                    return;
                }
                // it starts slow and winds up
                spin += 0.08 + Math.min(0.14, ticks * 0.003);
                Location core = player.getLocation().add(0, 1.1, 0);
                for (int i = 0; i < moons.size(); i++) {
                    LivingEntity moon = moons.get(i);
                    double a = spin + i * Math.PI * 2 / moons.size();
                    double tilt = Math.sin(a * 2 + i) * 0.6;
                    Location slot = core.clone().add(Math.cos(a) * ORBIT_RADIUS, tilt, Math.sin(a) * ORBIT_RADIUS);
                    Vector pull = slot.toVector().subtract(Combat.center(moon).toVector());
                    double distance = pull.length();
                    if (distance > 1.0E-3) {
                        Combat.setVelocity(moon, pull.multiply(Math.min(1.6, distance * 0.55) / distance));
                    }
                    Location at = Combat.center(moon);
                    Fx.particle(at, Particle.END_ROD, 1, 0.1, 0.01);
                    if (ticks % 2 == 0) {
                        Fx.particle(at, Particle.DUST, 2, 0.2, 0, Fx.dust(i % 2 == 0 ? PINK : 0x8E7BFF, 1.2f));
                    }
                    if (ticks % 20 == 0) {
                        combat().damage(player, moon, 1, true);
                    }
                    // anything in its path gets bowled over
                    for (LivingEntity other : combat().nearby(player, at, 1.4)) {
                        Integer last = bumped.get(other.getUniqueId());
                        if (moons.contains(other) || (last != null && ticks - last < 10)) {
                            continue;
                        }
                        bumped.put(other.getUniqueId(), ticks);
                        combat().damage(player, other, 4, true);
                        Combat.knockback(other, core, 1.1, 0.45);
                        fx().burst(Combat.center(other), PINK, 1.6f);
                        Fx.sound(other.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                    }
                }
                // the orbit's path, faintly
                if (ticks % 3 == 0) {
                    Fx.ring(core, ORBIT_RADIUS, 28, p -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(0x6C60C4, 0.7f)));
                }
                if (ticks % 15 == 0) {
                    Fx.sound(core, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.4f + (float) Math.min(0.6, ticks * 0.006));
                }
            }

            private void release(LivingEntity moon) {
                Boolean had = hadGravity.get(moon);
                moon.setGravity(had == null || had);
                floating.remove(moon);
            }

            private void fling() {
                cancel();
                Location core = player.getLocation().add(0, 1.1, 0);
                for (LivingEntity moon : moons) {
                    release(moon);
                    Vector out = Combat.center(moon).toVector().subtract(core.toVector()).setY(0);
                    if (out.lengthSquared() < 1.0E-4) {
                        out = new Vector(1, 0, 0);
                    }
                    out.normalize();
                    Vector tangent = new Vector(-out.getZ(), 0, out.getX());
                    Combat.setVelocity(moon, out.multiply(1.3).add(tangent.multiply(0.9)).setY(0.55));
                    if (alive(player)) {
                        combat().damage(player, moon, 4, true);
                    }
                    fx().burst(Combat.center(moon), 0x8E7BFF, 2f);
                    fx().follow(moon, PINK, 1.2f, 16);
                }
                Fx.sound(core, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 0.6f);
                Fx.sound(core, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static Location snapToGround(Location point) {
        RayTraceResult hit = point.getWorld().rayTraceBlocks(point.clone().add(0, 6, 0), new Vector(0, -1, 0), 20,
                FluidCollisionMode.NEVER, true);
        return hit == null ? point : hit.getHitPosition().toLocation(point.getWorld());
    }

}
