package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;

/**
 * Mjolnir, the Hammer of the House (Arc II faction weapon of the House of the Hammer).
 * Passive Stormborn, on-hit Thunderclap, F Stormcall, Sneak+F Bulwark Roar.
 */
public final class MjolnirWeapon extends SwordAbility {

    private static final int GOLD = 0xE0A030;
    private static final int ARC = 0xCFE8FF;

    public MjolnirWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.MJOLNIR);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Location feet = target.getLocation();
        fx().bolt(feet.clone().add(0, 10, 0), feet, ARC, 0.5f, 6, 2);
        event.setDamage(event.getDamage() + 4);
        Fx.sound(feet, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.2f);
    }

    /** Stormcall: lightning strikes the enemy you aim at, then arcs to up to three more nearby. */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 32, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to call the storm down on.");
            return false;
        }
        Location feet = target.getLocation();
        feet.getWorld().strikeLightningEffect(feet);
        fx().bolt(feet.clone().add(0, 14, 0), feet, ARC, 0.9f, 8, 3);
        fx().crack(feet, GOLD, 2.6f, 30);
        combat().damage(player, target, 8, true);
        Set<LivingEntity> struck = new HashSet<>();
        struck.add(target);
        LivingEntity from = target;
        for (int jump = 0; jump < 3; jump++) {
            LivingEntity next = null;
            for (LivingEntity near : combat().nearby(player, from.getLocation(), 6)) {
                if (!struck.contains(near)) {
                    next = near;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            LivingEntity hop = next;
            Location a = Combat.center(from);
            int delay = (jump + 1) * 3;
            displays().later(delay, () -> {
                if (!hop.isValid() || hop.isDead() || !alive(player)) {
                    return;
                }
                fx().bolt(a, Combat.center(hop), ARC, 0.4f, 6, 1);
                combat().damage(player, hop, 5, true);
                Fx.sound(hop.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
            });
            struck.add(next);
            from = next;
        }
        return true;
    }

    /** Bulwark Roar: you and your faction nearby harden; enemies close by are thrown back. */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        fx().flash(at.clone().add(0, 1.2, 0), GOLD, 3.2f);
        fx().shockwave(at, GOLD, 6f, 8);
        Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
        for (Player p : at.getNearbyPlayers(10)) {
            if (p != player && !plugin.arcs().allies(player, p)) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1, false, false, true));
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.DUST, 12, 0.4, 0, Fx.dust(GOLD, 1.4f));
        }
        for (LivingEntity enemy : combat().nearby(player, at, 6)) {
            Combat.knockback(enemy, at, 1.5, 0.5);
            applyEffect(enemy, PotionEffectType.SLOWNESS, 60, 1);
            combat().damage(player, enemy, 3, true);
        }
        return true;
    }
}
