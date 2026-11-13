package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Amethyst Shuriken, the Amethyst Shuriken: speed and many small cuts.
 * Passive Light Feet, on-hit Petal Cut, F Star Fan, Sneak+F Thousand Cuts.
 */
public final class ShurikenWeapon extends SwordAbility {

    private static final int MAGENTA = 0xE040FB;
    private static final int PETAL = 0xFF9AF5;
    private static final int DEEP = 0x7B1FA2;
    private static final float STAR_SCALE = 1.5f;

    public ShurikenWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.SHURIKEN);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.SPEED, 0, 0));
        out.add(effect(PotionEffectType.JUMP_BOOST, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        for (LivingEntity other : combat().nearby(player, target.getLocation(), 8)) {
            if (other != target && player.hasLineOfSight(other)) {
                throwStar(player, Combat.center(target), Combat.center(other).toVector()
                        .subtract(Combat.center(target).toVector()).normalize(), 4, new HashSet<>(Set.of(target.getUniqueId())), false);
                return;
            }
        }
    }

    /** Spin around the star's own axis while it lies flat along its flight. */
    private static Quaternionf spin(Vector dir, float angle) {
        Vector n = dir.clone().normalize();
        // lay the star flat (its face up), then spin it around the vertical
        return new Quaternionf().rotateY((float) Math.atan2(-n.getX(), n.getZ())).rotateX((float) (Math.PI / 2)).rotateZ(angle);
    }

    /**
     * A spinning shuriken flying from {@code from} along {@code dir}; it cuts the first enemy it
     * meets and, if {@code ricochet}, bounces on to the nearest other enemy once.
     */
    private void throwStar(Player player, Location from, Vector dir, double damage, Set<UUID> hit, boolean ricochet) {
        Location start = from.clone();
        start.setYaw(0);
        start.setPitch(0);
        ItemDisplay star = displays().item(start, plugin.items().create(type), STAR_SCALE, spin(dir, 0),
                ItemDisplay.ItemDisplayTransform.NONE, 40);
        star.setGlowing(true);
        star.setGlowColorOverride(org.bukkit.Color.fromRGB(MAGENTA));
        ItemDisplay glow = fx().orb(start, MAGENTA, 1.3f, 40);

        new BukkitRunnable() {
            final Location pos = start.clone();
            Vector heading = dir.clone().normalize();
            boolean bounced = !ricochet;
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !star.isValid() || ticks > 30 || pos.getWorld() != player.getWorld()) {
                    finish();
                    return;
                }
                if (pos.getWorld().rayTraceBlocks(pos, heading, 1.3, FluidCollisionMode.NEVER, true) != null) {
                    Fx.sound(pos, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
                    fx().burst(pos, PETAL, 1.0f);
                    finish();
                    return;
                }
                pos.add(heading.clone().multiply(1.3));
                star.teleport(pos);
                glow.teleport(pos);
                star.setInterpolationDelay(0);
                star.setInterpolationDuration(1);
                star.setTransformation(new Transformation(new Vector3f(), spin(heading, ticks * 1.1f), new Vector3f(STAR_SCALE),
                        new Quaternionf()));
                Fx.particle(pos, Particle.DUST, 2, 0.08, 0, Fx.dust(PETAL, 1.2f));
                // a fading streak of light behind each star
                if (ticks % 2 == 0) {
                    fx().flash(pos.clone().subtract(heading.clone().multiply(0.6)), ticks % 4 == 0 ? PETAL : MAGENTA, 0.7f);
                }
                if (ticks % 3 == 0) {
                    Fx.particle(pos, Particle.CHERRY_LEAVES, 1, 0.1, 0);
                }
                for (LivingEntity target : combat().nearby(player, pos, 1.1)) {
                    if (!hit.add(target.getUniqueId())) {
                        continue;
                    }
                    combat().damage(player, target, damage);
                    Location at = Combat.center(target);
                    fx().burst(at, MAGENTA, 1.4f);
                    fx().cut(at, heading, Math.random() * Math.PI * 2, 0.5, PETAL, 1.0f, 2);
                    Fx.particle(at, Particle.CHERRY_LEAVES, 8, 0.3, 0.02);
                    Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.8f);
                    Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.4f);
                    LivingEntity next = bounced ? null : nearestOther(player, target, hit);
                    if (next == null) {
                        finish();
                        return;
                    }
                    bounced = true;
                    heading = Combat.center(next).toVector().subtract(pos.toVector()).normalize();
                    ticks = Math.min(ticks, 18);
                    return;
                }
            }

            private void finish() {
                displays().remove(star);
                displays().remove(glow);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private LivingEntity nearestOther(Player player, LivingEntity from, Set<UUID> hit) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity other : combat().nearby(player, from.getLocation(), 7)) {
            double d = other.getLocation().distanceSquared(from.getLocation());
            if (!hit.contains(other.getUniqueId()) && d < bestDistance) {
                best = other;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Star Fan: five spinning shuriken in a fan, each ricocheting once. */
    @Override
    public boolean primary(Player player) {
        Location eye0 = player.getEyeLocation().subtract(0, 0.25, 0);
        Vector dir = eye0.getDirection();
        Location eye = eye0.clone().add(dir.clone().multiply(1.0)); // start ahead so nothing covers your view
        for (int i = -2; i <= 2; i++) {
            Vector shot = dir.clone().rotateAroundY(Math.toRadians(i * 9));
            int delay = Math.abs(i);
            displays().later(1 + delay, () -> throwStar(player, eye, shot, 5, new HashSet<>(), true));
        }
        // the throwing arc: a wide flat sweep of petals off your hand
        Vector flat = dir.clone().setY(0);
        if (flat.lengthSquared() > 1.0E-4) {
            fx().slash(eye0.clone().add(flat.clone().normalize().multiply(0.9)), flat.normalize(), 0.0, MAGENTA, 2.6f, 2);
        }
        Fx.particle(eye, Particle.CHERRY_LEAVES, 10, 0.6, 0.02);
        Fx.sound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.7f);
        Fx.sound(eye, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.8f);
        return true;
    }

    /** Thousand Cuts: flash between up to six enemies, cutting each, then return. */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 10)) {
            if (player.hasLineOfSight(target)) {
                targets.add(target);
            }
            if (targets.size() >= 6) {
                break;
            }
        }
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        Location home = player.getLocation();
        status().protectFromFall(player, 60);
        Fx.sound(home, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.4f);
        fx().burst(home.clone().add(0, 1, 0), MAGENTA, 2f);
        applyEffect(player, PotionEffectType.INVISIBILITY, 4 * targets.size() + 6, 0);

        new BukkitRunnable() {
            int index;
            Location last = home.clone().add(0, 1, 0);

            @Override
            public void run() {
                if (!alive(player)) {
                    cancel();
                    return;
                }
                if (index >= targets.size()) {
                    Location back = home.clone();
                    player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    fx().beam(last, back.clone().add(0, 1, 0), PETAL, 0.4f, 6);
                    fx().burst(back.clone().add(0, 1, 0), MAGENTA, 2.2f);
                    Fx.sound(back, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.8f);
                    cancel();
                    return;
                }
                LivingEntity target = targets.get(index++);
                if (!target.isValid() || target.getWorld() != player.getWorld()) {
                    return;
                }
                Vector away = target.getLocation().toVector().subtract(last.toVector()).setY(0);
                if (away.lengthSquared() < 1.0E-4) {
                    away = new Vector(1, 0, 0);
                }
                Location behind = target.getLocation().add(away.normalize().multiply(1.2));
                Location safe = Combat.safeSpot(behind);
                Location to = safe != null ? safe : target.getLocation();
                to.setDirection(Combat.center(target).toVector().subtract(to.clone().add(0, 1.6, 0).toVector()));
                Location at = Combat.center(target);
                fx().beam(last, at, MAGENTA, 0.35f, 5);
                player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
                combat().damage(player, target, 6);
                Vector cut = at.toVector().subtract(last.toVector());
                fx().cut(at, cut, 0.7, 0.5, MAGENTA, 1.6f, 2);
                fx().cut(at, cut, -0.7 + Math.PI, 0.5, PETAL, 1.6f, 2);
                Fx.particle(at, Particle.CHERRY_LEAVES, 12, 0.4, 0.02);
                Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
                Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
                last = at;
            }
        }.runTaskTimer(plugin, 2L, 4L);
        return true;
    }
}
