package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jadewarden, the Jade Halberd (netherite spear): the emperor's guard.
 * Passive Imperial Guard, on-hit Jade Cleave, F Dragon Sweep, Sneak+F Jade Dragon.
 */
public final class HalberdWeapon extends SwordAbility {

    private static final int JADE = 0x3FBF7F;
    private static final int PALE_JADE = 0xA8F0C8;
    private static final int GOLD = 0xE8C35A;
    private static final BlockData BODY = Material.EMERALD_BLOCK.createBlockData();
    private static final BlockData SCALE = Material.GOLD_BLOCK.createBlockData();

    public HalberdWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.HALBERD);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ENTITY_INTERACTION_RANGE, "reach", 1.0, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(meta, Attribute.ARMOR, "armor", 4, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            return;
        }
        look.normalize();
        double damage = event.getDamage() * 0.5;
        for (LivingEntity other : combat().nearby(player, player.getLocation().add(look.clone().multiply(2)), 2.8)) {
            if (other != target) {
                combat().damage(player, other, damage);
            }
        }
        fx().slash(eye.clone().subtract(0, 0.5, 0).add(look.clone().multiply(0.3)), look, 0.1, JADE, 2.8f, 3);
        Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
    }

    /** Dragon Sweep: a full circle of the halberd, twice, hurling everything back. */
    @Override
    public boolean primary(Player player) {
        Location center = player.getLocation().add(0, 1, 0);
        Vector look = center.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        for (int i = 0; i < 4; i++) {
            Vector dir = look.clone().rotateAroundY(i * Math.PI / 2);
            int color = i % 2 == 0 ? JADE : GOLD;
            displays().later(i * 2L, () -> fx().slash(player.getLocation().add(0, 1, 0), dir, 0.05, color, 2.9f, 3));
        }
        displays().later(4, () -> {
            Location at = player.getLocation();
            fx().shockwave(at, JADE, 5f, 8);
            fx().shockwave(at, PALE_JADE, 3.2f, 6);
            for (LivingEntity target : combat().nearby(player, at.clone().add(0, 1, 0), 4.6)) {
                combat().damage(player, target, 8);
                Combat.knockback(target, at, 1.5, 0.45);
                fx().burst(Combat.center(target), PALE_JADE, 1.3f);
            }
        });
        Fx.sound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        Fx.sound(center, Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 0.8f);
        return true;
    }

    /**
     * Jade Dragon: a serpent of jade with a glowing dragon's head winds forward about 20 blocks,
     * rising and diving, biting and tossing enemies. Its scales are turned along its body.
     */
    @Override
    public boolean secondary(Player player) {
        Location eye = player.getEyeLocation().subtract(0, 0.6, 0);
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        Vector dir = forward.normalize();
        Vector side = Fx.right(dir);
        Location start = eye.clone().add(dir.clone().multiply(2)); // not in the caster's face
        int segments = 18;
        List<BlockDisplay> body = new ArrayList<>();
        List<Float> sizes = new ArrayList<>();
        Location spawnAt = start.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        org.bukkit.entity.ItemDisplay head = fx().spectralHead(start, Material.DRAGON_HEAD, JADE, 1.9f, dir, 70);
        head.setTeleportDuration(1);
        for (int i = 0; i < segments; i++) {
            boolean gold = i % 3 == 1;
            // thick behind the head, tapering to a thin tail
            float size = (float) ((gold ? 0.62 : 0.95) * (1.0 - i * 0.032));
            BlockDisplay seg = displays().block(spawnAt, gold ? SCALE : BODY, new Vector3f(size), new Quaternionf(), 70);
            seg.setBrightness(new Display.Brightness(15, 15));
            seg.setTeleportDuration(1);
            seg.setGlowing(true);
            seg.setGlowColorOverride(Color.fromRGB(gold ? GOLD : JADE));
            body.add(seg);
            sizes.add(size);
        }
        org.bukkit.entity.ItemDisplay aura = fx().orb(start, JADE, 2.6f, 70);
        Deque<Location> trail = new ArrayDeque<>();
        Set<UUID> bitten = new HashSet<>();
        Fx.sound(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.6f);
        Fx.sound(start, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
        fx().flash(start, JADE, 2.8f);
        fx().castCircle(player.getLocation(), JADE, 2.2f);

        new BukkitRunnable() {
            int ticks;
            final Location base = start.clone();

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 55 || base.getWorld() != player.getWorld()) {
                    finish();
                    return;
                }
                base.add(dir.clone().multiply(0.45));
                double wave = Math.sin(ticks * 0.42) * 1.8;
                double bob = Math.sin(ticks * 0.27) * 0.9 + 0.6;
                Location headAt = base.clone().add(side.clone().multiply(wave)).add(0, bob, 0);
                if (!headAt.getBlock().isPassable()) {
                    headAt.add(0, 1, 0);
                }
                Location previous = trail.isEmpty() ? start : trail.peekFirst();
                trail.addFirst(headAt.clone());
                while (trail.size() > segments * 2 + 2) {
                    trail.removeLast();
                }
                Vector heading = headAt.toVector().subtract(previous.toVector());
                if (heading.lengthSquared() < 1.0E-4) {
                    heading = dir.clone();
                }
                head.teleport(headAt.clone().add(heading.clone().normalize().multiply(0.3)));
                head.setInterpolationDelay(0);
                head.setInterpolationDuration(1);
                head.setTransformation(new Transformation(new Vector3f(), dev.infuse.customs.ability.Fx3D.headRotation(heading),
                        new Vector3f(1.9f), new Quaternionf()));
                aura.teleport(headAt);
                // body segments follow the head's path two steps apart, each turned along it
                List<Location> points = new ArrayList<>(trail);
                for (int i = 0; i < body.size(); i++) {
                    int idx = Math.min(points.size() - 1, (i + 1) * 2);
                    BlockDisplay seg = body.get(i);
                    if (idx < 0 || !seg.isValid()) {
                        continue;
                    }
                    Location at = points.get(idx);
                    Location ahead = points.get(Math.max(0, idx - 1));
                    Vector along = ahead.toVector().subtract(at.toVector());
                    Quaternionf turn = along.lengthSquared() < 1.0E-4 ? new Quaternionf()
                            : new Quaternionf().rotateY((float) Math.atan2(-along.getX(), along.getZ()))
                            .rotateX((float) -Math.asin(Math.max(-1, Math.min(1, along.clone().normalize().getY()))))
                            .rotateZ((float) (Math.PI / 4));
                    seg.teleport(at);
                    Displays.animateBlock(seg, new Vector3f(sizes.get(i)), turn, new Vector3f(), 1);
                }
                Fx.particle(headAt, Particle.DUST, 4, 0.3, 0, Fx.dust(PALE_JADE, 1.5f));
                if (ticks % 2 == 0) {
                    Fx.particle(headAt, Particle.HAPPY_VILLAGER, 1, 0.4, 0);
                }
                if (ticks % 3 == 0 && points.size() > 6) {
                    Location tail = points.get(Math.min(points.size() - 1, segments * 2 - 2));
                    Fx.particle(tail, Particle.END_ROD, 1, 0.1, 0.01);
                    Fx.particle(tail, Particle.DUST, 2, 0.15, 0, Fx.dust(GOLD, 1.1f));
                }
                for (LivingEntity target : combat().nearby(player, headAt, 1.9)) {
                    if (bitten.add(target.getUniqueId())) {
                        combat().damage(player, target, 9);
                        Combat.setVelocity(target, dir.clone().multiply(0.4).setY(0.9));
                        Location at = Combat.center(target);
                        fx().burst(at, JADE, 2.2f);
                        // jaws: two cuts closing on the target
                        fx().cut(at, heading, Math.PI / 2 - 0.5, 0.6, PALE_JADE, 1.7f, 2);
                        fx().cut(at, heading, -Math.PI / 2 + 0.5, 0.6, GOLD, 1.7f, 2);
                        Fx.particle(at, Particle.BLOCK, 12, 0.3, 0.1, BODY);
                        Fx.sound(at, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1.2f);
                        Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.8f);
                    }
                }
                if (ticks % 10 == 0) {
                    Fx.sound(headAt, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.6f);
                }
            }

            private void finish() {
                Location end = head.isValid() ? head.getLocation() : base;
                fx().burst(end, JADE, 2.6f);
                fx().flash(end, PALE_JADE, 2.2f);
                fx().shockwave(end, JADE, 3f, 7);
                Fx.sound(end, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.9f);
                displays().remove(head);
                displays().remove(aura);
                for (int i = 0; i < body.size(); i++) {
                    Display seg = body.get(i);
                    displays().later(1 + i, () -> {
                        if (seg.isValid()) {
                            Fx.particle(seg.getLocation(), Particle.BLOCK, 6, 0.2, 0.05, BODY);
                            fx().flash(seg.getLocation(), JADE, 0.8f);
                        }
                        displays().remove(seg);
                    });
                }
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
