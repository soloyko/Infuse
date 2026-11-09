package me.infuse.util;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import me.infuse.effect.Equipped;
import me.infuse.effect.ItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

/** Shared particle/sound/title effects so every effect feels consistent and colourful. */
public final class Fx {
    private static InfusePlugin plugin;
    private static NamespacedKey fireworkKey;

    private Fx() {}

    public static void init(InfusePlugin p) {
        plugin = p;
        fireworkKey = new NamespacedKey(p, "fx_firework");
    }

    public static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    public static Particle.DustTransition fade(int from, int to, float size) {
        return new Particle.DustTransition(Color.fromRGB(from), Color.fromRGB(to), size);
    }

    /** Lighter version of a colour, for gradients. */
    public static int lighten(int rgb, double amount) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    public static void ring(Location center, int rgb, double radius, int points, float size) {
        World w = center.getWorld();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, dust(rgb, size));
        }
    }

    /** A ring that expands outward over a few ticks. */
    public static void shockwave(Location center, int rgb, double maxRadius) {
        Location c = center.clone().add(0, 0.15, 0);
        new BukkitRunnable() {
            double r = 0.4;

            @Override
            public void run() {
                if (r > maxRadius) {
                    cancel();
                    return;
                }
                int points = (int) Math.max(16, r * 14);
                World w = c.getWorld();
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r),
                            1, 0, 0.05, 0, 0, fade(lighten(rgb, 0.5), rgb, 1.3f));
                }
                r += 0.6;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Twin helix rising around an entity. */
    public static void helix(Entity target, int rgb, int ticks, double radius) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks || !target.isValid()) {
                    cancel();
                    return;
                }
                Location base = target.getLocation();
                double y = (t % 20) / 20.0 * 2.2;
                for (int strand = 0; strand < 2; strand++) {
                    double a = t * 0.45 + strand * Math.PI;
                    base.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius),
                            2, 0.02, 0.02, 0.02, 0, dust(strand == 0 ? rgb : lighten(rgb, 0.55), 1.2f));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void burst(Location loc, int rgb, int count, double spread) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0, dust(rgb, 1.5f));
        w.spawnParticle(Particle.DUST, loc, count / 2, spread, spread, spread, 0, dust(lighten(rgb, 0.6), 1.0f));
    }

    /** A harmless firework burst in the effect's colours. */
    public static void firework(Location loc, int rgb, boolean big) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(big ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST)
                    .withColor(Color.fromRGB(rgb), Color.fromRGB(lighten(rgb, 0.5)))
                    .withFade(Color.WHITE)
                    .trail(true).flicker(big).build());
            f.setFireworkMeta(meta);
            f.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
        });
        fw.detonate();
    }

    public static boolean isFxFirework(Entity e) {
        return e instanceof Firework && e.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE);
    }

    /** A straight particle line between two points (lightning arcs, teleport paths). */
    public static void line(Location from, Location to, int rgb, boolean sparks) {
        World w = from.getWorld();
        if (w == null || !w.equals(to.getWorld())) return;
        org.bukkit.util.Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        if (len < 0.01) return;
        int points = (int) Math.min(120, len * 4);
        step.multiply(1.0 / points);
        Location cur = from.clone();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i <= points; i++) {
            Location at = sparks ? cur.clone().add(r.nextDouble(-0.12, 0.12), r.nextDouble(-0.12, 0.12), r.nextDouble(-0.12, 0.12)) : cur;
            w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust(rgb, 1.0f));
            if (sparks && i % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0, 0, 0, 0);
            cur.add(step);
        }
    }

    /** Coloured trail following an entity for a few ticks (dashes, projectiles). */
    public static void trail(Entity target, int rgb, int ticks, Particle extra) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= ticks || !target.isValid()) {
                    cancel();
                    return;
                }
                Location l = target.getLocation().add(0, target.getHeight() / 2, 0);
                target.getWorld().spawnParticle(Particle.DUST, l, 3, 0.15, 0.15, 0.15, 0, dust(rgb, 1.2f));
                if (extra != null) target.getWorld().spawnParticle(extra, l, 1, 0.05, 0.05, 0.05, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ moments

    public static void equip(Player p, Equipped e, boolean primary) {
        int rgb = e.type().rgb();
        helix(p, rgb, 30, 0.8);
        shockwave(p.getLocation(), rgb, 2.5);
        if (e.augmented()) {
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
        var effect = Msg.comp("effect", ItemFactory.name(e.type(), e.augmented()));
        var slot = Msg.comp("slot", Msg.get(primary ? "slot-primary" : "slot-secondary"));
        p.showTitle(Title.title(Msg.get("equip-title", effect, slot), Msg.get("equip-subtitle", effect, slot),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(500))));
    }

    public static void spark(Player p, EffectType type) {
        int rgb = type.rgb();
        shockwave(p.getLocation(), rgb, 3.5);
        p.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, p.getLocation().add(0, 1, 0), 30, 0.35, 0.7, 0.35, 0,
                fade(rgb, lighten(rgb, 0.7), 1.4f));
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.08);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.6f);
    }

    public static void sparkFail(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
    }

    public static void sparkReady(Player p, EffectType type) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.8f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 2.2, 0), 8, 0.25, 0.1, 0.25, 0, dust(type.rgb(), 1f));
    }

    public static void crafted(Location stand, EffectType type) {
        Location c = stand.getBlock().getLocation().add(0.5, 1.1, 0.5);
        burst(c, type.rgb(), 40, 0.4);
        shockwave(stand.getBlock().getLocation().add(0.5, 0, 0.5), type.rgb(), 3);
        c.getWorld().playSound(c, Sound.BLOCK_BREWING_STAND_BREW, 1f, 1f);
        c.getWorld().playSound(c, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.4f);
    }

    public static void effectLost(Location loc, EffectType type) {
        burst(loc.clone().add(0, 1, 0), type.rgb(), 50, 0.6);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
    }

    public static void vanish(Player p, boolean in) {
        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 40, 0.35, 0.8, 0.35, 0.02);
        p.getWorld().spawnParticle(Particle.DUST, l, 30, 0.4, 0.8, 0.4, 0, dust(EffectType.THIEF.rgb(), 1.2f));
        p.getWorld().playSound(l, in ? Sound.ENTITY_ILLUSIONER_MIRROR_MOVE : Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS,
                1f, in ? 0.8f : 1.4f);
    }
}
