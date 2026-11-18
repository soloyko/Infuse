package dev.bloodbound.altar;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.data.BlockKey;
import dev.bloodbound.event.EventType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

/** Ambient particles on altars during a session, and a beam during Altar Surge. */
public final class AltarEffects implements Runnable {
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(170, 0, 20), 1.3f);
    private static final Particle.DustOptions SURGE = new Particle.DustOptions(Color.fromRGB(255, 40, 60), 2.0f);

    private final BloodboundPlugin plugin;
    private int tick;

    public AltarEffects(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.sessions().isActive()) return;
        tick++;
        boolean surge = plugin.events().current().orElse(null) == EventType.ALTAR_SURGE;
        for (BlockKey key : plugin.altars().all()) {
            if (!key.isLoaded()) continue;
            Location c = key.center();
            if (c == null) continue;
            double angle = tick * 0.35;
            for (int i = 0; i < 3; i++) {
                double a = angle + i * (Math.PI * 2 / 3);
                c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.8, 0.7, Math.sin(a) * 0.8), 1, BLOOD);
            }
            c.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, c.clone().add(0, 0.7, 0), 1, 0.1, 0.1, 0.1, 0.01);
            if (surge) {
                for (double y = 1; y < 24; y += 0.75) {
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, y, 0), 1, 0.05, 0, 0.05, SURGE);
                }
                double r = plugin.settings().surgeRadius();
                for (int i = 0; i < 16; i++) {
                    double a = angle + i * (Math.PI / 8);
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, BLOOD);
                }
            }
        }
    }
}
