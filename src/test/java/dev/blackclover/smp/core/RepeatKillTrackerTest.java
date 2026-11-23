package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatKillTrackerTest {

    private static final long MIN = 60_000L;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void samePairBlockedForWindow() {
        RepeatKillTracker t = new RepeatKillTracker(10 * MIN);
        assertTrue(t.tryRecord(a, b, 0));
        assertFalse(t.tryRecord(a, b, 9 * MIN));
        assertTrue(t.tryRecord(a, b, 10 * MIN));
    }

    @Test
    void otherPairsUnaffected() {
        RepeatKillTracker t = new RepeatKillTracker(10 * MIN);
        assertTrue(t.tryRecord(a, b, 0));
        assertTrue(t.tryRecord(b, a, 1));
        assertTrue(t.tryRecord(a, c, 2));
    }
}
