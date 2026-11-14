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
import org.bukkit.util.Vector;

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

    /** Stormcall: a storm gathers over the enemy you aim at; lightning strikes it, then arcs to up to three more nearby. */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 32, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to call the storm down on.");
            return false;
        }
        Location feet = target.getLocation();
        // the storm cloud: a wide, flat bank of dark haze above the target, lit from within by the strike
        Location cloud = feet.clone().add(0, 9, 0);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + r.nextDouble(0.3);
            double d = i % 2 == 0 ? 2.6 : 1.5;
            fx().haze(cloud.clone().add(Math.cos(a) * d, r.nextDouble(-0.35, 0.35), Math.sin(a) * d), i % 3 == 0 ? 0x39425A : 0x262C3C,
                    (float) r.nextDouble(2.2, 3.0), 36 + r.nextInt(10));
        }
        fx().haze(cloud, 0x1C2230, 3.2f, 44);
        Fx.particle(cloud, Particle.LARGE_SMOKE, 30, 2.2, 0.01);
        Fx.particle(cloud.clone().subtract(0, 0.6, 0), Particle.CLOUD, 25, 2.4, 0.01);
        Fx.sound(cloud, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);
        // the hammer's own arc to the target, then the strike
        fx().bolt(dev.infuse.customs.ability.Fx3D.handOrigin(player), Combat.center(target), ARC, 0.25f, 4, 1);
        feet.getWorld().strikeLightningEffect(feet);
        fx().bolt(cloud, feet, ARC, 0.9f, 8, 3);
        fx().flash(cloud, 0xE8F4FF, 5f);
        fx().crack(feet, ARC, 2.8f, 40);
        fx().shockwave(feet.clone().add(0, 0.05, 0), GOLD, 3.5f, 8);
        Fx.particle(Combat.center(target), Particle.ELECTRIC_SPARK, 40, 0.5, 0.4);
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
                fx().bolt(cloud, hop.getLocation(), ARC, 0.5f, 5, 1);
                fx().flash(Combat.center(hop), ARC, 2f);
                combat().damage(player, hop, 5, true);
                Fx.particle(Combat.center(hop), Particle.ELECTRIC_SPARK, 20, 0.4, 0.3);
                Fx.sound(hop.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
            });
            struck.add(next);
            from = next;
        }
        return true;
    }

    /**
     * Bulwark Roar: you slam the hammer down and roar; runes of warding circle you and your
     * faction nearby (Resistance II, Absorption II) and enemies close by are thrown back.
     */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        fx().flash(at.clone().add(0, 1.2, 0), GOLD, 3.2f);
        fx().shockwave(at, GOLD, 6.5f, 8);
        fx().shockwave(at, ARC, 4f, 6);
        fx().crack(at, GOLD, 3f, 40);
        displays().eruptGround(at, 0.9f, 0.8f);
        Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
        Fx.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
        for (Player p : at.getNearbyPlayers(10)) {
            if (p != player && !plugin.arcs().allies(player, p)) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1, false, false, true));
            ward(p);
        }
        for (LivingEntity enemy : combat().nearby(player, at, 6)) {
            Combat.knockback(enemy, at, 1.5, 0.5);
            applyEffect(enemy, PotionEffectType.SLOWNESS, 60, 1);
            combat().damage(player, enemy, 3, true);
            fx().bolt(at.clone().add(0, 1, 0), Combat.center(enemy), ARC, 0.25f, 4, 0);
        }
        return true;
    }

    /** Three gold runes circling a player for a few seconds: the ward. */
    private void ward(Player p) {
        org.bukkit.entity.ItemDisplay[] runes = new org.bukkit.entity.ItemDisplay[3];
        for (int i = 0; i < 3; i++) {
            runes[i] = fx().sigil(p.getLocation().add(0, 1, 0), new Vector(0, 0, 1), GOLD, 0.7f, 70);
        }
        Fx.particle(p.getLocation().add(0, 1, 0), Particle.DUST, 12, 0.4, 0, Fx.dust(GOLD, 1.4f));
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (t > 60 || !p.isOnline() || p.isDead()) {
                    for (org.bukkit.entity.ItemDisplay r : runes) {
                        displays().remove(r);
                    }
                    cancel();
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.18 + i * Math.PI * 2 / 3;
                    Vector out = new Vector(Math.cos(a), 0, Math.sin(a));
                    Location spot = p.getLocation().add(out.clone().multiply(1.1)).add(0, 1.0 + Math.sin(t * 0.15 + i) * 0.15, 0);
                    spot.setDirection(out);
                    if (runes[i].isValid()) {
                        runes[i].teleport(spot);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
