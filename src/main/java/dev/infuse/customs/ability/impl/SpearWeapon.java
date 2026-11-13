package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Skypiercer, the Celestial Spear (netherite spear): reach and piercing throws.
 * Passive Long Reach, on-hit Impale, F Javelin, Sneak+F Phalanx.
 */
public final class SpearWeapon extends SwordAbility {

    private static final float MODEL_SCALE = 2.2f;
    private static final int GOLD = 0xE9C46A;

    public SpearWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.SPEAR);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ENTITY_INTERACTION_RANGE, "reach", 1.5, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        event.setDamage(event.getDamage() + 3);
        applyEffect(target, PotionEffectType.SLOWNESS, 20, 4);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!target.isValid() || ticks++ >= 20) {
                    cancel();
                    return;
                }
                Combat.setVelocity(target, new Vector(0, Math.min(0, target.getVelocity().getY()), 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.CRIT, 20, 0.3, 0.3);
        Fx.sound(at, Sound.ITEM_TRIDENT_HIT, 0.9f, 1.2f);
    }

    private ItemStack spear() {
        return plugin.items().create(type);
    }

    /** Javelin: a spectral spear flies up to 32 blocks, pierces everything, then flies home. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getEyeLocation().subtract(0, 0.25, 0);
        Vector dir = start.getDirection().normalize();
        ItemDisplay javelin = displays().item(start, spear(), MODEL_SCALE, Displays.pointing(dir),
                ItemDisplay.ItemDisplayTransform.NONE, 80);
        javelin.setGlowing(true);
        ItemDisplay javGlow = fx().orb(start.clone().add(dir.clone().multiply(1.5)), 0xFFE08A, 1.4f, 80);
        Fx.sound(start, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.8f);
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            final Location pos = start.clone();
            int ticks;
            int stuck = -1;
            boolean returning;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !javelin.isValid() || ticks > 70 || player.getWorld() != pos.getWorld()) {
                    displays().remove(javelin);
                    displays().remove(javGlow);
                    cancel();
                    return;
                }
                if (!returning) {
                    if (stuck >= 0) {
                        if (++stuck > 12) {
                            returning = true;
                        }
                        return;
                    }
                    RayTraceResult block = pos.getWorld().rayTraceBlocks(pos, dir, 1.6, FluidCollisionMode.NEVER, true);
                    double step = block == null ? 1.6 : block.getHitPosition().distance(pos.toVector()) - 0.3;
                    Location next = pos.clone().add(dir.clone().multiply(Math.max(0, step)));
                    pierce(player, pos, next, hit);
                    pos.setX(next.getX());
                    pos.setY(next.getY());
                    pos.setZ(next.getZ());
                    javelin.teleport(pos);
                    javGlow.teleport(pos);
                    Fx.particle(pos, Particle.DUST, 2, 0.05, 0, Fx.dust(GOLD, 1.2f));
                    Fx.particle(pos, Particle.END_ROD, 1, 0.02, 0.01);
                    if (block != null) {
                        stuck = 0;
                        Fx.sound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
                        Block b = block.getHitBlock();
                        if (b != null) {
                            Fx.particle(pos, Particle.BLOCK, 12, 0.2, 0.1, b.getBlockData());
                        }
                    } else if (ticks >= 20) {
                        returning = true;
                    }
                    return;
                }
                Location home = player.getEyeLocation().subtract(0, 0.4, 0);
                Vector back = home.toVector().subtract(pos.toVector());
                double distance = back.length();
                if (distance < 1.4) {
                    Fx.sound(home, Sound.ITEM_TRIDENT_RETURN, 1f, 1.2f);
                    displays().remove(javelin);
                    displays().remove(javGlow);
                    cancel();
                    return;
                }
                back.multiply(Math.min(2.0, distance) / distance);
                pos.add(back);
                Transformation t = javelin.getTransformation();
                javelin.setTransformation(new Transformation(t.getTranslation(), Displays.pointing(back.clone().multiply(-1)),
                        t.getScale(), t.getRightRotation()));
                javelin.teleport(pos);
                    javGlow.teleport(pos);
                Fx.particle(pos, Particle.END_ROD, 1, 0.02, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void pierce(Player player, Location from, Location to, Set<UUID> hit) {
        Vector segment = to.toVector().subtract(from.toVector());
        double length = segment.length();
        if (length < 1.0E-3) {
            return;
        }
        Vector dir = segment.clone().multiply(1 / length);
        Location mid = from.clone().add(segment.clone().multiply(0.5));
        for (LivingEntity target : combat().nearby(player, mid, length / 2 + 1.5)) {
            if (!hit.contains(target.getUniqueId())
                    && target.getBoundingBox().expand(0.6).rayTrace(from.toVector(), dir, length) != null) {
                hit.add(target.getUniqueId());
                combat().damage(player, target, 8);
                applyEffect(target, PotionEffectType.SLOWNESS, 30, 1);
                Fx.particle(Combat.center(target), Particle.CRIT, 15, 0.3, 0.3);
                Fx.sound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 1f);
                fx().burst(Combat.center(target), 0xE9C46A, 1.6f);
            }
        }
    }

    /** Phalanx: eight spears thrust up out of the ground in a line ahead. */
    @Override
    public boolean secondary(Player player) {
        Location origin = player.getLocation();
        Vector dir = origin.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        Vector forward = dir.normalize();
        Set<UUID> hit = new HashSet<>();
        Fx.sound(origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 0.7f);
        for (int i = 0; i < 8; i++) {
            Location point = Combat.ground(origin.clone().add(forward.clone().multiply(2 + i * 1.5)).add(0, 1, 0));
            displays().later(1 + i * 2L, () -> thrust(player, point, hit));
        }
        return true;
    }

    private void thrust(Player player, Location ground, Set<UUID> hit) {
        if (!alive(player) || player.getWorld() != ground.getWorld()) {
            return;
        }
        Location base = ground.clone().add(0, 0.2, 0);
        base.setYaw(0);
        base.setPitch(0);
        Quaternionf up = new Quaternionf().rotateY((float) (Math.random() * Math.PI * 2));
        ItemDisplay spike = displays().item(base, spear(), MODEL_SCALE, up, ItemDisplay.ItemDisplayTransform.NONE, 22);
        spike.setTransformation(new Transformation(new Vector3f(0, -1.6f, 0), up, new Vector3f(MODEL_SCALE), new Quaternionf()));
        displays().later(1, () -> Displays.animate(spike,
                new Transformation(new Vector3f(0, 0.7f, 0), up, new Vector3f(MODEL_SCALE), new Quaternionf()), 3));
        displays().later(15, () -> Displays.animate(spike,
                new Transformation(new Vector3f(0, -1.8f, 0), up, new Vector3f(MODEL_SCALE), new Quaternionf()), 5));
        Block under = Displays.groundBelow(ground.clone().subtract(0, 0.1, 0));
        if (under != null) {
            Fx.particle(base, Particle.BLOCK, 14, 0.3, 0.1, under.getBlockData());
        }
        Fx.sound(base, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1.2f);
        Fx.sound(base, Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 1.4f);
        fx().shockwave(base, 0xE9C46A, 1.6f, 6);
        fx().beam(base, base.clone().add(0, 2.6, 0), 0xFFF0B8, 0.5f, 6);
        for (LivingEntity target : combat().nearby(player, base.clone().add(0, 0.8, 0), 1.6)) {
            if (hit.add(target.getUniqueId())) {
                combat().damage(player, target, 7);
                Combat.setVelocity(target, target.getVelocity().setY(0.95));
            }
        }
    }
}
