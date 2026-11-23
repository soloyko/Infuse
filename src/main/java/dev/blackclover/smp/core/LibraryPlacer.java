package dev.blackclover.smp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/** Picks library spots: inside the border, far from 0,0, spread apart. */
public final class LibraryPlacer {

    /** Half-width of a library footprint plus slack, kept clear of the border. */
    private static final int FOOTPRINT = 8;
    private static final int TRIES_BEFORE_RELAX = 500;

    public record Point(int x, int z) {
        double distance(Point o) {
            return Math.hypot(x - o.x, z - o.z);
        }
    }

    private LibraryPlacer() {
    }

    public static int minimumBorder(int minFromOrigin, int margin) {
        return 2 * (minFromOrigin + margin + FOOTPRINT);
    }

    public static List<Point> place(int count, int borderSize, int minFromOrigin, int margin,
                                    int minSpacing, Random rnd, Collection<Point> avoid) {
        if (borderSize < minimumBorder(minFromOrigin, margin)) {
            throw new IllegalArgumentException("Border must be at least " + minimumBorder(minFromOrigin, margin));
        }
        int limit = borderSize / 2 - margin - FOOTPRINT;
        List<Point> placed = new ArrayList<>();
        double spacing = minSpacing;
        int failures = 0;
        while (placed.size() < count) {
            Point p = new Point(rnd.nextInt(2 * limit + 1) - limit, rnd.nextInt(2 * limit + 1) - limit);
            if (Math.hypot(p.x(), p.z()) >= minFromOrigin && farEnough(p, placed, avoid, spacing)) {
                placed.add(p);
                failures = 0;
            } else if (++failures >= TRIES_BEFORE_RELAX) {
                spacing *= 0.8;
                failures = 0;
            }
        }
        return placed;
    }

    private static boolean farEnough(Point p, List<Point> placed, Collection<Point> avoid, double spacing) {
        for (Point q : placed) {
            if (p.distance(q) < spacing) {
                return false;
            }
        }
        for (Point q : avoid) {
            if (p.distance(q) < spacing) {
                return false;
            }
        }
        return true;
    }
}
