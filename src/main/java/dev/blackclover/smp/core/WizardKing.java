package dev.blackclover.smp.core;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Picks the Wizard King: the highest-level grimoire holder. */
public final class WizardKing {

    public record Candidate(UUID id, int level, int xp) {
    }

    private WizardKing() {
    }

    /**
     * Highest level wins, then most XP. On a full tie the current king keeps the crown, otherwise the
     * lowest UUID wins so the choice is stable. Nobody below {@code minLevel} can be king.
     */
    public static Optional<UUID> pick(List<Candidate> candidates, UUID current, int minLevel) {
        Comparator<Candidate> order = Comparator.comparingInt(Candidate::level)
                .thenComparingInt(Candidate::xp)
                .thenComparing(c -> c.id().equals(current))
                .thenComparing(Candidate::id, Comparator.reverseOrder());
        return candidates.stream()
                .filter(c -> c.level() >= minLevel)
                .max(order)
                .map(Candidate::id);
    }
}
