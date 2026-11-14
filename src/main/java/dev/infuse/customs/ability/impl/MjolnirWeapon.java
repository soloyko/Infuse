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

    /**
     * Stormcall: a storm gathers over the enemy (or the spot) you aim at. Lightning strikes there
     * at once, and for four seconds keeps striking enemies under the cloud.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 32, 0.8);
        Location ground;
        if (target != null) {
            ground = target.getLocation();
        } else {
            Location aim = combat().aimPoint(player, 32);
            if (aim == null) {
                tell(player, "Aim at an enemy or the ground to call the storm.");
                return false;
            }
            ground = Combat.ground(aim.clone().add(0, 0.5, 0));
        }
        Location cloud = ground.clone().add(0, 9, 0);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        // the storm cloud: a wide, flat bank of dark haze, lit from within by every strike
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + r.nextDouble(0.3);
            double d = i % 2 == 0 ? 3.2 : 1.8;
            fx().haze(cloud.clone().add(Math.cos(a) * d, r.nextDouble(-0.35, 0.35), Math.sin(a) * d), i % 3 == 0 ? 0x39425A : 0x262C3C,
                    (float) r.nextDouble(2.4, 3.2), ZONE_TICKS + 10 + r.nextInt(10));
        }
        fx().haze(cloud, 0x1C2230, 3.6f, ZONE_TICKS + 20);
        fx().castCircle(ground, ARC, (float) ZONE_RADIUS);
        Fx.particle(cloud, Particle.LARGE_SMOKE, 30, 2.6, 0.01);
        Fx.sound(cloud, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
        fx().bolt(dev.infuse.customs.ability.Fx3D.handOrigin(player), target != null ? Combat.center(target) : ground.clone().add(0, 1, 0), ARC, 0.25f, 4, 1);
        strike(player, cloud, ground, target, 8);

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > ZONE_TICKS || !alive(player) || ground.getWorld() != player.getWorld()) {
                    cancel();
                    return;
                }
                if (ticks % 4 == 0) {
                    // the storm's edge, crackling
                    double a = r.nextDouble(Math.PI * 2);
                    Fx.particle(ground.clone().add(Math.cos(a) * ZONE_RADIUS, 0.3, Math.sin(a) * ZONE_RADIUS), Particle.ELECTRIC_SPARK, 4, 0.2, 0.05);
                    Fx.particle(cloud.clone().add(r.nextDouble(-2.5, 2.5), -0.6, r.nextDouble(-2.5, 2.5)), Particle.FALLING_WATER, 3, 0.4, 0);
                }
                if (ticks % 15 != 0) {
                    return;
                }
                java.util.List<LivingEntity> under = combat().nearby(player, ground, ZONE_RADIUS);
                if (under.isEmpty()) {
                    // a strike on empty ground, to show where the storm is
                    double a = r.nextDouble(Math.PI * 2);
                    double d = r.nextDouble(ZONE_RADIUS);
                    strike(player, cloud, Combat.ground(ground.clone().add(Math.cos(a) * d, 1.5, Math.sin(a) * d)), null, 0);
                } else {
                    LivingEntity hit = under.get(r.nextInt(under.size()));
                    strike(player, cloud, hit.getLocation(), hit, 5);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static final int ZONE_TICKS = 80;
    private static final double ZONE_RADIUS = 5;

    /** One bolt out of the cloud onto {@code spot}, hurting {@code victim} if there is one. */
    private void strike(Player player, Location cloud, Location spot, LivingEntity victim, double damage) {
        Location from = cloud.clone().add((Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3);
        spot.getWorld().strikeLightningEffect(spot);
        fx().bolt(from, spot, ARC, 0.8f, 7, 2);
        fx().flash(cloud, 0xE8F4FF, 4f);
        fx().crack(spot, ARC, 2.2f, 30);
        fx().shockwave(spot.clone().add(0, 0.05, 0), GOLD, 2.6f, 7);
        Fx.particle(spot.clone().add(0, 0.6, 0), Particle.ELECTRIC_SPARK, 30, 0.5, 0.3);
        Fx.sound(spot, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1.1f);
        if (victim != null && victim.isValid() && !victim.isDead()) {
            combat().damage(player, victim, damage, true);
            applyEffect(victim, PotionEffectType.SLOWNESS, 20, 2);
            fx().flash(Combat.center(victim), ARC, 2f);
        }
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
