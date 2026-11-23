package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingSearchTest {

    @Test
    void prefersSpotAtOrBelowAim() {
        // Aiming just under a 1-block ceiling: the room floor is 2 below, the upstairs floor 1 above.
        Set<Integer> standable = Set.of(98, 101);
        assertEquals(OptionalInt.of(98), LandingSearch.findY(100, 1, 8, standable::contains));
    }

    @Test
    void cannotClimbThroughThickCeilings() {
        Set<Integer> standable = Set.of(103);
        assertTrue(LandingSearch.findY(100, 1, 8, standable::contains).isEmpty());
    }

    @Test
    void steppingUpOneBlockIsAllowed() {
        Set<Integer> standable = Set.of(101);
        assertEquals(OptionalInt.of(101), LandingSearch.findY(100, 1, 8, standable::contains));
    }

    @Test
    void exactSpotFirst() {
        Set<Integer> standable = Set.of(100, 99, 101);
        assertEquals(OptionalInt.of(100), LandingSearch.findY(100, 1, 8, standable::contains));
    }
}
