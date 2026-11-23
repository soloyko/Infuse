package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelCurveTest {

    private final LevelCurve curve = LevelCurve.defaults();

    @Test
    void thresholds() {
        assertEquals(100, curve.xpToNext(1));
        assertEquals(125, curve.xpToNext(2));
        assertEquals(0, curve.totalXpForLevel(1));
        assertEquals(100, curve.totalXpForLevel(2));
        assertEquals(6175, curve.totalXpForLevel(20));
        assertEquals(1, curve.levelForXp(0));
        assertEquals(1, curve.levelForXp(99));
        assertEquals(2, curve.levelForXp(100));
        assertEquals(20, curve.levelForXp(6175));
        assertEquals(20, curve.levelForXp(99999));
        assertEquals(1, curve.levelForXp(-5));
        assertEquals(6175, curve.clampXp(99999));
        assertEquals(0, curve.clampXp(-3));
    }
}
