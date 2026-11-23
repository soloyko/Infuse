package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WizardKingTest {

    @Test
    void kingIsHighestLevelThenXp() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        List<WizardKing.Candidate> cs = List.of(
                new WizardKing.Candidate(a, 7, 900), new WizardKing.Candidate(b, 9, 1500), new WizardKing.Candidate(c, 9, 1600));
        assertEquals(Optional.of(c), WizardKing.pick(cs, null, 5));
    }

    @Test
    void currentKingKeepsTheCrownOnATie() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        List<WizardKing.Candidate> cs = List.of(new WizardKing.Candidate(a, 8, 1000), new WizardKing.Candidate(b, 8, 1000));
        assertEquals(Optional.of(b), WizardKing.pick(cs, b, 5));
        assertEquals(Optional.of(a), WizardKing.pick(cs, a, 5));
        assertEquals(Optional.of(a), WizardKing.pick(cs, null, 5));
    }

    @Test
    void nobodyBelowTheMinimumLevel() {
        UUID a = new UUID(0, 1);
        assertEquals(Optional.empty(), WizardKing.pick(List.of(new WizardKing.Candidate(a, 4, 400)), null, 5));
    }
}
