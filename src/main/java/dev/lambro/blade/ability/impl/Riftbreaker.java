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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Tier 4 — tear the water open: a whirlpool drags foes in, then the rift detonates. */
public final class Riftbreaker extends Ability {

    public Riftbreaker(LambroPlugin plugin) {
        super(plugin, "riftbreaker", "Riftbreaker", 4, Trigger.SNEAK_RIGHT_CLICK, Material.ECHO_SHARD,
                "pull foes in, then detonate",
                List.of("Plunge the blade into the ground.",
                        "A whirlpool drags nearby foes inward",
                        "before the rift erupts and hurls them skyward."));
        setting(SettingDef.num("cooldown", "Cooldown", Material.CLOCK, 18, 0.5, 0, 300, "s"));
        setting(SettingDef.num("radius", "Radius", Material.COMPASS, 6, 0.5, 2, 16, " blocks"));
        setting(SettingDef.num("damage", "Detonation Damage", Material.IRON_SWORD, 7, 0.5, 0, 50, "hp"));
        setting(SettingDef.num("pull", "Pull Strength", Material.FISHING_ROD, 0.35, 0.05, 0, 1.5, "x"));
        setting(SettingDef.num("launch", "Launch Height", Material.SLIME_BALL, 1.0, 0.1, 0, 3, "x"));
    }

    @Override
    public boolean activate(Player p) {
        Location center = p.getLocation().clone();
        double radius = get("radius");
        double pull = get("pull");
        double damage = get("damage");
        double launch = get("launch");

        p.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1.3f);
        p.getWorld().playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 0.6f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (t < 14) {
                    // contracting whirlpool
                    double r = radius * (1 - t / 16.0);
                    double spin = t * 0.5;
                    Location ground = center.clone().add(0, 0.15, 0);
                    Fx.ring(ground, r, (int) (r * 10), spin, l -> Fx.fade(l, Fx.CYAN, Fx.VOID, 1.2f, 1, 0));
                    Fx.ring(ground, r * 0.6, (int) (r * 6), -spin, l -> Fx.dust(l, Fx.VOID, 1.6f));
                    for (LivingEntity e : plugin.abilities().targets(p, center, radius)) {
                        Vector in = center.toVector().subtract(e.getLocation().toVector()).setY(0);
                        if (in.lengthSquared() < 0.5) continue;
                        Vector tangent = new Vector(-in.getZ(), 0, in.getX()).normalize().multiply(pull * 0.4);
                        e.setVelocity(in.normalize().multiply(pull).add(tangent).setY(Math.min(e.getVelocity().getY(), 0.05)));
                    }
                    if (t % 4 == 0) p.getWorld().playSound(center, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.8f, 0.7f);
                } else if (t == 14) {
                    detonate(p, center, radius, damage, launch);
                } else if (t < 22) {
                    // expanding shockwave
                    double r = radius * ((t - 14) / 7.0);
                    Fx.ring(center.clone().add(0, 0.3, 0), r, (int) (r * 12) + 8, 0, l -> {
                        Fx.fade(l, Fx.ICE, Fx.DEEP, 1.5f, 1, 0);
                        if (Math.random() < 0.25) Fx.dust(l, Fx.VOID, 2f);
                    });
                } else {
                    cancel();
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void detonate(Player p, Location center, double radius, double damage, double launch) {
        Location mid = center.clone().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.SONIC_BOOM, mid, 1);
        p.getWorld().spawnParticle(Particle.SQUID_INK, mid, 60, radius * 0.25, 0.4, radius * 0.25, 0.15);
        p.getWorld().spawnParticle(Particle.END_ROD, mid, 25, 0.3, 0.3, 0.3, 0.3);
        p.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.4f);
        p.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
        for (LivingEntity e : plugin.abilities().targets(p, center, radius)) {
            plugin.abilities().damage(p, e, damage);
            Vector out = e.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (out.lengthSquared() > 0.01) out.normalize().multiply(0.35);
            e.setVelocity(out.setY(launch));
        }
    }
}
