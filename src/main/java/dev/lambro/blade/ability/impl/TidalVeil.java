package dev.lambro.blade.ability.impl;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.AbilityManager;
import dev.lambro.blade.ability.SettingDef;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Fx;
import dev.lambro.blade.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/** Tier 3 — at low health a sphere of deep water closes around you. */
public final class TidalVeil extends Ability {

    public TidalVeil(LambroPlugin plugin) {
        super(plugin, "tidal_veil", "Tidal Veil", 3, Trigger.PASSIVE, Material.NAUTILUS_SHELL,
                "low health summons a water shield",
                List.of("When you fall to low health, the tide",
                        "wraps you in a shell of absorption",
                        "and carries you away with a burst of speed."));
        setting(SettingDef.num("cooldown", "Cooldown", Material.CLOCK, 40, 1, 0, 600, "s"));
        setting(SettingDef.num("threshold", "Health Threshold", Material.REDSTONE, 40, 5, 5, 95, "%"));
        setting(SettingDef.whole("absorption-level", "Absorption Level", Material.GOLDEN_APPLE, 2, 1, 1, 10, ""));
        setting(SettingDef.num("duration", "Shield Duration", Material.HONEYCOMB, 6, 0.5, 1, 30, "s"));
        setting(SettingDef.whole("speed-level", "Speed Level", Material.SUGAR, 2, 1, 0, 5, ""));
    }

    @Override
    public void onDamaged(Player p, EntityDamageEvent e) {
        double after = p.getHealth() - e.getFinalDamage(); // final damage already excludes absorption
        if (after <= 0) return;
        if (after > AbilityManager.maxHealth(p) * get("threshold") / 100.0) return;
        if (!plugin.abilities().ready(p, this)) return;
        plugin.abilities().startCooldown(p, this);

        int ticks = (int) (get("duration") * 20);
        // Apply next tick so this hit doesn't eat the fresh shield.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, getInt("absorption-level") - 1, false, false, true));
            if (getInt("speed-level") > 0)
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Math.min(ticks, 80), getInt("speed-level") - 1, false, false, true));
            p.sendActionBar(Text.mm("<" + Text.CYAN + ">◆ <" + Text.WHITE + ">" + Text.tc("tidal veil") + " <" + Text.SHADOW + ">· <" + Text.STEEL + ">" + Text.tc("the tide protects you")));
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1f, 1.4f);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 0.6f);
        });

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t++ > 14) { cancel(); return; }
                Location c = p.getLocation().add(0, 1, 0);
                double r = t < 6 ? 2.6 - t * 0.25 : 1.15;          // collapse in, then hold
                Fx.sphere(c, r, t < 6 ? 60 : 34, l -> Fx.fade(l, Fx.ICE, Fx.ROYAL, 0.9f, 1, 0));
                if (t % 3 == 0) p.getWorld().spawnParticle(Particle.GLOW, c, 6, 0.6, 0.6, 0.6, 0.02);
                if (t == 6) p.getWorld().spawnParticle(Particle.BUBBLE_POP, c, 40, 0.8, 0.8, 0.8, 0.1);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
