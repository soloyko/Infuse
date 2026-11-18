package dev.bloodbound.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class KillCooldownsTest {
    @Test
    void blocksRepeatKillsWithinWindow() {
        AtomicLong now = new AtomicLong(0);
        KillCooldowns cd = new KillCooldowns(600_000, now::get);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

        assertTrue(cd.tryRecord(a, b));
        now.set(300_000);
        assertFalse(cd.tryRecord(a, b));
        assertTrue(cd.tryRecord(a, c));
        now.set(600_001);
        assertTrue(cd.tryRecord(a, b));
    }
}
