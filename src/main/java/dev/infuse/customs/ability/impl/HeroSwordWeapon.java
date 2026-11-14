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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Hero Sword, the Blade of the Order (Arc II faction weapon of the Order of the Blade).
 * Passive Vanguard, on-hit Shieldbreaker, F Heroic Charge, Sneak+F Rally.
 */
public final class HeroSwordWeapon extends SwordAbility {

    private static final int BLUE = 0x4DA3FF;
    private static final int GOLD = 0xF2C94C;

    public HeroSwordWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.HEROSWORD);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        applyEffect(target, PotionEffectType.WEAKNESS, 60, 0);
        applyEffect(target, PotionEffectType.SLOWNESS, 60, 1);
        Location at = Combat.center(target);
        fx().burst(at, GOLD, 1.6f);
        Fx.sound(at, Sound.ITEM_SHIELD_BREAK, 1f, 1f);
    }

    /** Heroic Charge: a short charge; the first enemy in the way is bowled over and stunned. */
    @Override
    public boolean primary(Player player) {
        Vector dir = dev.infuse.customs.ability.Aim.flatLook(player);
        status().protectFromFall(player, 40);
        Fx.sound(player.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1.2f, 0.8f);
        Fx.sound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 10) {
                    cancel();
                    return;
                }
                Combat.setVelocity(player, dir.clone().multiply(1.25).setY(Math.min(0.1, player.getVelocity().getY())));
                Location chest = player.getLocation().add(0, 1, 0);
                Fx.particle(chest, Particle.DUST, 4, 0.3, 0, Fx.dust(ticks % 2 == 0 ? BLUE : GOLD, 1.3f));
                if (ticks % 2 == 0) {
                    fx().afterimage(chest, plugin.items().create(type), dir, BLUE, 1.0f, 6);
                }
                for (LivingEntity target : combat().nearby(player, chest.clone().add(dir.clone().multiply(0.8)), 1.6)) {
                    combat().damage(player, target, 6, true);
                    Combat.setVelocity(target, dir.clone().multiply(1.4).setY(0.5));
                    hold(target, 30);
                    applyEffect(target, PotionEffectType.SLOWNESS, 40, 4);
                    Location at = Combat.center(target);
                    fx().burst(at, GOLD, 2.2f);
                    fx().cut(at, dir, Math.PI / 2, 0.4, BLUE, 1.8f, 2);
                    Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
                    Fx.sound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
                    Combat.setVelocity(player, new Vector());
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Rally: you and your faction nearby gain Strength, Speed and a quick heal. */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        fx().beam(at.clone().add(0, 12, 0), at, GOLD, 1.0f, 14);
        fx().flash(at.clone().add(0, 1.4, 0), BLUE, 3f);
        fx().castCircle(at, BLUE, 4f);
        Fx.sound(at, Sound.EVENT_RAID_HORN, 1f, 1.4f);
        Fx.sound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        for (Player p : at.getNearbyPlayers(12)) {
            if (p != player && !plugin.arcs().allies(player, p)) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, false, false, true));
            combat().heal(p, 4);
            fx().beam(at.clone().add(0, 1.4, 0), p.getLocation().add(0, 1, 0), GOLD, 0.15f, 8);
            Fx.particle(p.getLocation().add(0, 2, 0), Particle.HEART, 2, 0.3, 0);
        }
        for (LivingEntity enemy : combat().nearby(player, at, 5)) {
            Combat.knockback(enemy, at, 0.9, 0.3);
        }
        return true;
    }
}
