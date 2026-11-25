package dev.lambro.blade.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Particle helpers for the blue/black visual language of the blade. */
public final class Fx {

    public static final Color ICE = Color.fromRGB(0x9FE8FF);
    public static final Color CYAN = Color.fromRGB(0x5FCBFF);
    public static final Color BLUE = Color.fromRGB(0x2F7BFF);
    public static final Color ROYAL = Color.fromRGB(0x1F4FD8);
    public static final Color DEEP = Color.fromRGB(0x16245C);
    public static final Color VOID = Color.fromRGB(0x07080F);

    private Fx() {}

    public static void dust(Location l, Color c, float size, int count, double spread) {
        l.getWorld().spawnParticle(Particle.DUST, l, count, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    public static void dust(Location l, Color c, float size) {
        dust(l, c, size, 1, 0);
    }

    public static void fade(Location l, Color from, Color to, float size, int count, double spread) {
        l.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, l, count, spread, spread, spread, 0,
                new Particle.DustTransition(from, to, size));
    }

    public static void simple(Location l, Particle p, int count, double spread, double speed) {
        l.getWorld().spawnParticle(p, l, count, spread, spread, spread, speed);
    }

    /** Walks a straight line calling {@code at} every {@code step} blocks. */
    public static void line(Location from, Location to, double step, Consumer<Location> at) {
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len < 1e-6) return;
        dir.normalize().multiply(step);
        Location cur = from.clone();
        for (double d = 0; d <= len; d += step) {
            at.accept(cur);
            cur.add(dir);
        }
    }

    /** Horizontal ring. */
    public static void ring(Location center, double radius, int points, double angleOffset, Consumer<Location> at) {
        for (int i = 0; i < points; i++) {
            double a = angleOffset + (Math.PI * 2 * i / points);
            at.accept(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
    }

    /** Evenly distributed points on a sphere (fibonacci lattice). */
    public static void sphere(Location center, double radius, int points, Consumer<Location> at) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i;
            at.accept(center.clone().add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius));
        }
    }

    /** Jagged lightning arc made of dust, from a to b. */
    public static void arc(Location a, Location b, Color core, Color glow) {
        World w = a.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector total = b.toVector().subtract(a.toVector());
        int segments = Math.max(4, (int) (total.length() * 1.6));
        Location prev = a.clone();
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Location next = a.clone().add(total.clone().multiply(t));
            if (i != segments) {
                double jitter = 0.45 * Math.sin(Math.PI * t);
                next.add(r.nextDouble(-jitter, jitter), r.nextDouble(-jitter, jitter), r.nextDouble(-jitter, jitter));
            }
            line(prev, next, 0.18, l -> {
                dust(l, core, 0.9f);
                if (ThreadLocalRandom.current().nextInt(3) == 0) dust(l, glow, 1.5f);
            });
            prev = next;
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, b, 12, 0.25, 0.25, 0.25, 0.25);
    }
}
