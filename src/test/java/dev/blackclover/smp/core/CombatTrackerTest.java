package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTrackerTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID v = UUID.randomUUID();

    @Test
    void lastAttackerExpiresAfterTag() {
        CombatTracker t = new CombatTracker(15_000);
        t.recordHit(a, v, 0);
        assertEquals(Optional.of(a), t.lastAttacker(v, 14_000));
        assertTrue(t.lastAttacker(v, 16_000).isEmpty());
    }

    @Test
    void latestAttackerWins() {
        CombatTracker t = new CombatTracker(15_000);
        t.recordHit(a, v, 0);
        t.recordHit(b, v, 1_000);
        assertEquals(Optional.of(b), t.lastAttacker(v, 2_000));
    }

    @Test
    void bothSidesAreInCombat() {
        CombatTracker t = new CombatTracker(15_000);
        t.recordHit(a, v, 0);
        assertTrue(t.inCombat(a, 10_000));
        assertTrue(t.inCombat(v, 10_000));
        assertFalse(t.inCombat(a, 20_000));
        t.clear(v);
        assertFalse(t.inCombat(v, 1_000));
    }
}
