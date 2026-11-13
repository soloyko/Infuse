package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ripsaw, the Cyber Sawblade: heat, sparks and noise.
 * Passive Overclock, on-hit Arc Spark, F Grinder, Sneak+F EMP Overload.
 */
public final class SawbladeWeapon extends SwordAbility {

    private static final int ORANGE = 0xFF7A1A;
    private static final int CYAN = 0x29E0FF;
    private static final int HOT = 0xFFD27A;
    private static final int MAX_HEAT = 5;
    private static final long HEAT_WINDOW = 2500;

    private record Heat(int stacks, long last) {
    }

    private final Map<UUID, Heat> heat = new HashMap<>();

    public SawbladeWeapon(OPSwordsPlugin plugin) {
        super(plugin, SwordType.SAWBLADE);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        Heat h = heat.get(player.getUniqueId());
        int stacks = h == null || now - h.last() > HEAT_WINDOW ? 0 : h.stacks();
        Location at = Combat.center(target);
        if (stacks >= MAX_HEAT) {
            // vent: the stored heat goes into this hit
            event.setDamage(event.getDamage() + 6);
            heat.remove(player.getUniqueId());
            fx().burst(at, HOT, 2.2f);
            fx().flash(at, ORANGE, 1.8f);
            Fx.particle(at, Particle.LAVA, 6, 0.3, 0);
            Fx.particle(at, Particle.ELECTRIC_SPARK, 30, 0.4, 0.3);
            Fx.sound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.2f);
            Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
        } else {
            heat.put(player.getUniqueId(), new Heat(stacks + 1, now));
            Fx.particle(at, Particle.ELECTRIC_SPARK, 6 + stacks * 4, 0.3, 0.2);
            if (stacks + 1 == MAX_HEAT) {
                tell(player, net.kyori.adventure.text.Component.text("⚙ Overheated: next hit vents!",
                        net.kyori.adventure.text.format.TextColor.color(ORANGE)));
                Fx.sound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2f);
            }
        }
        if (!chance(0.25)) {
            return;
        }
        int arcs = 0;
        for (LivingEntity other : combat().nearby(player, target.getLocation(), 6)) {
            if (other == target || arcs >= 2) {
                continue;
            }
            arcs++;
            Location to = Combat.center(other);
            fx().beam(at, to, CYAN, 0.35f, 4);
            fx().burst(to, CYAN, 1.1f);
            combat().damage(player, other, 3);
            Fx.sound(to, Sound.ENTITY_BEE_STING, 0.6f, 2f);
        }
    }

    /** Grinder: two seconds of a spinning blade in front of you. */
    @Override
    public boolean primary(Player player) {
        Location eye = player.getEyeLocation();
        Location spawnAt = eye.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        ItemDisplay blade = displays().item(spawnAt, plugin.items().create(type), 2.2f, new Quaternionf(),
                ItemDisplay.ItemDisplayTransform.NONE, 50);
        ItemDisplay heat = fx().orb(spawnAt, ORANGE, 1.6f, 50);
        blade.setGlowing(true);
        blade.setGlowColorOverride(Color.fromRGB(ORANGE));
        blade.setTeleportDuration(1);
        Fx.sound(eye, Sound.ENTITY_MINECART_RIDING, 1.5f, 1.8f);
        Fx.sound(eye, Sound.BLOCK_GRINDSTONE_USE, 1.2f, 0.8f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 40 || plugin.items().heldType(player) != type) {
                    displays().remove(blade);
                    displays().remove(heat);
                    cancel();
                    return;
                }
                Location e = player.getEyeLocation();
                Vector look = e.getDirection().normalize();
                Location front = e.clone().add(look.clone().multiply(2.0)).add(Fx.right(look).multiply(0.45)).subtract(0, 0.35, 0);
                front.setYaw(0);
                front.setPitch(0);
                blade.teleport(front);
                // the blade points forward and spins around its own length like a saw
                Quaternionf rot = dev.infuse.opswords.ability.Displays.pointing(look).rotateY(ticks * 1.3f);
                blade.setInterpolationDelay(0);
                blade.setInterpolationDuration(1);
                blade.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(2.2f), new Quaternionf()));
                Location tip = e.clone().add(look.clone().multiply(3.0)).subtract(0, 0.35, 0);
                Location hot = tip.clone();
                hot.setYaw(0);
                hot.setPitch(0);
                heat.teleport(hot);
                Fx.particle(tip, Particle.ELECTRIC_SPARK, 6, 0.25, 0.35);
                Fx.particle(tip, Particle.DUST, 2, 0.2, 0, Fx.dust(ticks % 2 == 0 ? ORANGE : HOT, 1.2f));
                if (ticks % 4 == 0) {
                    fx().slash(tip, look, ticks * 0.9, ticks % 8 == 0 ? ORANGE : CYAN, 1.2f, 2);
                    Fx.sound(tip, Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.4f + (ticks % 8) * 0.05f);
                    for (LivingEntity target : combat().nearby(player, tip, 2.2)) {
                        combat().damage(player, target, 2.2, true);
                        Combat.setVelocity(target, target.getVelocity().multiply(0.3));
                        Fx.particle(Combat.center(target), Particle.CRIT, 8, 0.3, 0.2);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** EMP Overload: a short charge, then a pulse that stuns and reveals enemies. */
    @Override
    public boolean secondary(Player player) {
        Location start = player.getLocation();
        Fx.sound(start, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.8f);
        Fx.sound(start, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.6f);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player)) {
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1, 0);
                if (ticks < 12) {
                    Fx.ring(c, 2.5 - ticks * 0.18, 12, p -> Fx.particle(p, Particle.ELECTRIC_SPARK, 1, 0, 0));
                    if (ticks % 4 == 0) {
                        fx().flash(c, CYAN, 1.2f + ticks * 0.08f);
                    }
                    return;
                }
                pulse(player);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void pulse(Player player) {
        Location feet = player.getLocation();
        Location center = feet.clone().add(0, 1, 0);
        fx().shockwave(feet, CYAN, 8.5f, 8);
        fx().shockwave(feet, ORANGE, 5f, 6);
        fx().wave(center, new Vector(0, 1, 0.0001), CYAN, 4f, 6);
        fx().burst(center, CYAN, 3.5f);
        fx().flash(center, 0xBFF6FF, 3f);
        Fx.particle(center, Particle.ELECTRIC_SPARK, 80, 2.5, 0.4);
        Fx.sound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.8f);
        Fx.sound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.4f);
        applyEffect(player, PotionEffectType.SPEED, 120, 1);
        applyEffect(player, PotionEffectType.HASTE, 120, 1);
        for (LivingEntity target : combat().nearby(player, center, 8.5)) {
            combat().damage(player, target, 6, true);
            hold(target, 40);
            applyEffect(target, PotionEffectType.GLOWING, 100, 0);
            applyEffect(target, PotionEffectType.WEAKNESS, 80, 0);
            Location at = Combat.center(target);
            fx().beam(center, at, CYAN, 0.3f, 4);
            Fx.particle(at, Particle.ELECTRIC_SPARK, 20, 0.4, 0.2);
        }
    }

    @Override
    public void shutdown() {
        heat.clear();
    }
}
