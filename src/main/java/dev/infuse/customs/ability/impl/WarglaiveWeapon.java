package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.Fx3D;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Eclipse, the Lunar Warglaive: the moon and the dark.
 * Passive Moonlit, on-hit Crescent Echo, F Lunar Dash, Sneak+F Moonfall.
 */
public final class WarglaiveWeapon extends SwordAbility {

    private static final int SILVER = 0xC9D6FF;
    private static final int MOON = 0x8FA8FF;
    private static final int MIDNIGHT = 0x2B2F6B;
    private static final int MOON_GLOW = 0x6D7FFF;

    public WarglaiveWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.WARGLAIVE);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.NIGHT_VISION, 0, 0));
    }

    private static boolean dark(Player player) {
        long time = player.getWorld().getTime();
        boolean night = time >= 13000 && time <= 23000;
        return night || player.getLocation().getBlock().getLightLevel() < 7;
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (dark(player)) {
            event.setDamage(event.getDamage() * 1.25);
        }
        if (!chance(0.2)) {
            return;
        }
        double echo = event.getDamage() * 0.5;
        Vector look = player.getEyeLocation().getDirection();
        displays().later(6, () -> {
            if (!target.isValid() || target.isDead() || !alive(player)) {
                return;
            }
            Location at = Combat.center(target);
            fx().cut(at, look, Math.PI / 2 + 0.4, 0.5, SILVER, 1.6f, 3);
            combat().damage(player, target, echo);
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
            Fx.particle(at, Particle.END_ROD, 6, 0.3, 0.03);
        });
    }

    /** Lunar Dash: a dash through enemies that leaves silver crescents behind. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getLocation();
        Vector dir = start.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector heading = dir.clone();
        double distance = Math.min(9, Combat.clearDistance(player, 9) - 0.6);
        if (distance < 1.5) {
            tell(player, "No room to dash.");
            return false;
        }
        Combat.setVelocity(player, heading.clone().multiply(2.6).setY(0.15));
        status().protectFromFall(player, 40);
        Set<UUID> hit = new HashSet<>();
        Fx.sound(start, Sound.ENTITY_PHANTOM_FLAP, 1.2f, 1.4f);
        Fx.sound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.4f);
        fx().shockwave(start, MOON, 2f, 5);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 8) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                fx().slash(at, heading, ticks % 2 == 0 ? 0.5 : -0.5, ticks % 2 == 0 ? SILVER : MOON, 1.9f, 2);
                Fx.particle(at, Particle.END_ROD, 3, 0.3, 0.02);
                Fx.particle(at, Particle.DUST, 4, 0.3, 0, Fx.dust(MIDNIGHT, 1.4f));
                for (LivingEntity target : combat().nearby(player, at, 2.4)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 7);
                        Combat.knockback(target, at, 0.5, 0.35);
                        fx().burst(Combat.center(target), SILVER, 1.5f);
                        Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.3f);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Moonfall: a huge crescent moon descends on the target area and shatters. */
    @Override
    public boolean secondary(Player player) {
        Location aim = combat().aimPoint(player, 25);
        Location eye = player.getEyeLocation();
        Location ground = Combat.ground(aim != null ? aim
                : eye.clone().add(eye.getDirection().multiply(Combat.clearDistance(player, 18))));
        Vector facing = ground.toVector().subtract(player.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Vector face = facing.clone();
        // the crescent stands upright, horns up, its face turned to the caster: first tip its bulge
        // (+X) down, which stands the plane up facing +X, then turn that face towards the caster
        Quaternionf upright = new Quaternionf().rotationY((float) Math.atan2(-facing.getZ(), facing.getX()))
                .mul(new Quaternionf().rotationZ((float) (Math.PI / 2)));
        Location sky = ground.clone().add(0, 9.6 + 0.94 * 5.5, 0);
        sky.setYaw(0);
        sky.setPitch(0);
        float size = 5.5f;
        ItemDisplay moon = fx().spawn(sky, Fx3D.Shape.SLASH, MOON_GLOW,
                new Transformation(new Vector3f(), upright, new Vector3f(size), new Quaternionf()), Display.Billboard.FIXED, 60);
        moon.setTeleportDuration(2);
        ItemDisplay glow = fx().orb(sky, MOON, 4f, 60);
        fx().castCircle(ground, MOON, 5.5f);
        Fx.sound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
        Fx.sound(ground, Sound.ENTITY_PHANTOM_AMBIENT, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || player.getWorld() != ground.getWorld()) {
                    displays().remove(moon);
                    displays().remove(glow);
                    cancel();
                    return;
                }
                double y = 9 - 9 * Math.pow(Math.min(1, ticks / 20.0), 1.6);
                // the crescent's arc hangs 0.94 x size below the display, so lift the display by that much
                Location arc = ground.clone().add(0, y + 0.6, 0);
                Location pos = arc.clone().add(0, 0.94 * size, 0);
                pos.setYaw(0);
                pos.setPitch(0);
                if (ticks % 2 == 0) {
                    moon.teleport(pos);
                    glow.teleport(arc.clone().add(0, size * 0.25, 0));
                }
                Fx.particle(arc, Particle.END_ROD, 4, 1.5, 0.02);
                Fx.particle(arc, Particle.DUST, 6, 1.8, 0, Fx.dust(MIDNIGHT, 2f));
                if (ticks >= 20) {
                    shatter(player, ground, face);
                    fx().fade(moon, Fx3D.Shape.SLASH, MOON_GLOW, 1, 1);
                    displays().remove(glow);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void shatter(Player player, Location ground, Vector facing) {
        Location center = ground.clone().add(0, 0.5, 0);
        fx().shockwave(ground, SILVER, 6f, 8);
        fx().shockwave(ground, MIDNIGHT, 4f, 6);
        fx().wave(center.clone().add(0, 1, 0), facing, MOON, 4f, 6);
        fx().burst(center.clone().add(0, 1, 0), SILVER, 5f);
        fx().flash(center.clone().add(0, 1, 0), MOON, 4f);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            displays().eruptGround(ground.clone().add(Math.cos(a) * 2.5, 0.3, Math.sin(a) * 2.5), 0.6f, 1.0f);
        }
        Fx.particle(center, Particle.END_ROD, 60, 1.5, 0.15);
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        Fx.sound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        Fx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.6f);
        for (LivingEntity target : combat().nearby(player, center, 5.5)) {
            double d = Combat.distanceToBody(target, center);
            combat().damage(player, target, Math.max(6, 13 * (1 - d / 6.5)), true);
            Combat.knockback(target, center, 1.2, 0.8);
            applyEffect(target, PotionEffectType.BLINDNESS, 30, 0);
        }
    }
}
