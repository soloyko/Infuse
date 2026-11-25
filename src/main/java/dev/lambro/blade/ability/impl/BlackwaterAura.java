package dev.lambro.blade.ability.impl;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.SettingDef;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Tier 7 — black water pools around the wielder, weighing down every enemy inside. */
public final class BlackwaterAura extends Ability {

    public BlackwaterAura(LambroPlugin plugin) {
        super(plugin, "blackwater_aura", "Blackwater Aura", 7, Trigger.PASSIVE, Material.INK_SAC,
                "nearby foes are slowed & weakened",
                List.of("Black water pools at your feet.",
                        "Enemies who wade into it are slowed",
                        "and their strikes grow weak."));
        setting(SettingDef.num("radius", "Radius", Material.COMPASS, 5, 0.5, 1, 16, " blocks"));
        setting(SettingDef.whole("slow-level", "Slowness Level", Material.SOUL_SAND, 1, 1, 0, 5, ""));
        setting(SettingDef.whole("weakness-level", "Weakness Level", Material.FERMENTED_SPIDER_EYE, 1, 1, 0, 5, ""));
        setting(SettingDef.whole("show-ring", "Show Ring (1=on)", Material.ENDER_EYE, 1, 1, 0, 1, ""));
    }

    @Override
    public void onTick(Player p, long tick) {
        double radius = get("radius");
        Location feet = p.getLocation().add(0, 0.08, 0);

        if (getInt("show-ring") == 1 && tick % 4 == 0) {
            double spin = tick * 0.03;
            // two slow-orbiting arcs instead of a full ring: subtle, never cluttered
            for (int arc = 0; arc < 2; arc++) {
                for (int i = 0; i < 10; i++) {
                    double a = spin + arc * Math.PI + i * 0.09;
                    Location l = feet.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                    Fx.dust(l, i % 3 == 0 ? Fx.BLUE : Fx.VOID, i % 3 == 0 ? 0.8f : 1.2f);
                }
            }
        }

        if (tick % 10 != 0) return;
        int slow = getInt("slow-level");
        int weak = getInt("weakness-level");
        for (LivingEntity e : plugin.abilities().targets(p, p.getLocation(), radius)) {
            if (slow > 0) e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, slow - 1, true, false, true));
            if (weak > 0) e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, weak - 1, true, false, true));
            e.getWorld().spawnParticle(Particle.SQUID_INK, e.getLocation().add(0, 0.2, 0), 2, 0.25, 0.05, 0.25, 0.01);
        }
    }
}
