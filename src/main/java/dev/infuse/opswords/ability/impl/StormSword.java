package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Storm Sword: speed and lightning.
 * Passive Tailwind (lightning immunity in CombatListener), on-hit Static Charge, RC Thunder Strike,
 * Sneak+RC Chain Lightning. Lightning here is visual only: no fires, no charged creepers.
 */
public final class StormSword extends SwordAbility {

    private static final int SPARK_BLUE = 0x9CC8FF;

    public StormSword(OPSwordsPlugin plugin) {
        super(plugin, SwordType.STORM);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.MOVEMENT_SPEED, "speed", 0.25, AttributeModifier.Operation.ADD_SCALAR);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.12)) {
            Fx.particle(Combat.center(target), Particle.ELECTRIC_SPARK, 6, 0.3, 0.1);
            return;
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
        event.setDamage(event.getDamage() + 5);
        applyEffect(target, PotionEffectType.SLOWNESS, 20, 3);
        Fx.particle(Combat.center(target), Particle.ELECTRIC_SPARK, 40, 0.4, 0.3);
    }

    /** Thunder Strike: lightning wherever you aim (up to 40 blocks). */
    @Override
    public boolean primary(Player player) {
        Location point = combat().aimPoint(player, 40);
        if (point == null) {
            tell(player, "Nothing in range to strike.");
            return false;
        }
        strike(player, point, 3.5, 9);
        return true;
    }

    private void strike(Player player, Location point, double radius, double damage) {
        point.getWorld().strikeLightningEffect(point);
        Fx.particle(point.clone().add(0, 0.5, 0), Particle.FLASH, 1, 0, 0, Color.fromRGB(0xDDEEFF));
        Fx.particle(point.clone().add(0, 0.5, 0), Particle.ELECTRIC_SPARK, 60, 1.0, 0.4);
        Fx.sound(point, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2f, 1f);
        for (LivingEntity target : combat().nearby(player, point, radius)) {
            combat().damage(player, target, damage);
            Combat.knockback(target, point, 0.6, 0.5);
            Fx.particle(Combat.center(target), Particle.ELECTRIC_SPARK, 15, 0.3, 0.2);
        }
    }

    /** Chain Lightning: arcs from you through up to 6 enemies. */
    @Override
    public boolean secondary(Player player) {
        LivingEntity first = null;
        for (LivingEntity candidate : combat().nearby(player, player.getLocation(), 14)) {
            if (player.hasLineOfSight(candidate)) {
                first = candidate;
                break;
            }
        }
        if (first == null) {
            tell(player, "No enemies in range.");
            return false;
        }
        first.getWorld().strikeLightningEffect(first.getLocation());
        LivingEntity start = first;
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            Location from = player.getEyeLocation().subtract(0, 0.3, 0);
            LivingEntity current = start;
            int hops;

            @Override
            public void run() {
                if (current == null || !alive(player) || hops >= 6 || current.getWorld() != player.getWorld()) {
                    cancel();
                    return;
                }
                hops++;
                hit.add(current.getUniqueId());
                Location to = Combat.center(current);
                Fx.arc(from, to, p -> {
                    Fx.particle(p, Particle.ELECTRIC_SPARK, 1, 0.02, 0);
                    Fx.particle(p, Particle.DUST, 1, 0.02, 0, Fx.dust(SPARK_BLUE, 1.4f));
                    Fx.particle(p, Particle.END_ROD, 1, 0.01, 0);
                });
                Fx.particle(to, Particle.ELECTRIC_SPARK, 25, 0.35, 0.25);
                Fx.particle(to, Particle.FLASH, 1, 0, 0, Color.fromRGB(0xBFE3FF));
                Fx.sound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.2f + hops * 0.1f);
                combat().damage(player, current, 7);
                applyEffect(current, PotionEffectType.SLOWNESS, 30, 2);

                LivingEntity next = null;
                for (LivingEntity candidate : combat().nearby(player, to, 8)) {
                    if (!hit.contains(candidate.getUniqueId()) && current.hasLineOfSight(candidate)) {
                        next = candidate;
                        break;
                    }
                }
                from = to;
                current = next;
            }
        }.runTaskTimer(plugin, 0L, 3L);
        return true;
    }
}
