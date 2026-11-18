package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Waxweaver, the Waxen Blade: wax, wick and flame.
 * Passive Warm Glow, on-hit Hot Wax, F Wickfire, Sneak+F Melt.
 */
public final class CandleWeapon extends SwordAbility {

    private static final int WAX = 0xFFE3A3;
    private static final int FLAME = 0xFF8C2A;
    private static final int EMBER = 0xFFD27A;
    private static final int FUSE_TICKS = 80;
    private static final double FUSE_SPEED = 0.6;
    private static final int MELT_TICKS = 160;
    private static final BlockData CANDLE = candle();

    private static BlockData candle() {
        BlockData data = Material.WHITE_CANDLE.createBlockData();
        if (data instanceof org.bukkit.block.data.type.Candle c) {
            c.setLit(true);
            c.setCandles(1);
        }
        return data;
    }

    /** A waxed enemy and the candle burning on its head. */
    private record Waxed(UUID caster, BlockDisplay candle, int until) {
    }

    private final Map<LivingEntity, Waxed> waxed = new HashMap<>();

    public CandleWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.CANDLE);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.NIGHT_VISION, 0, 0));
        out.add(effect(PotionEffectType.FIRE_RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Waxed w = waxed.get(target);
        if (w != null && w.caster.equals(player.getUniqueId())) {
            igniteAll(player);
            return;
        }
        if (chance(0.3)) {
            PotionEffect slow = target.getPotionEffect(PotionEffectType.SLOWNESS);
            int level = slow == null ? 0 : Math.min(2, slow.getAmplifier() + 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, level, false, true, true));
            Location at = Combat.center(target);
            Fx.particle(at, Particle.FALLING_HONEY, 8, 0.3, 0);
            Fx.particle(at, Particle.DUST, 8, 0.3, 0, Fx.dust(WAX, 1.2f));
            Fx.sound(at, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1f, 1.2f);
        }
    }

    // ---- Wickfire ----

    /**
     * Wickfire: a lit fuse runs along the ground from your feet to the enemy under your crosshair,
     * following it as it runs, and explodes when it catches up.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 24, 0.6);
        if (target == null) {
            tell(player, "Look at an enemy to light the fuse.");
            return false;
        }
        Location spark = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(0.8));
        Fx.sound(spark, Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f);
        Fx.sound(spark, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.6f);
        fx().flash(spark.clone().add(0, 0.3, 0), FLAME, 1.2f);

        new BukkitRunnable() {
            int ticks;
            final Location pos = Combat.ground(spark.clone().add(0, 0.5, 0));

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !target.isValid() || target.isDead() || target.getWorld() != pos.getWorld() || ticks > FUSE_TICKS) {
                    Fx.particle(pos, Particle.SMOKE, 12, 0.2, 0.02);
                    Fx.sound(pos, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.6f);
                    cancel();
                    return;
                }
                Vector toward = target.getLocation().toVector().subtract(pos.toVector());
                double distance = toward.clone().setY(0).length();
                if (distance < 0.9 && Math.abs(toward.getY()) < 2.5) {
                    blow(player, target);
                    cancel();
                    return;
                }
                // the spark races along the ground, zig-zagging a little like a real fuse
                Vector step = toward.setY(0).normalize().multiply(FUSE_SPEED);
                Vector side = new Vector(-step.getZ(), 0, step.getX()).multiply(Math.sin(ticks * 1.3) * 0.25);
                Location next = Combat.ground(pos.clone().add(step).add(side).add(0, 0.8, 0));
                pos.setX(next.getX());
                pos.setY(next.getY());
                pos.setZ(next.getZ());
                Location flame = pos.clone().add(0, 0.15, 0);
                Fx.particle(flame, Particle.FLAME, 2, 0.05, 0.02);
                Fx.particle(flame, Particle.SMALL_FLAME, 2, 0.08, 0.01);
                Fx.particle(flame, Particle.ELECTRIC_SPARK, 1, 0.05, 0.05);
                if (ticks % 2 == 0) {
                    fx().flamePillar(pos, 0.35f, 0.45f, 18, false);
                }
                if (ticks % 4 == 0) {
                    Fx.sound(pos, Sound.ENTITY_CREEPER_PRIMED, 0.4f, 2f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void blow(Player player, LivingEntity target) {
        Location at = Combat.center(target);
        combat().damage(player, target, 9, true);
        target.setFireTicks(Math.max(target.getFireTicks(), 80));
        for (LivingEntity near : combat().nearby(player, at, 2.8)) {
            if (near != target) {
                combat().damage(player, near, 4, true);
                near.setFireTicks(Math.max(near.getFireTicks(), 60));
                Combat.knockback(near, at, 0.8, 0.4);
            }
        }
        Combat.setVelocity(target, target.getVelocity().setY(0.6));
        fx().burst(at, FLAME, 2.6f);
        fx().flash(at, EMBER, 2.4f);
        Fx.particle(at, Particle.EXPLOSION, 1, 0, 0);
        Fx.particle(at, Particle.FLAME, 30, 0.5, 0.1);
        Fx.particle(at, Particle.FALLING_HONEY, 12, 0.6, 0);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
    }

    // ---- Melt ----

    /**
     * Melt: hot wax pours over every enemy within 8 blocks, slowing them to a crawl for eight
     * seconds, and a candle sputters on each of their heads. Hit any of them and every candle
     * flares: all of them go up in flames at once.
     */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> targets = combat().nearby(player, player.getLocation(), 8);
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        int until = org.bukkit.Bukkit.getCurrentTick() + MELT_TICKS;
        Location from = player.getLocation().add(0, 1.2, 0);
        Fx.sound(from, Sound.BLOCK_HONEY_BLOCK_PLACE, 1.4f, 0.6f);
        Fx.sound(from, Sound.BLOCK_CANDLE_EXTINGUISH, 1f, 0.6f);
        fx().flash(from, WAX, 2.4f);
        for (LivingEntity target : targets) {
            Waxed old = waxed.remove(target);
            if (old != null) {
                displays().remove(old.candle);
            }
            combat().damage(player, target, 2, true);
            applyEffect(target, PotionEffectType.SLOWNESS, MELT_TICKS, 2);
            Location head = target.getEyeLocation().add(0, 1.2, 0);
            head.setYaw(0);
            head.setPitch(0);
            BlockDisplay candle = displays().block(head, CANDLE, new Vector3f(1.8f), new Quaternionf(), MELT_TICKS + 10);
            candle.setTeleportDuration(1);
            candle.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            waxed.put(target, new Waxed(player.getUniqueId(), candle, until));
            fx().drift(from, Combat.center(target).toVector().subtract(from.toVector()), WAX, 0.7f, 6);
            Fx.particle(Combat.center(target), Particle.FALLING_HONEY, 20, 0.4, 0);
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                int now = org.bukkit.Bukkit.getCurrentTick();
                waxed.entrySet().removeIf(e -> {
                    LivingEntity t = e.getKey();
                    Waxed w = e.getValue();
                    if (!t.isValid() || t.isDead() || w.until < now || !w.candle.isValid()) {
                        displays().remove(w.candle);
                        return true;
                    }
                    Location head = t.getEyeLocation().add(0, 1.2, 0);
                    head.setYaw(0);
                    head.setPitch(0);
                    w.candle.teleport(head);
                    if (ticks % 3 == 0) {
                        Fx.particle(head.clone().add(0, 0.15, 0), Particle.SMALL_FLAME, 1, 0.01, 0);
                        Fx.particle(Combat.center(t), Particle.DRIPPING_HONEY, 1, 0.3, 0);
                    }
                    return false;
                });
                if (waxed.isEmpty()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Every candle flares at once: every waxed enemy goes up in flames. */
    private void igniteAll(Player player) {
        boolean any = false;
        for (Map.Entry<LivingEntity, Waxed> e : new HashMap<>(waxed).entrySet()) {
            if (!e.getValue().caster.equals(player.getUniqueId())) {
                continue;
            }
            LivingEntity target = e.getKey();
            waxed.remove(target);
            displays().remove(e.getValue().candle);
            if (!target.isValid() || target.isDead()) {
                continue;
            }
            any = true;
            Location at = Combat.center(target);
            combat().damage(player, target, 7, true);
            target.setFireTicks(Math.max(target.getFireTicks(), 120));
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            fx().flamePillar(Combat.ground(target.getLocation().add(0, 0.5, 0)), 1.1f, 2.6f, 16, false);
            fx().burst(at, FLAME, 2f);
            Fx.particle(at, Particle.FLAME, 25, 0.4, 0.08);
            Fx.particle(at, Particle.LAVA, 3, 0.3, 0);
        }
        if (any) {
            Fx.sound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.7f);
            Fx.sound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.6f);
        }
    }

    @Override
    public void shutdown() {
        for (Waxed w : waxed.values()) {
            displays().remove(w.candle);
        }
        waxed.clear();
    }
}
