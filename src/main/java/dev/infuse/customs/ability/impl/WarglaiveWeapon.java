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

import java.util.List;
import java.util.UUID;

/**
 * Watching Warglaive, the Lunar Warglaive: the moon and the dark.
 * Passive Moonlit, on-hit Crescent Echo, F Moon Snare, Sneak+F Moongate.
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

    private static final int SNARE_TICKS = 400;
    private static final int SNARE_MAX = 2;
    private static final int CAUGHT_TICKS = 40;

    /** A set Moon Snare: where it lies and its glowing crescent. */
    private record Snare(Location at, ItemDisplay crescent, ItemDisplay glow, int until) {
    }

    private final java.util.Map<UUID, java.util.Deque<Snare>> snares = new java.util.HashMap<>();

    /**
     * Moon Snare: a crescent of moonlight settles on the ground where you aim and waits for twenty
     * seconds (two at a time). The first enemy to step on it is caught: lifted into the air and
     * turned helplessly in rings of moonlight for two seconds, then dropped.
     */
    @Override
    public boolean primary(Player player) {
        Location aim = combat().aimPoint(player, 24);
        if (aim == null) {
            Vector look = player.getLocation().getDirection().setY(0);
            look = look.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : look.normalize();
            aim = player.getLocation().add(look.multiply(6));
        }
        Location ground = Combat.ground(aim.clone().add(0, 0.5, 0)).add(0, 0.06, 0);
        ground.setYaw(0);
        ground.setPitch(0);
        java.util.Deque<Snare> mine = snares.computeIfAbsent(player.getUniqueId(), k -> new java.util.ArrayDeque<>());
        while (mine.size() >= SNARE_MAX) {
            vanish(mine.pollFirst());
        }
        ItemDisplay crescent = fx().sigil(ground, new Vector(0, 1, 0), SILVER, 1.4f, SNARE_TICKS + 10);
        ItemDisplay glow = fx().orb(ground.clone().add(0, 0.15, 0), MOON_GLOW, 0.9f, SNARE_TICKS + 10);
        Snare snare = new Snare(ground, crescent, glow, org.bukkit.Bukkit.getCurrentTick() + SNARE_TICKS);
        mine.addLast(snare);
        fx().beam(ground.clone().add(0, 10, 0), ground, MOON, 0.4f, 10);
        Fx.particle(ground.clone().add(0, 0.2, 0), Particle.END_ROD, 12, 0.4, 0.02);
        Fx.sound(ground, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
        Fx.sound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.4f);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!mine.contains(snare)) {
                    cancel();
                    return;
                }
                if (snare.until() < org.bukkit.Bukkit.getCurrentTick() || !player.isOnline() || ground.getWorld() != player.getWorld()) {
                    mine.remove(snare);
                    vanish(snare);
                    cancel();
                    return;
                }
                // the crescent turns slowly and breathes
                if (snare.crescent().isValid() && t % 2 == 0) {
                    float pulse = 1.4f + (float) Math.sin(t * 0.1) * 0.08f;
                    snare.crescent().setInterpolationDelay(0);
                    snare.crescent().setInterpolationDuration(2);
                    snare.crescent().setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(t * 0.03f),
                            new Vector3f(pulse), new Quaternionf()));
                }
                if (t % 10 == 0) {
                    Fx.particle(ground.clone().add(0, 0.2, 0), Particle.DUST, 2, 0.5, 0, Fx.dust(SILVER, 0.8f));
                }
                if (t < 10 || t % 2 != 0) {
                    return; // a moment to settle before it can spring
                }
                for (LivingEntity victim : combat().nearby(player, ground, 1.3)) {
                    mine.remove(snare);
                    vanish(snare);
                    catchIn(player, victim, ground);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void vanish(Snare snare) {
        displays().remove(snare.crescent());
        displays().remove(snare.glow());
        Fx.particle(snare.at().clone().add(0, 0.2, 0), Particle.END_ROD, 6, 0.3, 0.02);
    }

    /** Sprung: the enemy is lifted and turned in rings of moonlight, then dropped. */
    private void catchIn(Player player, LivingEntity victim, Location ground) {
        Fx.sound(ground, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        Fx.sound(ground, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        fx().flash(ground.clone().add(0, 1, 0), SILVER, 3f);
        fx().beam(ground.clone().add(0, 14, 0), ground, MOON, 0.9f, CAUGHT_TICKS);
        combat().damage(player, victim, 3, true);
        ItemDisplay[] rings = new ItemDisplay[2];
        for (int i = 0; i < 2; i++) {
            rings[i] = fx().sigil(Combat.center(victim), new Vector(1, 0, 0), i == 0 ? SILVER : MOON, 1.3f, CAUGHT_TICKS + 5);
        }
        Location hover = ground.clone().add(0, 2.4, 0);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!victim.isValid() || victim.isDead() || t > CAUGHT_TICKS) {
                    for (ItemDisplay r : rings) {
                        displays().remove(r);
                    }
                    if (victim.isValid() && !victim.isDead()) {
                        Combat.setVelocity(victim, new Vector(0, -1.2, 0));
                        displays().later(6, () -> {
                            if (victim.isValid() && alive(player)) {
                                combat().damage(player, victim, 4, true);
                                applyEffect(victim, PotionEffectType.SLOWNESS, 40, 1);
                                fx().shockwave(victim.getLocation().add(0, 0.05, 0), SILVER, 2.4f, 6);
                                Fx.sound(victim.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL, 1f, 0.8f);
                            }
                        });
                    }
                    cancel();
                    return;
                }
                // drawn up to the hover point and held there
                Vector to = hover.toVector().subtract(victim.getLocation().toVector());
                Combat.setVelocity(victim, to.multiply(0.35));
                Location c = Combat.center(victim);
                for (int i = 0; i < 2; i++) {
                    if (!rings[i].isValid()) {
                        continue;
                    }
                    double a = t * 0.25 + i * Math.PI / 2;
                    Location spot = c.clone();
                    spot.setDirection(new Vector(Math.cos(a), 0, Math.sin(a)));
                    rings[i].teleport(spot);
                }
                if (t % 4 == 0) {
                    Fx.particle(c, Particle.END_ROD, 3, 0.4, 0.02);
                }
                if (t % 10 == 0) {
                    combat().damage(player, victim, 1.5, true);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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


    @Override
    public void shutdown() {
        for (java.util.Deque<Snare> mine : snares.values()) {
            mine.forEach(this::vanish);
        }
        snares.clear();
    }
}
