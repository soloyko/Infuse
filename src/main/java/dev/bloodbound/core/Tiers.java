package dev.bloodbound.core;

import java.util.Arrays;

/** Blood thresholds for each tier. Tier numbers are 1-based. */
public final class Tiers {
    private final int[] thresholds;

    public Tiers(int[] thresholds) {
        if (thresholds.length == 0 || thresholds[0] != 0) {
            throw new IllegalArgumentException("Tier thresholds must start at 0");
        }
        for (int i = 1; i < thresholds.length; i++) {
            if (thresholds[i] <= thresholds[i - 1]) {
                throw new IllegalArgumentException("Tier thresholds must be strictly increasing");
            }
        }
        this.thresholds = thresholds.clone();
    }

    public int tierFor(int blood) {
        int tier = 1;
        for (int i = 1; i < thresholds.length; i++) {
            if (blood >= thresholds[i]) tier = i + 1;
        }
        return tier;
    }

    public int floorOf(int tier) {
        int index = Math.max(1, Math.min(tier, thresholds.length)) - 1;
        return thresholds[index];
    }

    public int maxTier() {
        return thresholds.length;
    }

    /** Blood still needed to reach the next tier, or -1 at max tier. */
    public int toNext(int blood) {
        int tier = tierFor(blood);
        return tier >= maxTier() ? -1 : thresholds[tier] - blood;
    }

    @Override
    public String toString() {
        return "Tiers" + Arrays.toString(thresholds);
    }
}
