package dev.blackclover.smp.mage;

import dev.blackclover.smp.grimoire.GrimoireType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDataTest {

    @Test
    void xpIsPerGrimoire() {
        PlayerData d = new PlayerData(UUID.randomUUID(), "Asta");
        assertEquals(0, d.xp(GrimoireType.FIRE));
        d.setXp(GrimoireType.FIRE, 640);
        d.setXp(GrimoireType.WATER, 10);
        assertEquals(640, d.xp(GrimoireType.FIRE));
        assertEquals(10, d.xp(GrimoireType.WATER));
        assertEquals(0, d.xp(GrimoireType.LIGHT));
        assertEquals(2, d.xpView().size());
    }

    @Test
    void penaltyCooldown() {
        PlayerData d = new PlayerData(UUID.randomUUID(), "Yuno");
        long cd = 300_000;
        assertTrue(d.canBePenalized(1_000_000, cd));
        d.setLastPenaltyMillis(1_000_000);
        assertFalse(d.canBePenalized(1_060_000, cd));
        assertTrue(d.canBePenalized(1_300_000, cd));
    }
}
