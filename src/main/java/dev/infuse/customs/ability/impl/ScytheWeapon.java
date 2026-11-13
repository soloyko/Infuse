package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.AbilitySlot;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Requiem of the Ninth Abyss, the Reaper's Scythe: finishes off whatever is already dying.
 * Passive Death's Embrace, on-hit Rend, F Reap, Sneak+F Final Harvest.
 */
public final class ScytheWeapon extends SwordAbility implements Listener {

    private static final int TEAL = 0x2BD9A8;
    private static final int ABYSS = 0x0E3B33;

    public ScytheWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.SCYTHE);
    }

    private static double healthFraction(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return entity.getHealth() / (max == null ? 20 : max.getValue());
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || plugin.items().heldType(killer) != type || !plugin.canUse(killer, type)) {
            return;
        }
        plugin.cooldowns().clear(killer.getUniqueId(), type, AbilitySlot.PRIMARY);
        applyEffect(killer, PotionEffectType.SPEED, 60, 1);
        Fx.line(Combat.center(event.getEntity()), Combat.center(killer), 0.4,
                p -> Fx.particle(p, Particle.SOUL_FIRE_FLAME, 1, 0.03, 0.01));
        tell(killer, Text.plain("☠ Reap is ready", type.color()));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (healthFraction(target) >= 0.35) {
            return;
        }
        event.setDamage(event.getDamage() * 1.5);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SOUL_FIRE_FLAME, 12, 0.3, 0.04);
        Fx.particle(at, Particle.SCULK_SOUL, 4, 0.3, 0.02);
        Fx.sound(at, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);
    }

    /** Reap: a 220 degree sweep in front of you over 6 ticks. */
    @Override
    public boolean primary(Player player) {
        Location origin = player.getLocation();
        float yaw = origin.getYaw();
        double start = Math.toRadians(yaw - 110);
        double end = Math.toRadians(yaw + 110);
        Fx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        Fx.sound(origin, Sound.ITEM_TRIDENT_THROW, 0.8f, 0.6f);
        Vector look = origin.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        // soul fire bursts up along the arc of the sweep
        for (int i = 0; i <= 8; i++) {
            double a = start + (end - start) * i / 8.0;
            Location spot = Combat.ground(origin.clone().add(-Math.sin(a) * 3.2, 1.5, Math.cos(a) * 3.2));
            displays().later(i * 6 / 8 + 1, () -> fx().flamePillar(spot, 0.8f, 1.4f, 10, true));
        }
        fx().slash(origin.clone().add(0, 1.0, 0), look.clone().rotateAroundY(0.9), 0.15, 0x5FFFD0, 3.0f, 5);
        Vector lookCopy = look.clone();
        displays().later(3, () -> fx().slash(player.getLocation().add(0, 1.0, 0), lookCopy.clone().rotateAroundY(-0.9), 0.15, 0xB8FFF0, 3.0f, 5));
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!alive(player) || tick >= 6) {
                    cancel();
                    return;
                }
                double from = start + (end - start) * tick / 6.0;
                double to = start + (end - start) * (tick + 1) / 6.0;
                tick++;
                Location center = player.getLocation().add(0, 1.0, 0);
                for (double a = from; a <= to; a += 0.08) {
                    // Minecraft yaw 0 faces +Z, so the facing vector is (-sin, cos)
                    double x = -Math.sin(a);
                    double z = Math.cos(a);
                    for (double r = 2.2; r <= 4.5; r += 0.55) {
                        Location p = center.clone().add(x * r, -0.1 + (r - 2.2) * 0.08, z * r);
                        Fx.particle(p, Particle.DUST, 1, 0.03, 0, Fx.dust(r > 4 ? ABYSS : TEAL, 1.3f));
                    }
                    Fx.particle(center.clone().add(x * 4.6, 0, z * 4.6), Particle.SOUL_FIRE_FLAME, 1, 0.05, 0.01);
                }
                double mid = (from + to) / 2;
                Fx.particle(center.clone().add(-Math.sin(mid) * 3.2, 0, Math.cos(mid) * 3.2), Particle.SWEEP_ATTACK, 1, 0, 0);
                for (LivingEntity target : combat().nearby(player, center, 4.8)) {
                    if (hit.contains(target.getUniqueId())) {
                        continue;
                    }
                    Vector rel = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    double angle = Math.atan2(-rel.getX(), rel.getZ());
                    if (angleBetween(angle, from - 0.15, to + 0.15) || rel.lengthSquared() < 1.5) {
                        hit.add(target.getUniqueId());
                        combat().damage(player, target, 8);
                        combat().heal(player, 1.5);
                        Combat.knockback(target, player.getLocation(), 0.5, 0.2);
                        Fx.particle(Combat.center(target), Particle.SOUL, 8, 0.3, 0.03);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private static boolean angleBetween(double angle, double from, double to) {
        double a = (angle - from) % (2 * Math.PI);
        if (a < 0) {
            a += 2 * Math.PI;
        }
        return a <= (to - from);
    }

    /** Final Harvest: a giant scythe rises over the target and swings down. */
    @Override
    public boolean secondary(Player player) {
        LivingEntity target = combat().lookTarget(player, 16, 1.0);
        if (target == null) {
            tell(player, "No target in sight.");
            return false;
        }
        Fx.sound(target.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.5f);
        Fx.sound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.5f);
        float yaw = (float) Math.toRadians(-player.getLocation().getYaw());
        // raised: blade up behind the target; struck: swung forward and down through it
        Quaternionf raised = new Quaternionf().rotateY(yaw).rotateX((float) Math.toRadians(-40));
        Quaternionf struck = new Quaternionf().rotateY(yaw).rotateX((float) Math.toRadians(95));
        ItemDisplay scythe = displays().item(above(target), plugin.items().create(type), 0.01f, raised,
                ItemDisplay.ItemDisplayTransform.NONE, 40);
        scythe.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        scythe.setGlowing(true);
        displays().later(1, () -> Displays.animate(scythe, transform(raised, 3.6f), 10));

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid() || target.isDead()) {
                    displays().remove(scythe);
                    cancel();
                    return;
                }
                if (scythe.isValid()) {
                    scythe.teleport(above(target));
                }
                Location head = target.getLocation().add(0, target.getHeight() + 0.4, 0);
                if (ticks < 20) {
                    Fx.ring(head, 0.8 + Math.sin(ticks * 0.4) * 0.15, 10, p -> Fx.particle(p, Particle.SOUL, 1, 0, 0.005));
                    return;
                }
                if (ticks == 20) {
                    Displays.animate(scythe, transform(struck, 3.6f), 4);
                    Fx.sound(head, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.5f);
                    return;
                }
                if (ticks == 24) {
                    strike(player, target);
                    return;
                }
                if (ticks == 28) {
                    Displays.animate(scythe, transform(struck, 0.01f), 6);
                }
                if (ticks >= 34) {
                    displays().remove(scythe);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static Location above(LivingEntity target) {
        Location at = target.getLocation().add(0, target.getHeight() + 1.6, 0);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    private static Transformation transform(Quaternionf rotation, float scale) {
        return new Transformation(new Vector3f(), rotation, new Vector3f(scale), new Quaternionf());
    }

    private void strike(Player player, LivingEntity target) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double missing = (max == null ? 20 : max.getValue()) - target.getHealth();
        double damage = Math.min(22, 8 + 0.3 * missing);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SOUL, 40, 0.5, 0.08);
        Fx.particle(at, Particle.SCULK_SOUL, 20, 0.5, 0.05);
        Fx.particle(at, Particle.SWEEP_ATTACK, 3, 0.4, 0);
        Fx.sound(at, Sound.ITEM_TRIDENT_HIT, 1.2f, 0.5f);
        Fx.sound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 1.4f);
        fx().impact(at, 0x2BD9A8, 3f);
        fx().crack(target.getLocation(), 0x2BD9A8, 4f, 40);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3;
            fx().drift(at, new Vector(Math.cos(a) * 1.5, 2.6, Math.sin(a) * 1.5), 0x7FFFE0, 0.6f, 18 + i * 3);
        }
        fx().cut(at, player.getLocation().getDirection(), Math.PI / 2, 0.5, 0x7FFFE0, 2.6f, 3);
        combat().damage(player, target, damage, true);
        if (target.isDead()) {
            tell(player, Text.plain("☠ Harvested", type.color()));
        }
    }
}
