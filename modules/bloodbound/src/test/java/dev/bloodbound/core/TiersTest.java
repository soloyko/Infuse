package dev.bloodbound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TiersTest {
    private final Tiers tiers = new Tiers(new int[]{0, 150, 400, 800, 1400});

    @Test
    void tierForBlood() {
        assertEquals(1, tiers.tierFor(0));
        assertEquals(1, tiers.tierFor(149));
        assertEquals(2, tiers.tierFor(150));
        assertEquals(5, tiers.tierFor(1400));
        assertEquals(5, tiers.tierFor(99_999));
        assertEquals(1, tiers.tierFor(-5));
    }

    @Test
    void floorsAndProgress() {
        assertEquals(400, tiers.floorOf(3));
        assertEquals(5, tiers.maxTier());
        assertEquals(50, tiers.toNext(100));
        assertEquals(-1, tiers.toNext(1400));
    }

    @Test
    void rejectsBadThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new Tiers(new int[]{}));
        assertThrows(IllegalArgumentException.class, () -> new Tiers(new int[]{10, 20}));
        assertThrows(IllegalArgumentException.class, () -> new Tiers(new int[]{0, 50, 50}));
    }
}
