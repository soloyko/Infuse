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
 * Watching Warglaive, the Lunar Warglaive: the moon and the dark.
 * Passive Moonlit, on-hit Crescent Echo, F Lunar Dash, Sneak+F Moongate.
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
                if (ticks % 2 == 1) {
                    fx().afterimage(at.clone().subtract(heading.clone().multiply(0.8)), plugin.items().create(type), heading,
                            MOON, 1.4f, 5);
                }
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

    /** Moongate: how long the gates stay open and how close something must come to pass through. */
    private static final int GATE_TICKS = 160;
    private static final double GATE_REACH = 1.15;

    /**
     * Moongate: two rings of moonlight open, one just in front of you and one where you aim, for
     * eight seconds. Anything that goes into one comes out of the other, still moving the same
     * way: you, your enemies, arrows. Enemies are moonburned on the way through.
     */
    @Override
    public boolean secondary(Player player) {
        Vector look = dev.infuse.customs.ability.Aim.flatLook(player);
        Location near = player.getLocation().add(look.clone().multiply(1.8)).add(0, 1.1, 0);
        Location aim = combat().aimPoint(player, 28);
        if (aim == null || aim.distanceSquared(player.getLocation()) < 36) {
            aim = player.getLocation().add(look.clone().multiply(14));
        }
        Location far = Combat.ground(aim.clone().subtract(look.clone().multiply(1.2)).add(0, 1, 0)).add(0, 1.1, 0);
        // gate -> where things come out of it and which way they face
        Location[] gates = {near, far};
        Vector[] exits = {look.clone().multiply(-1), look.clone()};
        List<ItemDisplay> parts = new java.util.ArrayList<>();
        for (int g = 0; g < 2; g++) {
            Location c = gates[g].clone();
            c.setYaw(0);
            c.setPitch(0);
            Quaternionf stand = new Quaternionf().rotationTo(0, 1, 0, (float) look.getX(), 0, (float) look.getZ());
            ItemDisplay ring = fx().spawn(c, Fx3D.Shape.RING, g == 0 ? MOON : SILVER,
                    new Transformation(new Vector3f(), stand, new Vector3f(0.2f), new Quaternionf()), Display.Billboard.FIXED, GATE_TICKS + 12);
            ItemDisplay inner = fx().spawn(c, Fx3D.Shape.GLOW, MIDNIGHT,
                    new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.2f), new Quaternionf()), Display.Billboard.CENTER, GATE_TICKS + 12);
            displays().later(1, () -> {
                dev.infuse.customs.ability.Displays.animate(ring, new Transformation(new Vector3f(), stand, new Vector3f(1.35f), new Quaternionf()), 8);
                dev.infuse.customs.ability.Displays.animate(inner, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.2f), new Quaternionf()), 8);
            });
            parts.add(ring);
            parts.add(inner);
            fx().flash(c, SILVER, 2.4f);
            Fx.sound(c, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.8f);
        }
        Fx.sound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        java.util.Map<UUID, Integer> passed = new java.util.HashMap<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > GATE_TICKS || far.getWorld() != near.getWorld()) {
                    close();
                    return;
                }
                for (int g = 0; g < 2; g++) {
                    Location c = gates[g];
                    if (ticks % 2 == 0) {
                        double a = ticks * 0.2 + g;
                        Vector side = new Vector(-look.getZ(), 0, look.getX());
                        Location p = c.clone().add(side.clone().multiply(Math.cos(a) * 1.3)).add(0, Math.sin(a) * 1.3, 0);
                        Fx.particle(p, Particle.END_ROD, 1, 0.02, 0.01);
                        Fx.particle(c, Particle.REVERSE_PORTAL, 2, 0.3, 0.02);
                    }
                    for (org.bukkit.entity.Entity e : c.getWorld().getNearbyEntities(c, 1.4, 1.8, 1.4)) {
                        if (!(e instanceof LivingEntity || e instanceof org.bukkit.entity.Projectile || e instanceof org.bukkit.entity.Item)) {
                            continue;
                        }
                        Location body = e instanceof LivingEntity living ? Combat.center(living) : e.getLocation();
                        if (body.distance(c) > GATE_REACH + e.getWidth() / 2) {
                            continue;
                        }
                        Integer last = passed.get(e.getUniqueId());
                        if (last != null && ticks - last < 20) {
                            continue;
                        }
                        passed.put(e.getUniqueId(), ticks);
                        travel(player, e, gates[1 - g], exits[1 - g]);
                    }
                }
                if (ticks % 30 == 0) {
                    Fx.sound(near, Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 1.6f);
                    Fx.sound(far, Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 1.6f);
                }
            }

            private void close() {
                cancel();
                for (ItemDisplay part : parts) {
                    dev.infuse.customs.ability.Displays.animate(part, new Transformation(new Vector3f(), part.getTransformation().getLeftRotation(),
                            new Vector3f(0.05f), new Quaternionf()), 6);
                    displays().later(7, () -> displays().remove(part));
                }
                for (Location c : gates) {
                    Fx.particle(c, Particle.END_ROD, 16, 0.5, 0.05);
                    Fx.sound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Something steps through a gate and out of the other one, still moving the same way. */
    private void travel(Player caster, org.bukkit.entity.Entity e, Location gate, Vector exit) {
        Vector velocity = e.getVelocity();
        double height = e instanceof LivingEntity living ? living.getHeight() / 2 : 0;
        Location out = gate.clone().add(exit.clone().multiply(2.1)).subtract(0, height, 0);
        out.setYaw(e.getLocation().getYaw());
        out.setPitch(e.getLocation().getPitch());
        Location from = e.getLocation().add(0, height, 0);
        fx().flash(from, MOON, 1.4f);
        e.teleport(out, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        // keep their momentum, turned to come out of the gate's face
        double speed = velocity.length();
        Combat.setVelocity(e, exit.clone().multiply(Math.max(0.3, speed)).setY(velocity.getY()));
        fx().flash(out.clone().add(0, height, 0), SILVER, 1.6f);
        Fx.sound(out, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        if (e == caster) {
            applyEffect(caster, PotionEffectType.SPEED, 40, 1);
            status().protectFromFall(caster, 40);
        } else if (e instanceof LivingEntity target && combat().canHit(caster, target)) {
            combat().damage(caster, target, 3, true);
            applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
            Fx.particle(Combat.center(target), Particle.DUST, 10, 0.3, 0, Fx.dust(SILVER, 1.2f));
        }
    }

}
