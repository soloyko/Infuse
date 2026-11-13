package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crimson Sword: lifesteal.
 * Passive Blood Reserve, on-hit Bloodthirst, RC Blood Crescent, Sneak+RC Crimson Harvest.
 */
public final class CrimsonSword extends SwordAbility {

    private static final int BLOOD_GLOW = 0xFF3048;

    private static final int RED = 0xC4415B;
    private static final int DARK_RED = 0x6B0F1A;

    public CrimsonSword(CustomsPlugin plugin) {
        super(plugin, SwordType.CRIMSON);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.MAX_HEALTH, "max_health", 6, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        combat().heal(player, event.getFinalDamage() * 0.15);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.DUST, 8, 0.3, 0, Fx.dust(RED, 1.2f));
        if (chance(0.25)) {
            status().bleed(player, target, 4);
            Fx.sound(at, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.8f);
        }
    }

    /** Blood Crescent: a wide slash that travels forward and cuts through everything. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getEyeLocation().subtract(0, 0.4, 0);
        // flies level, so aiming a little down doesn't drive it into the ground
        Vector flat = start.getDirection().setY(0);
        Vector dir = flat.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : flat.normalize();
        Vector right = Fx.right(dir);
        Fx.sound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        Fx.sound(start, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
        Set<UUID> hit = new HashSet<>();
        float size = 2.4f;
        // tilted diagonally so it reads as a crescent from behind, not a flat line
        org.bukkit.entity.ItemDisplay crescent = fx().movingSlash(start.clone().subtract(dir.clone().multiply(0.94 * size)),
                dir, -0.6, BLOOD_GLOW, size, 40);

        new BukkitRunnable() {
            final Location center = start.clone();
            int steps;

            @Override
            public void run() {
                if (!alive(player) || center.getWorld() != player.getWorld() || steps++ >= 14) {
                    fx().fade(crescent, dev.infuse.customs.ability.Fx3D.Shape.SLASH, BLOOD_GLOW, 1, 1);
                    cancel();
                    return;
                }
                center.add(dir.clone().multiply(1.2));
                if (!center.getBlock().isPassable()) {
                    Fx.particle(center, Particle.DUST, 20, 0.5, 0, Fx.dust(DARK_RED, 1.5f));
                    fx().burst(center, 0xD3122E, 1.8f);
                    fx().fade(crescent, dev.infuse.customs.ability.Fx3D.Shape.SLASH, BLOOD_GLOW, 1, 1);
                    cancel();
                    return;
                }
                if (crescent.isValid()) {
                    Location at = center.clone().subtract(dir.clone().multiply(0.94 * size));
                    at.setYaw(0);
                    at.setPitch(0);
                    crescent.teleport(at);
                }
                for (double s = -1.6; s <= 1.6; s += 0.2) {
                    Location p = center.clone().add(right.clone().multiply(s)).subtract(dir.clone().multiply(s * s * 0.25));
                    Fx.particle(p, Particle.DUST, 1, 0.02, 0, Fx.dust(Math.abs(s) > 1.1 ? DARK_RED : RED, 1.4f));
                }
                if (steps % 2 == 0 && steps >= 4) { // not right in the caster's face
                    Fx.particle(center, Particle.SWEEP_ATTACK, 1, 0, 0);
                }
                for (LivingEntity target : combat().nearby(player, center, 2.0)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 7);
                        status().bleed(player, target, 4);
                        Combat.knockback(target, center.clone().subtract(dir), 0.5, 0.2);
                        Fx.particle(Combat.center(target), Particle.DUST, 15, 0.3, 0, Fx.dust(DARK_RED, 1.3f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Crimson Harvest: drain every enemy within 8 blocks. */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> targets = combat().nearby(player, player.getLocation(), 8);
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        double healed = 0;
        for (LivingEntity target : targets) {
            combat().damage(player, target, 5);
            status().bleed(player, target, 4);
            healed += 3;
            Location from = Combat.center(target);
            fx().beam(from, Combat.center(player).add(0, 0.4, 0), 0xC4142E, 0.55f, 10);
            new BukkitRunnable() {
                int t;

                @Override
                public void run() {
                    if (!alive(player) || player.getWorld() != from.getWorld() || t++ >= 10) {
                        cancel();
                        return;
                    }
                    Location to = Combat.center(player);
                    Location p = from.clone().add(to.toVector().subtract(from.toVector()).multiply(t / 10.0));
                    Fx.particle(p, Particle.DUST, 3, 0.08, 0, Fx.dust(RED, 1.6f));
                    Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(DARK_RED, 1.0f));
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        double amount = Math.min(12, healed);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (alive(player)) {
                combat().heal(player, amount);
                Fx.particle(Combat.center(player), Particle.DUST, 30, 0.5, 0, Fx.dust(RED, 1.5f));
                Fx.particle(player.getLocation().add(0, 2, 0), Particle.HEART, 3, 0.4, 0);
            }
        }, 10L);
        Fx.sound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1f);
        Fx.sound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.6f);
        fx().shockwave(player.getLocation(), 0xD3122E, 8f, 10);
        fx().flash(Combat.center(player), 0xFF2E4A, 2.4f);
        return true;
    }
}
