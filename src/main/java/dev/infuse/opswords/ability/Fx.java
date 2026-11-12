package dev.infuse.opswords.ability;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Particle and sound helpers. */
public final class Fx {

    private Fx() {
    }

    public static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    /** Particles closer than this to a player's eyes are not sent to that player (they'd fill the screen). */
    private static final double FACE_CLEARANCE_SQ = 1.6 * 1.6;
    private static final double VIEW_RANGE_SQ = 96 * 96;

    public static void particle(Location at, Particle particle, int count, double spread, double speed) {
        emit(at, particle, count, spread, spread, spread, speed, null);
    }

    public static <T> void particle(Location at, Particle particle, int count, double spread, double speed, T data) {
        emit(at, particle, count, spread, spread, spread, speed, data);
    }

    /** Spawns one particle moving in {@code direction} at {@code speed}. */
    public static void directional(Location at, Particle particle, Vector direction, double speed) {
        emit(at, particle, 0, direction.getX(), direction.getY(), direction.getZ(), speed, null);
    }

    /**
     * Sends a particle to every player who can see it from a distance (long-range "forced" particles),
     * except players whose eyes are right next to it: in first person those render huge and cover
     * the screen, e.g. a burst centred on the caster's own chest.
     */
    private static <T> void emit(Location at, Particle particle, int count, double dx, double dy, double dz,
                                 double speed, T data) {
        List<Player> receivers = new ArrayList<>();
        for (Player viewer : at.getWorld().getPlayers()) {
            double d = viewer.getEyeLocation().distanceSquared(at);
            if (d > FACE_CLEARANCE_SQ && d < VIEW_RANGE_SQ) {
                receivers.add(viewer);
            }
        }
        if (receivers.isEmpty()) {
            return;
        }
        new ParticleBuilder(particle).location(at).count(count).offset(dx, dy, dz).extra(speed).data(data)
                .force(true).receivers(receivers).spawn();
    }

    /** Spawns {@code count} particles flying outward from {@code center} in random directions. */
    public static void burst(Location center, Particle particle, int count, double speed) {
        for (int i = 0; i < count; i++) {
            directional(center, particle, randomUnit(), speed);
        }
    }

    public static void sound(Location at, Sound sound, float volume, float pitch) {
        World world = at.getWorld();
        world.playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    /** Calls {@code each} for points along a horizontal circle. */
    public static void ring(Location center, double radius, int points, Consumer<Location> each) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            each.accept(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
    }

    /** Calls {@code each} every {@code step} blocks along a straight line. */
    public static void line(Location from, Location to, double step, Consumer<Location> each) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4) {
            each.accept(from.clone());
            return;
        }
        Vector unit = delta.multiply(1.0 / length);
        for (double d = 0; d <= length; d += step) {
            each.accept(from.clone().add(unit.clone().multiply(d)));
        }
    }

    /** A jagged lightning-style line. */
    public static void arc(Location from, Location to, Consumer<Location> each) {
        Vector delta = to.toVector().subtract(from.toVector());
        int segments = Math.max(2, (int) (delta.length() / 1.2));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location prev = from.clone();
        for (int i = 1; i <= segments; i++) {
            Location next = from.clone().add(delta.clone().multiply((double) i / segments));
            if (i < segments) {
                next.add(r.nextDouble(-0.4, 0.4), r.nextDouble(-0.4, 0.4), r.nextDouble(-0.4, 0.4));
            }
            line(prev, next, 0.25, each);
            prev = next;
        }
    }

    public static Vector randomUnit() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector v;
        do {
            v = new Vector(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
        } while (v.lengthSquared() > 1 || v.lengthSquared() < 1.0E-4);
        return v.normalize();
    }

    /** A unit vector perpendicular to {@code dir}, lying in the horizontal plane where possible. */
    public static Vector right(Vector dir) {
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        return right.normalize();
    }
}
