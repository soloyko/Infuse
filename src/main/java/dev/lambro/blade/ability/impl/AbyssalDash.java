package dev.lambro.blade.ability.impl;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.SettingDef;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tier 2 — phase forward through a corridor of black water, cutting everything you pass. */
public final class AbyssalDash extends Ability {

    public AbyssalDash(LambroPlugin plugin) {
        super(plugin, "abyssal_dash", "Abyssal Dash", 2, Trigger.RIGHT_CLICK, Material.PHANTOM_MEMBRANE,
                "dash through foes, blinding them",
                List.of("Surge forward on a black current.",
                        "Every enemy you pass through is cut",
                        "and left blind in the dark."));
        setting(SettingDef.num("cooldown", "Cooldown", Material.CLOCK, 10, 0.5, 0, 300, "s"));
        setting(SettingDef.num("power", "Dash Power", Material.FEATHER, 1.9, 0.1, 0.5, 4, "x"));
        setting(SettingDef.num("damage", "Pass-Through Damage", Material.IRON_SWORD, 5, 0.5, 0, 40, "hp"));
        setting(SettingDef.num("blind-seconds", "Blind Duration", Material.INK_SAC, 1.5, 0.5, 0, 10, "s"));
    }

    @Override
    public boolean activate(Player p) {
        Vector dir = p.getEyeLocation().getDirection().normalize();
        double power = get("power");
        Vector vel = dir.clone().multiply(power);
        vel.setY(Math.max(-0.4, Math.min(0.7, dir.getY() * power * 0.6)) + 0.18);
        p.setVelocity(vel);
        p.setFallDistance(0);
        plugin.abilities().protectFromFall(p, 60);

        Location start = p.getLocation();
        p.getWorld().playSound(start, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 0.7f);
        p.getWorld().playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.7f, 1.5f);
        p.getWorld().spawnParticle(Particle.SQUID_INK, start.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.06);
        Fx.ring(start.clone().add(0, 0.1, 0), 1.2, 24, 0, l -> Fx.fade(l, Fx.CYAN, Fx.VOID, 1.2f, 1, 0));

        Set<UUID> hit = new HashSet<>();
        double damage = get("damage");
        int blind = (int) (get("blind-seconds") * 20);

        new BukkitRunnable() {
            int t = 0;
            Location last = p.getLocation().add(0, 1, 0);
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t++ > 9) { cancel(); return; }
                Location now = p.getLocation().add(0, 1, 0);
                // afterimage trail
                Fx.line(last, now, 0.3, l -> {
                    Fx.dust(l, Fx.VOID, 1.8f, 1, 0.15);
                    Fx.dust(l, Fx.BLUE, 1.0f, 1, 0.25);
                });
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, now, 2, 0.2, 0.3, 0.2, 0.01);
                last = now;

                for (LivingEntity e : plugin.abilities().targets(p, now, 1.8)) {
                    if (!hit.add(e.getUniqueId())) continue;
                    plugin.abilities().damage(p, e, damage);
                    if (blind > 0) e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blind, 0, false, false, true));
                    Vector side = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (side.lengthSquared() > 0.01) e.setVelocity(side.normalize().multiply(0.5).setY(0.25));
                    Location el = e.getLocation().add(0, e.getHeight() / 2, 0);
                    e.getWorld().spawnParticle(Particle.SWEEP_ATTACK, el, 1);
                    Fx.dust(el, Fx.CYAN, 1.3f, 8, 0.3);
                    e.getWorld().playSound(el, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
