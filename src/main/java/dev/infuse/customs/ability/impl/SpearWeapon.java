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
 * Sunbreak, the Celestial Spear (netherite spear): reach and piercing throws.
 * Passive Long Reach, on-hit Impale, F Skyhook, Sneak+F Phalanx.
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

    /** How far the chained spear can fly, and how fast. */
    private static final double HOOK_RANGE = 28;
    private static final double HOOK_SPEED = 2.2;

    /**
     * Skyhook: the spear flies out on a chain. An enemy it hits is impaled and dragged back to you;
     * a block it hits holds fast and the chain hauls you to it (no fall damage); otherwise it is
     * reeled back in.
     */
    @Override
    public boolean primary(Player player) {
        Location start = player.getEyeLocation().subtract(0, 0.25, 0);
        Vector dir = start.getDirection().normalize();
        ItemDisplay hook = displays().item(start, spear(), MODEL_SCALE, Displays.pointing(dir),
                ItemDisplay.ItemDisplayTransform.NONE, 120);
        hook.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        Fx.sound(start, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.7f);
        Fx.sound(start, Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);

        new BukkitRunnable() {
            final Location pos = start.clone();
            int ticks;
            double travelled;
            /** 0 flying out, 1 dragging an enemy in, 2 hauling the caster, 3 reeling back. */
            int phase;
            LivingEntity caught;
            int phaseTicks;
            final Set<UUID> swept = new HashSet<>();

            @Override
            public void run() {
                ticks++;
                phaseTicks++;
                if (!alive(player) || !hook.isValid() || player.getWorld() != pos.getWorld() || ticks > 110) {
                    finish();
                    return;
                }
                Location hand = dev.infuse.customs.ability.Fx3D.handOrigin(player);
                switch (phase) {
                    case 0 -> flyOut();
                    case 1 -> dragIn(hand);
                    case 2 -> haul();
                    default -> reel(hand);
                }
                chain(hand, pos);
            }

            private void flyOut() {
                RayTraceResult hit = pos.getWorld().rayTrace(pos, dir, HOOK_SPEED, FluidCollisionMode.NEVER, true, 0.5,
                        e -> combat().canHit(player, e));
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                    caught = target;
                    move(Combat.center(target));
                    combat().damage(player, target, 7);
                    applyEffect(target, PotionEffectType.SLOWNESS, 40, 2);
                    Location at = Combat.center(target);
                    fx().burst(at, 0xFFE08A, 2f);
                    Fx.particle(at, Particle.CRIT, 20, 0.3, 0.3);
                    Fx.sound(at, Sound.ITEM_TRIDENT_HIT, 1f, 0.8f);
                    Fx.sound(at, Sound.BLOCK_CHAIN_BREAK, 1f, 0.6f);
                    next(1);
                    return;
                }
                if (hit != null && hit.getHitBlock() != null) {
                    move(hit.getHitPosition().toLocation(pos.getWorld()).subtract(dir.clone().multiply(0.4)));
                    Fx.particle(pos, Particle.BLOCK, 20, 0.3, 0.1, hit.getHitBlock().getBlockData());
                    fx().burst(pos, 0xFFE08A, 1.8f);
                    fx().wave(pos, dir, GOLD, 1.4f, 6);
                    Fx.sound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.7f);
                    Fx.sound(pos, Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f);
                    status().protectFromFall(player, 80);
                    next(2);
                    return;
                }
                move(pos.clone().add(dir.clone().multiply(HOOK_SPEED)));
                travelled += HOOK_SPEED;
                if (travelled >= HOOK_RANGE) {
                    next(3);
                }
            }

            /** The impaled enemy is hauled to just in front of you, the spear stuck in it. */
            private void dragIn(Location hand) {
                if (caught == null || !caught.isValid() || caught.isDead()) {
                    next(3);
                    return;
                }
                Location front = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.8));
                Vector pull = front.toVector().subtract(caught.getLocation().toVector());
                double distance = pull.length();
                if (distance < 1.4 || phaseTicks > 16) {
                    Combat.setVelocity(caught, new Vector(0, 0.2, 0));
                    fx().burst(Combat.center(caught), GOLD, 1.6f);
                    Fx.sound(caught.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.1f);
                    next(3);
                    return;
                }
                Combat.setVelocity(caught, pull.multiply(Math.min(1.5, distance * 0.35) / distance).setY(0.18));
                move(Combat.center(caught));
                if (phaseTicks % 3 == 0) {
                    Fx.sound(pos, Sound.BLOCK_CHAIN_STEP, 1f, 0.7f);
                }
            }

            /** The spear holds fast and the chain hauls you to it; you clip anyone you fly past. */
            private void haul() {
                Location body = player.getLocation().add(0, 1, 0);
                Vector pull = pos.toVector().subtract(body.toVector());
                double distance = pull.length();
                if (distance < 1.8 || phaseTicks > 30 || player.isSneaking() && phaseTicks > 3) {
                    // arrive with a little hop so you can land on a ledge
                    Combat.setVelocity(player, player.getVelocity().multiply(0.3).setY(0.45));
                    fx().burst(body, 0xFFE08A, 1.6f);
                    Fx.sound(body, Sound.ITEM_TRIDENT_RETURN, 1f, 1.2f);
                    next(3);
                    return;
                }
                Combat.setVelocity(player, pull.multiply(Math.min(1.6, 0.6 + distance * 0.12) / distance));
                Fx.particle(body, Particle.CLOUD, 2, 0.2, 0.01);
                if (phaseTicks % 2 == 0) {
                    fx().afterimage(player.getLocation().add(0, 1, 0), spear(), pull, GOLD, 1.0f, 6);
                }
                for (LivingEntity target : combat().nearby(player, body, 1.8)) {
                    if (swept.add(target.getUniqueId())) {
                        combat().damage(player, target, 5);
                        Combat.knockback(target, body, 0.8, 0.4);
                        fx().cut(Combat.center(target), pull, Math.random() * Math.PI, 0.4, GOLD, 1.4f, 2);
                        Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
                    }
                }
            }

            private void reel(Location hand) {
                Vector back = hand.toVector().subtract(pos.toVector());
                double distance = back.length();
                if (distance < 1.6) {
                    Fx.sound(hand, Sound.ITEM_TRIDENT_RETURN, 1f, 1.3f);
                    finish();
                    return;
                }
                move(pos.clone().add(back.multiply(Math.min(2.6, distance) / distance)));
            }

            private void move(Location to) {
                // the spear keeps pointing the way it was thrown, in or out
                pos.setX(to.getX());
                pos.setY(to.getY());
                pos.setZ(to.getZ());
                hook.teleport(pos);
            }

            private void next(int phase) {
                this.phase = phase;
                phaseTicks = 0;
            }

            private void finish() {
                displays().remove(hook);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** The chain between your hand and the spear: dark links with a golden glint. */
    private void chain(Location hand, Location tip) {
        Vector span = tip.toVector().subtract(hand.toVector());
        double length = span.length();
        if (length < 0.6) {
            return;
        }
        Vector step = span.clone().multiply(0.45 / length);
        Location link = hand.clone().add(step.clone().multiply(2)); // not right in front of your eyes
        for (double d = 0.9; d < length; d += 0.45) {
            Fx.particle(link, Particle.DUST, 1, 0, 0, Fx.dust(((int) (d / 0.45)) % 3 == 0 ? GOLD : 0x5A5A66, 0.8f));
            link.add(step);
        }
    }

    /**
     * Phalanx: clusters of spears thrust up out of the ground in a line ahead, three at a time in
     * a fan, ending in one giant spear wrapped in a column of light.
     */
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
        fx().castCircle(origin, GOLD, 1.8f);
        int count = 8;
        for (int i = 0; i < count; i++) {
            Location point = Combat.ground(origin.clone().add(forward.clone().multiply(2 + i * 1.5)).add(0, 1, 0));
            boolean last = i == count - 1;
            displays().later(1 + i * 2L, () -> thrust(player, point, forward, hit, last));
        }
        return true;
    }

    private void thrust(Player player, Location ground, Vector forward, Set<UUID> hit, boolean last) {
        if (!alive(player) || player.getWorld() != ground.getWorld()) {
            return;
        }
        Location base = ground.clone().add(0, 0.2, 0);
        base.setYaw(0);
        base.setPitch(0);
        if (last) {
            // the last one is huge
            spear(base, new Quaternionf().rotateY((float) (Math.random() * Math.PI * 2)), MODEL_SCALE * 1.6f, 1.3f, 26);
            fx().beam(base, base.clone().add(0, 7, 0), 0xFFF0B8, 1.1f, 12);
            fx().shockwave(base, GOLD, 3.4f, 9);
            fx().burst(base.clone().add(0, 1.4, 0), 0xFFE08A, 2.6f);
            Fx.sound(base, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.6f);
        } else {
            // a fan of three: one straight up and two leaning out to the sides
            float spin = (float) (Math.random() * Math.PI * 2);
            spear(base, new Quaternionf().rotateY(spin), MODEL_SCALE, 0.7f, 22);
            for (int side = -1; side <= 1; side += 2) {
                Quaternionf lean = new Quaternionf().rotationAxis(side * 0.42f, (float) forward.getX(), 0, (float) forward.getZ())
                        .rotateY(spin + side);
                spear(base, lean, MODEL_SCALE * 0.8f, 0.45f, 20);
            }
            fx().shockwave(base, GOLD, 1.8f, 6);
            fx().beam(base, base.clone().add(0, 2.6, 0), 0xFFF0B8, 0.5f, 6);
        }
        Block under = Displays.groundBelow(ground.clone().subtract(0, 0.1, 0));
        if (under != null) {
            Fx.particle(base, Particle.BLOCK, last ? 30 : 14, last ? 0.6 : 0.3, 0.1, under.getBlockData());
        }
        Fx.particle(base.clone().add(0, 0.6, 0), Particle.CRIT, 6, 0.3, 0.2);
        Fx.sound(base, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, last ? 0.8f : 1.2f);
        Fx.sound(base, Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 1.4f);
        double radius = last ? 2.4 : 1.7;
        for (LivingEntity target : combat().nearby(player, base.clone().add(0, 0.8, 0), radius)) {
            if (hit.add(target.getUniqueId())) {
                combat().damage(player, target, last ? 10 : 7);
                Combat.setVelocity(target, target.getVelocity().setY(last ? 1.2 : 0.95));
                fx().burst(Combat.center(target), 0xFFE08A, 1.4f);
            }
        }
    }

    /** A spear that bursts up out of the ground along its own axis, holds, then sinks back. */
    private void spear(Location base, Quaternionf rotation, float scale, float rise, int life) {
        ItemDisplay spike = displays().item(base, spear(), scale, rotation, ItemDisplay.ItemDisplayTransform.NONE, life);
        spike.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        Vector3f hidden = rotation.transform(new Vector3f(0, -0.75f * scale, 0));
        Vector3f shown = rotation.transform(new Vector3f(0, rise * scale / MODEL_SCALE, 0));
        spike.setTransformation(new Transformation(hidden, rotation, new Vector3f(scale), new Quaternionf()));
        displays().later(1, () -> Displays.animate(spike, new Transformation(shown, rotation, new Vector3f(scale), new Quaternionf()), 3));
        displays().later(life - 7, () -> Displays.animate(spike,
                new Transformation(hidden, rotation, new Vector3f(scale), new Quaternionf()), 5));
    }
}
