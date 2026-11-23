package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellUnlocksTest {

    @Test
    void unlockedCountFollowsUnlockLevels() {
        assertEquals(0, SpellUnlocks.unlockedCount(0));
        assertEquals(1, SpellUnlocks.unlockedCount(1));
        assertEquals(1, SpellUnlocks.unlockedCount(4));
        assertEquals(2, SpellUnlocks.unlockedCount(5));
        assertEquals(2, SpellUnlocks.unlockedCount(9));
        assertEquals(3, SpellUnlocks.unlockedCount(10));
        assertEquals(3, SpellUnlocks.unlockedCount(19));
        assertEquals(4, SpellUnlocks.unlockedCount(20));
    }

    @Test
    void unlockLevelBySlot() {
        assertEquals(1, SpellUnlocks.levelForSlot(0));
        assertEquals(5, SpellUnlocks.levelForSlot(1));
        assertEquals(10, SpellUnlocks.levelForSlot(2));
        assertEquals(20, SpellUnlocks.levelForSlot(3));
    }
}
