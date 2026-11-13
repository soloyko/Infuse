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
 * Passive Zero-G, on-hit Starstrike, F Telekinesis, Sneak+F Meteor Shower.
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
    /** Enemies held right now (their gravity is off), so a reload or shutdown can let them go. */
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

    /** Meteor Shower: 8 meteors on the targeted area. */
    @Override
    public boolean secondary(Player player) {
        Location target = combat().aimPoint(player, 40);
        if (target == null) {
            Location eye = player.getEyeLocation();
            target = Combat.ground(eye.clone().add(eye.getDirection().setY(0).normalize().multiply(20)));
        }
        Location area = target;
        Vector approach = player.getLocation().getDirection().setY(0);
        if (approach.lengthSquared() < 1.0E-4) {
            approach = new Vector(1, 0, 0);
        }
        Vector from = approach.normalize().multiply(-10);
        Fx.sound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
        Fx.sound(area, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);

        new BukkitRunnable() {
            int launched;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != area.getWorld() || launched >= 8) {
                    cancel();
                    return;
                }
                launched++;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double a = r.nextDouble(Math.PI * 2);
                double d = Math.sqrt(r.nextDouble()) * 5;
                Location impact = snapToGround(area.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d));
                Location start = impact.clone().add(from).add(r.nextDouble(-2, 2), 22, r.nextDouble(-2, 2));
                meteor(player, start, impact);
            }
        }.runTaskTimer(plugin, 0L, 6L);
        return true;
    }

    private static Location snapToGround(Location point) {
        RayTraceResult hit = point.getWorld().rayTraceBlocks(point.clone().add(0, 6, 0), new Vector(0, -1, 0), 20,
                FluidCollisionMode.NEVER, true);
        return hit == null ? point : hit.getHitPosition().toLocation(point.getWorld());
    }

    private void meteor(Player player, Location start, Location impact) {
        Fx.sound(impact, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 0.6f);
        BlockDisplay rock = displays().block(start, Material.MAGMA_BLOCK.createBlockData(), new Vector3f(0.9f),
                new Quaternionf(), 20);
        rock.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        org.bukkit.entity.ItemDisplay flare = fx().orb(start, 0xFF8A3A, 2.2f, 20);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                double progress = t / 14.0;
                Location pos = start.clone().add(impact.toVector().subtract(start.toVector()).multiply(Math.min(1, progress)));
                if (flare.isValid()) {
                    Location f = pos.clone();
                    f.setYaw(0);
                    f.setPitch(0);
                    flare.teleport(f);
                }
                if (rock.isValid()) {
                    rock.teleport(pos);
                    Quaternionf tumble = new Quaternionf().rotateXYZ(t * 0.45f, t * 0.3f, t * 0.2f);
                    rock.setInterpolationDelay(0);
                    rock.setInterpolationDuration(1);
                    rock.setTransformation(Displays.centered(new Vector3f(0.9f), tumble));
                }
                Fx.particle(pos, Particle.FLAME, 6, 0.25, 0.02);
                Fx.particle(pos, Particle.LARGE_SMOKE, 2, 0.2, 0.01);
                Fx.particle(pos, Particle.DUST, 4, 0.3, 0, Fx.dust(t % 2 == 0 ? INDIGO : PINK, 2.0f));
                Fx.particle(pos, Particle.END_ROD, 1, 0.1, 0.01);
                if (progress < 1) {
                    return;
                }
                cancel();
                displays().remove(rock);
                displays().remove(flare);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    displays().eruptGround(impact.clone().add(Math.cos(a) * 1.3, 0.5, Math.sin(a) * 1.3), 0.55f, 0.6f);
                }
                Fx.particle(impact, Particle.EXPLOSION, 4, 0.8, 0);
                Fx.burst(impact.clone().add(0, 0.3, 0), Particle.FLAME, 30, 0.25);
                Fx.particle(impact, Particle.DUST, 30, 1.5, 0, Fx.dust(INDIGO, 1.8f));
                Fx.particle(impact, Particle.LAVA, 6, 0.5, 0);
                Fx.sound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
                fx().impact(impact.clone().add(0, 0.8, 0), 0x8E7BFF, 2.6f);
                fx().flamePillar(impact, 0.9f, 1.6f, 12, false);
                if (!alive(player)) {
                    return;
                }
                for (LivingEntity target : combat().nearby(player, impact, 3.5)) {
                    combat().damage(player, target, 6, true);
                    target.setFireTicks(Math.max(target.getFireTicks(), 40));
                    Combat.knockback(target, impact, 0.9, 0.5);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
