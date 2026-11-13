package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Dragon Sword: tanky bruiser.
 * Passive Dragon Scales, on-hit Dragon's Fury, RC Dragon Breath, Sneak+RC Wyrm Dive.
 */
public final class DragonSword extends SwordAbility {

    private static final float BREATH_POWER = 1.0f;
    private static final double BREATH_RANGE = 10;
    private static final double BREATH_HALF_ANGLE = Math.toRadians(35);

    public DragonSword(OPSwordsPlugin plugin) {
        super(plugin, SwordType.DRAGON);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.KNOCKBACK_RESISTANCE, "knockback_resistance", 0.5, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        event.setDamage(event.getDamage() + 4);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.DRAGON_BREATH, 25, 0.4, 0.02, BREATH_POWER);
        Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 0.6f, 1.4f);
    }

    /** Dragon Breath: an 8-pulse cone that follows where you aim. */
    @Override
    public boolean primary(Player player) {
        Fx.sound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.3f);
        new BukkitRunnable() {
            int pulse;

            @Override
            public void run() {
                if (!alive(player) || pulse++ >= 8) {
                    cancel();
                    return;
                }
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection();
                for (double d = 1; d <= BREATH_RANGE; d += 0.7) {
                    Location point = eye.clone().add(dir.clone().multiply(d));
                    double spread = d * 0.16;
                    Fx.particle(point, Particle.DRAGON_BREATH, 3, spread, 0.01, BREATH_POWER);
                }
                Fx.sound(eye, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.6f);
                fx().flash(eye.clone().add(dir.clone().multiply(2.4)), 0xD14BE8, 1.8f);
                fx().flash(eye.clone().add(dir.clone().multiply(4.8)), 0x9A2EDB, 2.8f);
                for (LivingEntity target : combat().nearby(player, eye, BREATH_RANGE)) {
                    Vector to = Combat.center(target).toVector().subtract(eye.toVector());
                    boolean inCone = to.lengthSquared() < 1 || dir.angle(to) <= BREATH_HALF_ANGLE;
                    if (inCone && player.hasLineOfSight(target)) {
                        combat().damage(player, target, 2.0, true);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
        return true;
    }

    /** Wyrm Dive: launch up, dive down, shockwave on landing. */
    @Override
    public boolean secondary(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 1.0E-4) {
            forward.normalize().multiply(0.6);
        }
        Combat.setVelocity(player, forward.setY(1.6));
        status().protectFromFall(player, 200);
        Location start = player.getLocation();
        Fx.sound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.8f);
        Fx.sound(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.1f);
        Fx.particle(start, Particle.CLOUD, 25, 0.5, 0.05);
        Fx.particle(start, Particle.DRAGON_BREATH, 30, 0.6, 0.02, BREATH_POWER);

        new BukkitRunnable() {
            int ticks;
            boolean diving;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 140) {
                    status().setFallProtection(player, 20);
                    cancel();
                    return;
                }
                Location feet = player.getLocation();
                Fx.particle(feet, Particle.DRAGON_BREATH, 4, 0.3, 0.01, BREATH_POWER);
                if (!diving) {
                    if (ticks >= 10 && player.getVelocity().getY() <= 0.15) {
                        diving = true;
                        Vector look = player.getLocation().getDirection();
                        Combat.setVelocity(player, new Vector(look.getX() * 0.9, -2.4, look.getZ() * 0.9));
                        Fx.sound(feet, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.2f, 0.7f);
                    }
                    return;
                }
                if (player.getVelocity().getY() > -1.5) {
                    Combat.setVelocity(player, player.getVelocity().setY(-2.4));
                }
                Block below = feet.clone().subtract(0, 0.3, 0).getBlock();
                if (below.getType().isSolid() || player.isInWater() || player.isInLava()) {
                    impact(player);
                    status().setFallProtection(player, 20);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void impact(Player player) {
        Location center = player.getLocation();
        double radius = 6;
        Fx.particle(center, Particle.EXPLOSION_EMITTER, 1, 0, 0);
        for (double r = 1; r <= radius; r += 1) {
            Fx.ring(center.clone().add(0, 0.2, 0), r, (int) (r * 10),
                    p -> Fx.particle(p, Particle.DRAGON_BREATH, 1, 0.05, 0.02, BREATH_POWER));
        }
        Block ground = center.clone().subtract(0, 0.5, 0).getBlock();
        if (ground.getType().isSolid()) {
            Fx.particle(center, Particle.BLOCK, 80, 2.0, 0.1, ground.getBlockData());
        }
        for (double r = 1.6; r <= 4.6; r += 1.5) {
            int points = (int) (r * 3);
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points + r;
                displays().eruptGround(center.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 0.7f, (float) (1.4 - r * 0.15));
            }
        }
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.7f);
        Fx.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
        fx().shockwave(center, type.color().value(), 6.5f, 7);
        fx().shockwave(center, 0xC93BE0, 4f, 5);
        fx().impact(center.clone().add(0, 1, 0), type.color().value(), 3.2f);
        for (LivingEntity target : combat().nearby(player, center, radius)) {
            double distance = Combat.distanceToBody(target, center);
            double damage = Math.max(5, 12 * (1 - distance / (radius + 1)));
            combat().damage(player, target, damage, true);
            Combat.knockback(target, center, 1.4, 0.7);
        }
    }
}
