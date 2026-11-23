package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LibraryPlacerTest {

    private static void assertRules(List<LibraryPlacer.Point> pts, int border, int minSpacing) {
        int limit = border / 2 - 50 - 8;
        for (LibraryPlacer.Point p : pts) {
            assertTrue(Math.hypot(p.x(), p.z()) >= 1000, "too close to origin: " + p);
            assertTrue(Math.abs(p.x()) <= limit && Math.abs(p.z()) <= limit, "outside border: " + p);
        }
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                LibraryPlacer.Point p = pts.get(i), q = pts.get(j);
                assertTrue(Math.hypot(p.x() - q.x(), p.z() - q.z()) >= minSpacing, "too close: " + p + " " + q);
            }
        }
    }

    @Test
    void largeBorder() {
        List<LibraryPlacer.Point> pts = LibraryPlacer.place(13, 6000, 1000, 50, 400, new Random(1), List.of());
        assertEquals(13, pts.size());
        assertRules(pts, 6000, 400);
    }

    @Test
    void smallBorderRelaxesSpacing() {
        int border = LibraryPlacer.minimumBorder(1000, 50);
        assertEquals(2116, border);
        List<LibraryPlacer.Point> pts = LibraryPlacer.place(13, border, 1000, 50, 400, new Random(2), List.of());
        assertEquals(13, pts.size());
        assertRules(pts, border, 1);
    }

    @Test
    void avoidsGivenPoints() {
        List<LibraryPlacer.Point> first = LibraryPlacer.place(12, 6000, 1000, 50, 400, new Random(3), List.of());
        List<LibraryPlacer.Point> extra = LibraryPlacer.place(1, 6000, 1000, 50, 400, new Random(4), first);
        assertEquals(1, extra.size());
        for (LibraryPlacer.Point p : first) {
            assertTrue(Math.hypot(p.x() - extra.get(0).x(), p.z() - extra.get(0).z()) >= 400);
        }
    }

    @Test
    void tooSmallBorderThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> LibraryPlacer.place(13, 2000, 1000, 50, 400, new Random(1), List.of()));
    }
}
