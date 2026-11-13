package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Displays;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
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
 * Passive Zero-G, on-hit Starstrike, RC Gravity Pulse, Sneak+RC Meteor Shower.
 */
public final class CosmicSword extends SwordAbility {

    private static final int INDIGO = 0x6C60C4;
    private static final int PINK = 0xF2C9FF;

    public CosmicSword(OPSwordsPlugin plugin) {
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

    /** Gravity Pulse: launch every enemy within 8 blocks, then slam them down. */
    @Override
    public boolean primary(Player player) {
        Location center = player.getLocation();
        List<LivingEntity> targets = combat().nearby(player, center, 8);
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        for (double r = 1; r <= 8; r += 1.4) {
            Fx.ring(center.clone().add(0, 0.2, 0), r, (int) (r * 8), p -> Fx.particle(p, Particle.REVERSE_PORTAL, 1, 0, 0.05));
        }
        Fx.sound(center, Sound.ENTITY_SHULKER_SHOOT, 1.2f, 0.5f);
        Fx.sound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f);
        for (LivingEntity target : targets) {
            combat().damage(player, target, 3);
            Vector out = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                out.normalize().multiply(0.3);
            }
            Combat.setVelocity(target, out.setY(1.3));
        }
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                for (LivingEntity target : targets) {
                    if (target.isValid() && !target.isDead()) {
                        Fx.particle(Combat.center(target), Particle.END_ROD, 2, 0.2, 0.01);
                    }
                }
                if (ticks < 18) {
                    return;
                }
                cancel();
                if (!alive(player)) {
                    return;
                }
                for (LivingEntity target : targets) {
                    if (target.isValid() && !target.isDead() && combat().canHit(player, target)) {
                        Combat.setVelocity(target, new Vector(0, -3, 0));
                        combat().damage(player, target, 4, true);
                        Fx.particle(Combat.center(target), Particle.DUST, 12, 0.3, 0, Fx.dust(INDIGO, 1.5f));
                    }
                }
                Fx.sound(player.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.8f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
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
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                double progress = t / 14.0;
                Location pos = start.clone().add(impact.toVector().subtract(start.toVector()).multiply(Math.min(1, progress)));
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
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    displays().eruptGround(impact.clone().add(Math.cos(a) * 1.3, 0.5, Math.sin(a) * 1.3), 0.55f, 0.6f);
                }
                Fx.particle(impact, Particle.EXPLOSION, 4, 0.8, 0);
                Fx.burst(impact.clone().add(0, 0.3, 0), Particle.FLAME, 30, 0.25);
                Fx.particle(impact, Particle.DUST, 30, 1.5, 0, Fx.dust(INDIGO, 1.8f));
                Fx.particle(impact, Particle.LAVA, 6, 0.5, 0);
                Fx.sound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
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
