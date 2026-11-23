package dev.blackclover.smp.core;

/**
 * Magic level curve. Going from level L to L+1 costs {@code base + step * (L - 1)} XP.
 */
public record LevelCurve(int cap, int base, int step) {

    public static LevelCurve defaults() {
        return new LevelCurve(20, 100, 25);
    }

    public int xpToNext(int level) {
        return base + step * (level - 1);
    }

    /** Total XP needed to be at the start of {@code level}. */
    public int totalXpForLevel(int level) {
        int n = Math.max(0, Math.min(level, cap) - 1);
        return base * n + step * (n * (n - 1) / 2);
    }

    public int levelForXp(int xp) {
        int level = 1;
        while (level < cap && xp >= totalXpForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    public int clampXp(int xp) {
        return Math.max(0, Math.min(xp, totalXpForLevel(cap)));
    }
}
