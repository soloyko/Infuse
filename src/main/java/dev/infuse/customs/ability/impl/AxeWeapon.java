package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ragnarök, the Berserker Axe (netherite axe): stronger the closer you are to death.
 * Passive Bloodlust, on-hit Cleave, F Whirlwind, Sneak+F Warcry.
 */
public final class AxeWeapon extends SwordAbility {

    private static final float MODEL_SCALE = 2.0f;
    private static final int RAGE = 0xB0305A;
    private static final int RUNE = 0xC77DFF;

    public AxeWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.AXE);
    }

    /** 0 at full health, 1 at 30% health or less. */
    private static double rage(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double fraction = player.getHealth() / (max == null ? 20 : max.getValue());
        return Math.max(0, Math.min(1, (1 - fraction) / 0.7));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        double rage = rage(player);
        if (rage > 0) {
            event.setDamage(event.getDamage() * (1 + 0.5 * rage));
            if (rage > 0.5) {
                Fx.particle(Combat.center(target), Particle.DUST, 10, 0.3, 0, Fx.dust(RAGE, 1.3f));
            }
        }
        double cleave = event.getDamage() * 0.4;
        boolean any = false;
        for (LivingEntity near : combat().nearby(player, target.getLocation(), 2.5)) {
            if (near != target) {
                combat().damage(player, near, cleave);
                any = true;
            }
        }
        if (any) {
            Fx.particle(Combat.center(target), Particle.SWEEP_ATTACK, 2, 0.4, 0);
            Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
        }
    }

    /** Whirlwind: 2.5 seconds of spinning with the axe out. */
    @Override
    public boolean primary(Player player) {
        applyEffect(player, PotionEffectType.SPEED, 50, 0);
        ItemDisplay axe = displays().item(orbit(player, 0), plugin.items().create(type), MODEL_SCALE, blade(0),
                ItemDisplay.ItemDisplayTransform.NONE, 60);
        Fx.sound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 0.6f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 50) {
                    displays().remove(axe);
                    cancel();
                    return;
                }
                if (axe.isValid()) {
                    axe.teleport(orbit(player, ticks));
                    axe.setInterpolationDelay(0);
                    axe.setInterpolationDuration(1);
                    axe.setTransformation(new Transformation(new Vector3f(), blade(ticks), new Vector3f(MODEL_SCALE), new Quaternionf()));
                }
                Location center = player.getLocation().add(0, 1, 0);
                double a = ticks * 0.8;
                Fx.particle(center.clone().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), Particle.DUST, 2, 0.1, 0, Fx.dust(RUNE, 1.4f));
                if (ticks % 5 == 0) {
                    Fx.ring(center, 2.4, 6, p -> Fx.particle(p, Particle.SWEEP_ATTACK, 1, 0, 0));
                    Fx.sound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                    fx().slash(center, new Vector(Math.cos(a), 0, Math.sin(a)), 0.1, 0xC77DFF, 2.8f, 3);
                    for (LivingEntity target : combat().nearby(player, center, 3.5)) {
                        combat().damage(player, target, 3, true);
                        Combat.knockback(target, center, 0.3, 0.15);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static Location orbit(Player player, int ticks) {
        double a = ticks * 0.8;
        Location base = player.getLocation();
        base.setYaw(0);
        base.setPitch(0);
        return base.add(Math.cos(a) * 1.3, 1.0, Math.sin(a) * 1.3);
    }

    /** Axe lying flat with the blade facing outward as it circles. */
    private static Quaternionf blade(int ticks) {
        double a = ticks * 0.8;
        return new Quaternionf().rotateY((float) (-a + Math.PI / 2)).rotateZ((float) Math.toRadians(-90));
    }

    /** Warcry: buff yourself, weaken and scatter everyone near you. */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Fx.sound(center, Sound.ITEM_GOAT_HORN_SOUND_2, 2f, 0.8f);
        Fx.sound(center, Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
        Vector forward = center.getDirection().setY(0).lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1)
                : center.getDirection().setY(0).normalize();
        // a giant spectral axe rises behind you and comes down on the ground ahead
        Location axeAt = center.clone().subtract(forward.clone().multiply(0.6)).add(0, 3.2, 0);
        axeAt.setYaw(0);
        axeAt.setPitch(0);
        org.bukkit.entity.ItemDisplay axe = displays().item(axeAt, plugin.items().create(type), 4.5f,
                new org.joml.Quaternionf(), org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE, 30);
        axe.setGlowing(true);
        axe.setGlowColorOverride(org.bukkit.Color.fromRGB(0xFF3A5A));
        float yaw = (float) Math.atan2(-forward.getX(), forward.getZ());
        org.joml.Quaternionf raised = new org.joml.Quaternionf().rotateY(-yaw).rotateX(-0.9f);
        org.joml.Quaternionf struck = new org.joml.Quaternionf().rotateY(-yaw).rotateX(1.7f);
        axe.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), raised, new org.joml.Vector3f(4.5f),
                new org.joml.Quaternionf()));
        displays().later(5, () -> dev.infuse.customs.ability.Displays.animate(axe, new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, -1.0f, 0), struck, new org.joml.Vector3f(4.5f), new org.joml.Quaternionf()), 4));
        Location strike = Combat.ground(center.clone().add(forward.clone().multiply(3.2)).add(0, 1.5, 0));
        displays().later(9, () -> {
            fx().crack(strike, 0xFF4A6A, 6f, 50);
            fx().shockwave(strike, 0xC77DFF, 8f, 9);
            fx().shockwave(strike, 0xFF4A6A, 5.5f, 12);
            fx().impact(strike.clone().add(0, 0.6, 0), 0xFF4A6A, 2.6f);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                displays().eruptGround(strike.clone().add(Math.cos(a) * 1.6, 0.3, Math.sin(a) * 1.6), 0.6f, 0.9f);
            }
            Fx.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
            Fx.sound(strike, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        });
        displays().later(20, () -> displays().remove(axe));
        applyEffect(player, PotionEffectType.STRENGTH, 160, 1);
        applyEffect(player, PotionEffectType.RESISTANCE, 160, 0);
        combat().heal(player, 4);
        new BukkitRunnable() {
            double radius = 1;

            @Override
            public void run() {
                if (radius > 8 || player.getWorld() != center.getWorld()) {
                    cancel();
                    return;
                }
                Location ring = center.clone().add(0, 0.4, 0);
                Fx.ring(ring, radius, (int) (radius * 8), p -> Fx.particle(p, Particle.DUST, 1, 0.05, 0,
                        Fx.dust(radius % 2 < 1 ? RAGE : RUNE, 1.6f)));
                radius += 1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Fx.particle(center.clone().add(0, 1.2, 0), Particle.ANGRY_VILLAGER, 6, 0.5, 0);
        for (LivingEntity target : combat().nearby(player, center, 8)) {
            applyEffect(target, PotionEffectType.WEAKNESS, 100, 1);
            applyEffect(target, PotionEffectType.SLOWNESS, 60, 1);
            Combat.knockback(target, center, 1.0, 0.35);
            Fx.particle(Combat.center(target), Particle.CRIT, 10, 0.3, 0.2);
        }
        return true;
    }
}
