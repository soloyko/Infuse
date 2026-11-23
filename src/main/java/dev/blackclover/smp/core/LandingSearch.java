package dev.blackclover.smp.core;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Finds a landing height for teleport spells. Searches the aimed height, then downward, and only
 * then up to {@code maxUp} blocks, so a blink can't hop through a ceiling into the room above.
 */
public final class LandingSearch {

    private LandingSearch() {
    }

    public static OptionalInt findY(int startY, int maxUp, int maxDown, IntPredicate standable) {
        for (int d = 0; d <= maxDown; d++) {
            if (standable.test(startY - d)) {
                return OptionalInt.of(startY - d);
            }
        }
        for (int u = 1; u <= maxUp; u++) {
            if (standable.test(startY + u)) {
                return OptionalInt.of(startY + u);
            }
        }
        return OptionalInt.empty();
    }
}
