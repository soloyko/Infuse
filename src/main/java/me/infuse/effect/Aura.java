package me.infuse.effect;

import me.infuse.data.PlayerData;
import me.infuse.util.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;

import java.util.concurrent.ThreadLocalRandom;

/** Ambient particles: a soft aura for equipped effects and a signature look while a spark is active. */
final class Aura {
    private Aura() {}

    static void tick(Player p, PlayerData d, long tick, FileConfiguration cfg) {
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        World w = p.getWorld();
        Location feet = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // Invisible or vanished players shouldn't give themselves away
        boolean hidden = p.isInvisible() || d.hasPassive(EffectType.INVISIBILITY) || d.isSparkActive(EffectType.THIEF)
                || p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);

        // Passive aura: a few motes at the feet, an orbiting wisp when augmented
        if (!hidden && cfg.getBoolean("visuals.passive-aura", true) && tick % 10 == 0) {
            for (int slot = 0; slot < 2; slot++) {
                Equipped e = d.get(slot);
                if (e == null || !e.type().enabled() || e.type() == EffectType.THIEF) continue;
                double a = r.nextDouble(Math.PI * 2);
                w.spawnParticle(Particle.DUST, feet.clone().add(Math.cos(a) * 0.45, 0.1 + r.nextDouble(0.3), Math.sin(a) * 0.45),
                        1, 0, 0, 0, 0, Fx.dust(e.type().rgb(), 0.8f));
            }
        }
        if (!hidden && cfg.getBoolean("visuals.augmented-orbit", true) && tick % 2 == 0) {
            for (int slot = 0; slot < 2; slot++) {
                Equipped e = d.get(slot);
                if (e == null || !e.augmented() || e.type() == EffectType.THIEF) continue;
                double a = tick * 0.18 + slot * Math.PI;
                w.spawnParticle(Particle.DUST, feet.clone().add(Math.cos(a) * 0.7, 1.0 + Math.sin(tick * 0.09 + slot) * 0.35, Math.sin(a) * 0.7),
                        1, 0, 0, 0, 0, Fx.dust(Fx.lighten(e.type().rgb(), 0.35), 0.9f));
            }
        }

        // Spark auras
        if (!cfg.getBoolean("visuals.spark-aura", true) || tick % 4 != 0) return;
        Location body = feet.clone().add(0, 1, 0);
        if (d.isSparkActive(EffectType.STRENGTH)) {
            w.spawnParticle(Particle.CRIMSON_SPORE, body, 6, 0.35, 0.6, 0.35, 0);
            w.spawnParticle(Particle.DUST, body, 3, 0.35, 0.6, 0.35, 0, Fx.dust(EffectType.STRENGTH.rgb(), 1.2f));
        }
        if (d.isSparkActive(EffectType.HASTE)) {
            w.spawnParticle(Particle.WAX_OFF, body, 3, 0.4, 0.6, 0.4, 0.5);
            w.spawnParticle(Particle.ELECTRIC_SPARK, p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6)).subtract(0, 0.4, 0),
                    2, 0.15, 0.15, 0.15, 0.05);
        }
        if (d.isSparkActive(EffectType.FROST)) {
            double a = tick * 0.3;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                w.spawnParticle(Particle.SNOWFLAKE, feet.clone().add(Math.cos(ang) * 0.9, 0.3 + (tick % 20) / 10.0, Math.sin(ang) * 0.9),
                        1, 0, 0, 0, 0);
            }
        }
        if (d.isSparkActive(EffectType.REGENERATION) && tick % 12 == 0) {
            w.spawnParticle(Particle.HEART, feet.clone().add(0, 2.2, 0), 1, 0.3, 0.1, 0.3, 0);
        }
        if (d.isSparkActive(EffectType.REGENERATION)) {
            w.spawnParticle(Particle.DUST, body, 2, 0.4, 0.6, 0.4, 0, Fx.dust(EffectType.REGENERATION.rgb(), 1f));
        }
        if (d.isSparkActive(EffectType.EMERALD)) {
            w.spawnParticle(Particle.HAPPY_VILLAGER, body, 2, 0.4, 0.6, 0.4, 0);
        }
        if (d.isSparkActive(EffectType.ENDER)) {
            w.spawnParticle(Particle.PORTAL, body, 12, 0.3, 0.6, 0.3, 0.6);
            w.spawnParticle(Particle.DUST, body, 2, 0.3, 0.6, 0.3, 0, Fx.dust(EffectType.ENDER.rgb(), 1.1f));
        }
        if (d.isSparkActive(EffectType.APOPHIS)) {
            w.spawnParticle(Particle.WITCH, body, 4, 0.35, 0.6, 0.35, 0);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, feet.clone().add(0, 0.1, 0), 2, 0.4, 0, 0.4, 0.01);
        }
        if (d.featherSlamUntil() > System.currentTimeMillis()) {
            w.spawnParticle(Particle.CLOUD, feet, 2, 0.25, 0, 0.25, 0.01);
            w.spawnParticle(Particle.DUST, feet, 2, 0.3, 0.1, 0.3, 0, Fx.dust(EffectType.FEATHER.rgb(), 1f));
        }
        if (d.isSparkActive(EffectType.THIEF)) {
            // Only the thief sees their own shadow trail
            p.spawnParticle(Particle.SMOKE, feet.clone().add(0, 0.2, 0), 4, 0.25, 0.1, 0.25, 0.01);
            p.spawnParticle(Particle.DUST, body, 2, 0.3, 0.6, 0.3, 0, Fx.dust(EffectType.THIEF.rgb(), 0.9f));
        }
    }
}
