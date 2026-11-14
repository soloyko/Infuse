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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Frost Sword: crowd control and defence.
 * Passive Frostguard (freeze immunity in CombatListener), on-hit Frostbite, RC Cold Snap, Sneak+RC Blizzard.
 */
public final class FrostSword extends SwordAbility {

    private static final int ICE = 0x93D7EF;
    private static final int WHITE = 0xF4FBFF;

    public FrostSword(CustomsPlugin plugin) {
        super(plugin, SwordType.FROST);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ARMOR, "armor", 4, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(meta, Attribute.ARMOR_TOUGHNESS, "toughness", 2, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void tickHeld(Player player, long tick) {
        if (player.getFreezeTicks() > 0) {
            player.setFreezeTicks(0);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        applyEffect(target, PotionEffectType.SLOWNESS, 60, 1);
        freeze(target, 70);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SNOWFLAKE, 15, 0.3, 0.03);
        Fx.sound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.6f, 1.2f);
    }

    /** Adds freeze ticks (the frosty screen overlay; fully frozen entities take freeze damage). */
    private static void freeze(LivingEntity target, int ticks) {
        int max = target.getMaxFreezeTicks() + 100;
        target.setFreezeTicks(Math.min(max, target.getFreezeTicks() + ticks));
    }

    private static final int SNAP_TICKS = 30;
    private static final double SNAP_RADIUS = 3.2;

    /**
     * Cold Snap: a ring of frost forms around the enemy you aim at and closes in over a second and
     * a half. If it is still inside when the ring closes, it freezes solid for two seconds and
     * the ice shatters around it; step out in time and it only chills you.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 24, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to bring the cold down on.");
            return false;
        }
        Location center = Combat.ground(target.getLocation().add(0, 0.5, 0)).add(0, 0.08, 0);
        center.setYaw(0);
        center.setPitch(0);
        org.bukkit.entity.ItemDisplay ring = fx().sigil(center, new Vector(0, 1, 0), ICE, (float) SNAP_RADIUS * 1.25f, SNAP_TICKS + 6);
        ring.setInterpolationDelay(0);
        Fx.sound(center, Sound.BLOCK_POWDER_SNOW_STEP, 1.5f, 0.6f);
        Fx.sound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
        fx().beam(dev.infuse.customs.ability.Fx3D.handOrigin(player), Combat.center(target), 0x9FE6FF, 0.18f, 6);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                double radius = SNAP_RADIUS * (1 - (double) t / SNAP_TICKS);
                // the ring of frost closing in, frost creeping over the ground behind it
                if (ring.isValid() && t % 2 == 0) {
                    ring.setInterpolationDelay(0);
                    ring.setInterpolationDuration(2);
                    ring.setTransformation(new org.bukkit.util.Transformation(new Vector3f(), new Quaternionf().rotateY(t * 0.08f),
                            new Vector3f((float) Math.max(0.3, radius * 1.25)), new Quaternionf()));
                }
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5 + t * 0.1;
                    Fx.particle(center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), Particle.SNOWFLAKE, 1, 0.02, 0);
                }
                if (t % 6 == 0) {
                    Fx.sound(center, Sound.BLOCK_GLASS_STEP, 0.8f, 1.6f + t * 0.01f);
                }
                if (t < SNAP_TICKS) {
                    return;
                }
                cancel();
                displays().remove(ring);
                fx().burst(center.clone().add(0, 1, 0), 0xBDF3FF, 2.4f);
                Fx.sound(center, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
                if (!target.isValid() || target.isDead() || !alive(player)) {
                    return;
                }
                double dx = target.getLocation().getX() - center.getX();
                double dz = target.getLocation().getZ() - center.getZ();
                if (dx * dx + dz * dz > 1.3 * 1.3) {
                    freeze(target, 60);
                    applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
                    return; // it got out: only a chill
                }
                // caught: frozen solid, then shattered
                freeze(target, 200);
                hold(target, 40);
                combat().damage(player, target, 5, true);
                Location base = target.getLocation();
                base.setYaw(0);
                base.setPitch(0);
                float w = (float) Math.max(1.0, target.getWidth() + 0.5);
                float h = (float) target.getHeight() + 0.3f;
                BlockDisplay ice = displays().block(base.clone().add(0, h / 2, 0), Material.PACKED_ICE.createBlockData(),
                        new Vector3f(w, h, w), new Quaternionf(), 42);
                ice.setBrightness(new org.bukkit.entity.Display.Brightness(13, 15));
                Fx.sound(base, Sound.BLOCK_GLASS_PLACE, 1.5f, 0.6f);
                displays().later(40, () -> {
                    Location at = base.clone().add(0, 1, 0);
                    Fx.particle(at, Particle.BLOCK, 40, 0.6, 0.15, Material.PACKED_ICE.createBlockData());
                    Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.4f, 1f);
                    fx().burst(at, 0xBDF3FF, 2.2f);
                    if (!alive(player)) {
                        return;
                    }
                    for (LivingEntity e : combat().nearby(player, base, 2.8)) {
                        combat().damage(player, e, e == target ? 4 : 3, true);
                        freeze(e, 60);
                    }
                });
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Blizzard: 6 seconds of storm centred on the caster. */
    @Override
    public boolean secondary(Player player) {
        double radius = 7;
        Fx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.6f);
        Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
        fx().shockwave(player.getLocation(), 0xBDF3FF, 7f, 10);
        // a spiral of ice spikes bursts out of the ground around you
        Location origin = player.getLocation();
        java.util.Set<java.util.UUID> speared = new java.util.HashSet<>();
        for (int i = 0; i < 16; i++) {
            double a = i * 0.85;
            double d = 1.6 + i * 0.32;
            Location spot = Combat.ground(origin.clone().add(Math.cos(a) * d, 1.5, Math.sin(a) * d));
            int delay = i;
            boolean blue = i % 3 == 0;
            displays().later(1 + delay, () -> {
                fx().spike(spot, (blue ? Material.BLUE_ICE : Material.PACKED_ICE).createBlockData(),
                        (float) (1.6 + Math.min(1.6, delay * 0.12)), 0.6f, 0.25, 34);
                Fx.particle(spot.clone().add(0, 0.4, 0), Particle.BLOCK, 10, 0.3, 0.1, Material.PACKED_ICE.createBlockData());
                Fx.sound(spot, Sound.BLOCK_GLASS_PLACE, 0.6f, 1.6f);
                for (LivingEntity target : combat().nearby(player, spot.clone().add(0, 1, 0), 1.4)) {
                    if (speared.add(target.getUniqueId())) {
                        combat().damage(player, target, 4);
                        freeze(target, 120);
                        Combat.setVelocity(target, target.getVelocity().setY(0.55));
                    }
                }
            });
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || ticks++ >= 120) {
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                if (ticks % 2 == 0) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < 30; i++) {
                        double a = r.nextDouble(Math.PI * 2);
                        double d = Math.sqrt(r.nextDouble()) * radius;
                        Location p = center.clone().add(Math.cos(a) * d, r.nextDouble(0.2, 4), Math.sin(a) * d);
                        Fx.directional(p, Particle.SNOWFLAKE, new Vector(Math.sin(a), -1.2, -Math.cos(a)), 0.25);
                    }
                    double spin = ticks * 0.25;
                    for (int arm = 0; arm < 3; arm++) {
                        double a = spin + arm * Math.PI * 2 / 3;
                        double d = (ticks % 30) / 30.0 * radius;
                        Fx.particle(center.clone().add(Math.cos(a) * d, 0.5 + (ticks % 30) / 15.0, Math.sin(a) * d),
                                Particle.DUST, 2, 0.1, 0, Fx.dust(WHITE, 1.6f));
                    }
                    Fx.particle(center.clone().add(0, 2.5, 0), Particle.CLOUD, 4, radius / 2, 0.01);
                }
                if (ticks % 20 == 0) {
                    Fx.sound(center, Sound.BLOCK_POWDER_SNOW_STEP, 1.2f, 0.6f);
                    for (LivingEntity target : combat().nearby(player, center, radius)) {
                        combat().damage(player, target, 3);
                        applyEffect(target, PotionEffectType.SLOWNESS, 40, 2);
                        applyEffect(target, PotionEffectType.MINING_FATIGUE, 40, 1);
                        freeze(target, 60);
                        Fx.particle(Combat.center(target), Particle.SNOWFLAKE, 10, 0.3, 0.02);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
