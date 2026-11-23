package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManaMathTest {

    private final ManaMath mana = ManaMath.defaults();

    @Test
    void maxManaGrowsWithLevel() {
        assertEquals(100.0, mana.maxMana(1, false), 1e-9);
        assertEquals(176.0, mana.maxMana(20, false), 1e-9);
    }

    @Test
    void fiveLeafBoostsManaAndRegen() {
        assertEquals(150.0, mana.maxMana(1, true), 1e-9);
        assertEquals(4.0, mana.regenPerSecond(false), 1e-9);
        assertEquals(6.0, mana.regenPerSecond(true), 1e-9);
    }
}
