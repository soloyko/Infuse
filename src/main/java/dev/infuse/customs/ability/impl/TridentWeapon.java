package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ocean's Rage, the Abyssal Trident (vanilla trident): the sea and the storm.
 * Passive Stormborn (always returns, thrown hits call lightning in any weather), on-hit Undertow,
 * F Abyssal Bubble, Sneak+F Maelstrom.
 */
public final class TridentWeapon extends SwordAbility implements Listener {

    private static final float MODEL_SCALE = 2.3f;
    private static final int TEAL = 0x2EC4C4;
    private static final int DEEP = 0x0B4F6C;
    private static final BlockData WATER_GLASS = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
    private static final BlockData DEEP_GLASS = Material.CYAN_STAINED_GLASS.createBlockData();

    private final NamespacedKey struckKey;

    public TridentWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.TRIDENT);
        this.struckKey = new NamespacedKey(plugin, "trident_struck");
    }

    // ---- Stormborn ----

    @Override
    public void addAttributes(ItemMeta meta) {
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(false);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.WATER_BREATHING, 0, 0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrownHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)
                || plugin.items().typeOf(trident.getItemStack()) != type || !plugin.canUse(player, type)
                || trident.getPersistentDataContainer().has(struckKey)) {
            return;
        }
        trident.getPersistentDataContainer().set(struckKey, PersistentDataType.BYTE, (byte) 1);
        Entity hit = event.getHitEntity();
        Location point = hit != null ? hit.getLocation() : trident.getLocation();
        point.getWorld().strikeLightningEffect(point);
        fx().bolt(point.clone().add(0, 14, 0), point, 0x9FF5F5, 0.8f, 6, 2);
        fx().crack(Combat.ground(point.clone().add(0, 0.5, 0)), 0x2EC4C4, 3f, 36);
        Fx.particle(point.clone().add(0, 0.5, 0), Particle.ELECTRIC_SPARK, 30, 0.6, 0.2);
        Fx.particle(point.clone().add(0, 0.3, 0), Particle.SPLASH, 40, 0.8, 0.1);
        for (LivingEntity target : combat().nearby(player, point, 2.5)) {
            combat().damage(player, target, 5);
        }
    }

    // ---- Undertow ----

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        event.setDamage(event.getDamage() + 3);
        applyEffect(target, PotionEffectType.SLOWNESS, 50, 2);
        Vector v = target.getVelocity();
        Combat.setVelocity(target, new Vector(v.getX() * 0.3, Math.min(v.getY(), -0.6), v.getZ() * 0.3));
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SPLASH, 30, 0.4, 0.2);
        Fx.particle(at, Particle.BUBBLE_POP, 15, 0.4, 0.05);
        Fx.particle(at, Particle.DUST, 12, 0.4, 0, Fx.dust(DEEP, 1.3f));
        Fx.sound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.8f, 0.8f);
    }

    // ---- abilities ----

    private Location aim(Player player, double range) {
        Location point = combat().aimPoint(player, range);
        if (point == null) {
            Location eye = player.getEyeLocation();
            point = eye.clone().add(eye.getDirection().multiply(Combat.clearDistance(player, range)));
        }
        return Combat.ground(point);
    }

    /** Abyssal Bubble: how long the bubble holds and how high it floats. */
    private static final int BUBBLE_TICKS = 60;

    /**
     * Abyssal Bubble: a globe of seawater wells up around the enemy under your crosshair and lifts
     * it off the ground. For three seconds it floats there helpless, drowning, then the bubble
     * bursts and slams it back down in a rush of water.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 28, 0.7);
        if (target == null) {
            tell(player, "Look at an enemy to trap it.");
            return false;
        }
        Location start = target.getLocation();
        boolean hadGravity = target.hasGravity();
        target.setGravity(false);
        Location at = Combat.center(target);
        at.setYaw(0);
        at.setPitch(0);
        float size = (float) Math.max(1.6, target.getHeight() + 0.6);
        org.bukkit.entity.BlockDisplay bubble = displays().block(at, org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(),
                new org.joml.Vector3f(0.1f), new org.joml.Quaternionf(), BUBBLE_TICKS + 10);
        bubble.setBrightness(new org.bukkit.entity.Display.Brightness(12, 15));
        bubble.setTeleportDuration(1);
        org.bukkit.entity.ItemDisplay sheen = fx().orb(at, 0x2EC4C4, size * 1.3f, BUBBLE_TICKS + 10);
        displays().later(1, () -> dev.infuse.customs.ability.Displays.animateBlock(bubble, new org.joml.Vector3f(size),
                new org.joml.Quaternionf().rotateXYZ(0.3f, 0.5f, 0.2f), new org.joml.Vector3f(), 6));
        fx().beam(player.getEyeLocation(), at, 0x2EC4C4, 0.2f, 6);
        Fx.particle(at, Particle.SPLASH, 40, 0.6, 0.2);
        Fx.sound(at, Sound.ITEM_BUCKET_FILL, 1.2f, 0.8f);
        Fx.sound(at, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, 1f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid() || target.isDead() || !alive(player) || ticks > BUBBLE_TICKS) {
                    burst();
                    return;
                }
                // floats up a couple of blocks and bobs there, turning slowly
                double rise = Math.min(2.2, ticks * 0.12) + Math.sin(ticks * 0.25) * 0.15;
                Location hold = start.clone().add(0, rise, 0);
                Combat.setVelocity(target, hold.toVector().subtract(target.getLocation().toVector()).multiply(0.5));
                Location center = Combat.center(target);
                center.setYaw(0);
                center.setPitch(0);
                bubble.teleport(center);
                sheen.teleport(center);
                if (ticks % 4 == 0) {
                    dev.infuse.customs.ability.Displays.animateBlock(bubble, new org.joml.Vector3f(size),
                            new org.joml.Quaternionf().rotateXYZ(0.3f, 0.5f + ticks * 0.05f, 0.2f), new org.joml.Vector3f(), 4);
                }
                Fx.particle(center, Particle.BUBBLE, 3, size * 0.3, 0.02);
                if (ticks % 3 == 0) {
                    Fx.particle(center, Particle.BUBBLE_POP, 2, size * 0.35, 0.02);
                }
                if (ticks % 20 == 0) {
                    combat().damage(player, target, 2, true);
                    Fx.sound(center, Sound.ENTITY_PLAYER_HURT_DROWN, 1f, 1f);
                }
            }

            private void burst() {
                cancel();
                target.setGravity(hadGravity);
                Location center = target.isValid() ? Combat.center(target) : at;
                displays().remove(bubble);
                displays().remove(sheen);
                Fx.particle(center, Particle.SPLASH, 80, 1, 0.3);
                Fx.particle(center, Particle.BUBBLE_POP, 30, 0.8, 0.1);
                fx().wave(center, new org.bukkit.util.Vector(0, 1, 0), 0x9FE0FF, 2.4f, 6);
                Fx.sound(center, Sound.ENTITY_GENERIC_SPLASH, 1.4f, 0.7f);
                Fx.sound(center, Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
                if (target.isValid() && !target.isDead() && alive(player)) {
                    Combat.setVelocity(target, new org.bukkit.util.Vector(0, -1.6, 0));
                    combat().damage(player, target, 6, true);
                    target.setFireTicks(0);
                    applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }



    /** Maelstrom: a whirlpool drags enemies around and in for four seconds, then erupts as a geyser. */
    @Override
    public boolean secondary(Player player) {
        Location center = aim(player, 20).add(0, 0.1, 0);
        center.setYaw(0);
        center.setPitch(0);
        List<BlockDisplay> waves = new ArrayList<>();
        int arms = 8;
        for (int i = 0; i < arms; i++) {
            BlockData data = i % 2 == 0 ? WATER_GLASS : DEEP_GLASS;
            BlockDisplay wave = displays().block(center, data, new Vector3f(0.05f), new Quaternionf(), 100);
            wave.setBrightness(new org.bukkit.entity.Display.Brightness(12, 15));
            wave.setTeleportDuration(2);
            waves.add(wave);
        }
        Fx.sound(center, Sound.AMBIENT_UNDERWATER_ENTER, 1.2f, 0.6f);
        Fx.sound(center, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.5f, 0.8f);
        fx().castCircle(center, 0x2EC4C4, 6.5f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || player.getWorld() != center.getWorld()) {
                    waves.forEach(displays()::remove);
                    cancel();
                    return;
                }
                if (ticks > 80) {
                    waves.forEach(displays()::remove);
                    geyser(player, center);
                    cancel();
                    return;
                }
                double grow = Math.min(1, ticks / 10.0);
                // spinning water arms: each a sliver of glass circling the eye, inner ones faster
                for (int i = 0; i < waves.size(); i++) {
                    BlockDisplay wave = waves.get(i);
                    double radius = (1.2 + (i % 4) * 1.2) * grow;
                    double speed = 0.35 - (i % 4) * 0.06;
                    double angle = ticks * speed + i * Math.PI * 2 / waves.size();
                    Location p = center.clone().add(Math.cos(angle) * radius, 0.15 + (i % 4) * 0.12, Math.sin(angle) * radius);
                    if (ticks % 2 == 0 || ticks <= 2) {
                        wave.teleport(p);
                        Quaternionf facing = new Quaternionf().rotateY((float) (-angle));
                        Displays.animateBlock(wave, new Vector3f((float) (0.9 + radius * 0.25), 0.12f, 0.35f), facing,
                                new Vector3f(), 2);
                    }
                }
                if (ticks % 2 == 0) {
                    for (int arm = 0; arm < 3; arm++) {
                        double a = ticks * 0.3 + arm * Math.PI * 2 / 3;
                        for (double rad = 0.6; rad <= 5.5; rad += 0.9) {
                            double ang = a + rad * 0.55;
                            Location p = center.clone().add(Math.cos(ang) * rad, 0.2, Math.sin(ang) * rad);
                            Fx.particle(p, Particle.SPLASH, 2, 0.1, 0);
                            if (rad > 4) {
                                Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(TEAL, 1.2f));
                            }
                        }
                    }
                    Fx.particle(center.clone().add(0, 0.4, 0), Particle.BUBBLE_POP, 6, 0.6, 0.05);
                }
                if (ticks % 20 == 0) {
                    Fx.sound(center, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.5f, 0.7f);
                }
                for (LivingEntity target : combat().nearby(player, center, 6)) {
                    Vector in = center.toVector().subtract(target.getLocation().toVector()).setY(0);
                    double distance = in.length();
                    if (distance < 0.3) {
                        continue;
                    }
                    in.multiply(1 / distance);
                    Vector around = new Vector(-in.getZ(), 0, in.getX());
                    Vector pull = around.multiply(0.32).add(in.multiply(0.12 + 0.03 * (6 - distance)));
                    Combat.setVelocity(target, pull.setY(Math.min(target.getVelocity().getY(), 0.0)));
                    if (ticks % 10 == 0) {
                        combat().damage(player, target, 2);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void geyser(Player player, Location center) {
        Fx.sound(center, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);
        Fx.sound(center, Sound.AMBIENT_UNDERWATER_EXIT, 1.5f, 0.8f);
        Fx.sound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.7f);
        fx().beam(center, center.clone().add(0, 8, 0), 0x9FF5F5, 2.2f, 10);
        fx().shockwave(center, 0x2EC4C4, 5.5f, 8);
        fx().burst(center.clone().add(0, 1.5, 0), 0xD8FFFF, 3f);
        BlockDisplay column = displays().block(center.clone().add(0, 0.2, 0), WATER_GLASS, new Vector3f(1.4f, 0.1f, 1.4f),
                new Quaternionf(), 30);
        column.setBrightness(new org.bukkit.entity.Display.Brightness(12, 15));
        displays().later(1, () -> Displays.animateBlock(column, new Vector3f(1.2f, 7f, 1.2f), new Quaternionf(),
                new Vector3f(0, 3.5f, 0), 4));
        displays().later(14, () -> Displays.animateBlock(column, new Vector3f(0.2f, 9f, 0.2f), new Quaternionf(),
                new Vector3f(0, 5f, 0), 8));
        for (int i = 0; i < 6; i++) {
            int step = i;
            displays().later(1 + i, () -> {
                Location p = center.clone().add(0, step * 1.2, 0);
                Fx.particle(p, Particle.SPLASH, 40, 0.7, 0.3);
                Fx.particle(p, Particle.CLOUD, 6, 0.4, 0.05);
                Fx.particle(p, Particle.DUST, 8, 0.6, 0, Fx.dust(step % 2 == 0 ? TEAL : DEEP, 1.6f));
            });
        }
        Block under = Displays.groundBelow(center.clone().subtract(0, 0.2, 0));
        if (under != null) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                displays().eruptGround(center.clone().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8), 0.45f, 0.8f);
            }
        }
        for (LivingEntity target : combat().nearby(player, center, 4.5)) {
            combat().damage(player, target, 8);
            Combat.setVelocity(target, new Vector(0, 1.35, 0));
        }
    }
}
