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

import java.util.Comparator;
import java.util.List;

/** Tier 8 (ultimate) — a black eclipse opens overhead and rains blue lightning on your enemies. */
public final class EclipseTide extends Ability {

    public EclipseTide(LambroPlugin plugin) {
        super(plugin, "eclipse_tide", "Eclipse Tide", 8, Trigger.SNEAK_SWAP, Material.NETHER_STAR,
                "ultimate: an eclipse of blue lightning",
                List.of("Open a black eclipse above you.",
                        "For its duration, arcs of blue lightning",
                        "lash out at the nearest enemies again and again."));
        setting(SettingDef.num("cooldown", "Cooldown", Material.CLOCK, 90, 5, 0, 900, "s"));
        setting(SettingDef.num("duration", "Duration", Material.HONEYCOMB, 6, 0.5, 1, 30, "s"));
        setting(SettingDef.num("radius", "Strike Radius", Material.COMPASS, 9, 0.5, 2, 24, " blocks"));
        setting(SettingDef.num("damage", "Strike Damage", Material.IRON_SWORD, 4.5, 0.5, 0, 40, "hp"));
        setting(SettingDef.whole("interval", "Strike Interval", Material.REPEATER, 10, 1, 2, 60, " ticks"));
        setting(SettingDef.whole("max-targets", "Targets Per Strike", Material.TARGET, 3, 1, 1, 12, ""));
    }

    @Override
    public boolean activate(Player p) {
        int duration = (int) (get("duration") * 20);
        double radius = get("radius");
        double damage = get("damage");
        int interval = getInt("interval");
        int maxTargets = getInt("max-targets");

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 0.8f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                Location eye = p.getLocation().add(0, 3.2, 0);
                double open = Math.min(1, t / 10.0);               // eclipse opens over half a second
                double fade = t > duration - 10 ? (duration - t) / 10.0 : 1;
                double size = 1.4 * open * Math.max(0, fade);

                // black core + blue corona
                Fx.dust(eye, Fx.VOID, 2.6f, (int) (6 * size) + 1, size * 0.25);
                Fx.ring(eye, size, 20, t * 0.15, l -> Fx.fade(l, Fx.ICE, Fx.BLUE, 1.1f, 1, 0));
                Fx.ring(eye, size * 1.35, 14, -t * 0.1, l -> Fx.dust(l, Fx.ROYAL, 0.7f));
                // spiral feeding down to the wielder
                if (t % 2 == 0) {
                    for (int arm = 0; arm < 3; arm++) {
                        double prog = (t % 20) / 20.0;
                        double ang = prog * Math.PI * 4 + arm * Math.PI * 2 / 3;
                        double r = 0.9 * (1 - prog) + 0.3;
                        Location l = p.getLocation().add(Math.cos(ang) * r, 3.0 - prog * 2.8, Math.sin(ang) * r);
                        Fx.dust(l, arm == 0 ? Fx.CYAN : Fx.VOID, 1.0f);
                    }
                }
                if (t % 6 == 0) Fx.ring(p.getLocation().add(0, 0.1, 0), radius, (int) (radius * 5), t * 0.05,
                        l -> Fx.dust(l, Fx.DEEP, 1.0f));

                if (t > 8 && t % interval == 0) strike(p, eye, radius, damage, maxTargets);

                if (++t >= duration) {
                    cancel();
                    Location c = p.getLocation().add(0, 1, 0);
                    p.getWorld().spawnParticle(Particle.SQUID_INK, eye, 40, 0.3, 0.3, 0.3, 0.2);
                    p.getWorld().spawnParticle(Particle.END_ROD, c, 30, 0.4, 0.6, 0.4, 0.15);
                    p.getWorld().playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.7f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void strike(Player p, Location from, double radius, double damage, int max) {
        List<LivingEntity> near = plugin.abilities().targets(p, p.getLocation(), radius);
        near.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
        int n = 0;
        for (LivingEntity e : near) {
            if (n++ >= max) break;
            Location hit = e.getLocation().add(0, e.getHeight() * 0.6, 0);
            Fx.arc(from, hit, Fx.ICE, Fx.BLUE);
            plugin.abilities().damage(p, e, damage);
            e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 12, 0, false, false, false));
            Fx.fade(hit, Fx.ICE, Fx.VOID, 1.6f, 10, 0.35);
            e.getWorld().playSound(hit, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.7f);
        }
        if (n > 0) p.getWorld().playSound(from, Sound.ITEM_TRIDENT_THUNDER, 0.25f, 1.9f);
    }
}
