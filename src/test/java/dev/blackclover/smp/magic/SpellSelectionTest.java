package dev.blackclover.smp.magic;

import dev.blackclover.smp.grimoire.GrimoireType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellSelectionTest {

    private final UUID a = UUID.randomUUID();

    @Test
    void startsAtFirstSpell() {
        SpellSelection s = new SpellSelection();
        assertEquals(0, s.index(a, GrimoireType.FIRE, 4));
    }

    @Test
    void cyclesThroughUnlocked() {
        SpellSelection s = new SpellSelection();
        assertEquals(1, s.next(a, GrimoireType.FIRE, 3));
        assertEquals(2, s.next(a, GrimoireType.FIRE, 3));
        assertEquals(0, s.next(a, GrimoireType.FIRE, 3));
    }

    @Test
    void switchingGrimoireResetsSelection() {
        SpellSelection s = new SpellSelection();
        s.next(a, GrimoireType.FIRE, 4);
        s.next(a, GrimoireType.FIRE, 4);
        s.next(a, GrimoireType.FIRE, 4);
        assertEquals(3, s.index(a, GrimoireType.FIRE, 4));
        assertEquals(0, s.index(a, GrimoireType.WATER, 4));
    }

    @Test
    void levelDownMovesToHighestUnlocked() {
        SpellSelection s = new SpellSelection();
        s.next(a, GrimoireType.FIRE, 4);
        s.next(a, GrimoireType.FIRE, 4);
        s.next(a, GrimoireType.FIRE, 4);
        assertEquals(1, s.index(a, GrimoireType.FIRE, 2));
        assertEquals(0, s.index(a, GrimoireType.FIRE, 0));
    }
}
