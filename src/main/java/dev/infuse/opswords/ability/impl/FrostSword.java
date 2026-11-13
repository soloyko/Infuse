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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Frost Sword: crowd control and defence.
 * Passive Frostguard (freeze immunity in CombatListener), on-hit Frostbite, RC Glacial Spike, Sneak+RC Blizzard.
 */
public final class FrostSword extends SwordAbility {

    private static final int ICE = 0x93D7EF;
    private static final int WHITE = 0xF4FBFF;

    public FrostSword(OPSwordsPlugin plugin) {
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

    /** Glacial Spike: a fast ice projectile that impales the first enemy it hits. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getEyeLocation().subtract(0, 0.2, 0);
        Vector dir = start.getDirection();
        Fx.sound(start, Sound.ITEM_TRIDENT_THROW, 1f, 1.6f);
        Fx.sound(start, Sound.BLOCK_GLASS_PLACE, 1f, 1.4f);

        // the spike itself: a sliver of ice pointing where it flies
        Quaternionf aim = Displays.pointing(dir);
        BlockDisplay shard = displays().block(start, Material.PACKED_ICE.createBlockData(),
                new Vector3f(0.18f, 1.1f, 0.18f), aim, 30);

        new BukkitRunnable() {
            final Location pos = start.clone();
            int steps;

            @Override
            public void run() {
                if (!alive(player) || pos.getWorld() != player.getWorld() || steps++ >= 20) {
                    displays().remove(shard);
                    cancel();
                    return;
                }
                RayTraceResult hit = pos.getWorld().rayTrace(pos, dir, 1.5, FluidCollisionMode.NEVER, true, 0.4,
                        e -> combat().canHit(player, e));
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                    displays().remove(shard);
                    impale(player, target);
                    cancel();
                    return;
                }
                if (hit != null && hit.getHitBlock() != null) {
                    Location at = hit.getHitPosition().toLocation(pos.getWorld());
                    Fx.particle(at, Particle.BLOCK, 25, 0.2, 0.1, Material.ICE.createBlockData());
                    Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
                    displays().remove(shard);
                    cancel();
                    return;
                }
                pos.add(dir.clone().multiply(1.5));
                shard.teleport(pos);
                Fx.particle(pos, Particle.SNOWFLAKE, 4, 0.08, 0.01);
                Fx.particle(pos, Particle.DUST, 3, 0.1, 0, Fx.dust(ICE, 1.4f));
                Fx.particle(pos, Particle.ITEM_SNOWBALL, 1, 0.05, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void impale(Player player, LivingEntity target) {
        Location at = Combat.center(target);
        encase(target);
        combat().damage(player, target, 8);
        applyEffect(target, PotionEffectType.SLOWNESS, 60, 3);
        freeze(target, 200);
        Fx.particle(at, Particle.BLOCK, 50, 0.4, 0.1, Material.PACKED_ICE.createBlockData());
        Fx.particle(at, Particle.SNOWFLAKE, 30, 0.5, 0.05);
        Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
        fx().burst(at, 0xBDF3FF, 2.0f);
        fx().shockwave(target.getLocation(), 0x8FD8FF, 2.6f, 6);
        Fx.sound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
        for (LivingEntity nearby : combat().nearby(player, at, 2.5)) {
            if (nearby != target) {
                combat().damage(player, nearby, 3);
                applyEffect(nearby, PotionEffectType.SLOWNESS, 40, 1);
                freeze(nearby, 80);
            }
        }
    }

    /** Briefly locks the target inside a block of ice that follows it. */
    private void encase(LivingEntity target) {
        Vector3f size = new Vector3f((float) target.getWidth() + 0.35f, (float) target.getHeight() + 0.3f,
                (float) target.getWidth() + 0.35f);
        BlockDisplay ice = displays().block(Combat.center(target), Material.ICE.createBlockData(),
                new Vector3f(0.05f), new Quaternionf(), 40);
        displays().later(1, () -> Displays.animateBlock(ice, size, new Quaternionf(), new Vector3f(), 3));
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!target.isValid() || !ice.isValid() || ticks++ >= 34) {
                    if (ice.isValid()) {
                        Fx.particle(ice.getLocation(), Particle.BLOCK, 30, 0.4, 0.1, Material.ICE.createBlockData());
                        Fx.sound(ice.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
                    }
                    displays().remove(ice);
                    cancel();
                    return;
                }
                ice.teleport(Combat.center(target));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Blizzard: 6 seconds of storm centred on the caster. */
    @Override
    public boolean secondary(Player player) {
        double radius = 7;
        Fx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.6f);
        Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
        fx().shockwave(player.getLocation(), 0xBDF3FF, 7f, 10);
        fx().shockwave(player.getLocation(), 0x6FC8FF, 5f, 14);

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
