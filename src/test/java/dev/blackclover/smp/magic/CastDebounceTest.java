package dev.blackclover.smp.magic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastDebounceTest {

    @Test
    void oneCastPerClick() {
        CastDebounce d = new CastDebounce(150);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(d.tryAcquire(a, 1000));
        assertFalse(d.tryAcquire(a, 1100));
        assertTrue(d.tryAcquire(b, 1100));
        assertTrue(d.tryAcquire(a, 1200));
    }
}
