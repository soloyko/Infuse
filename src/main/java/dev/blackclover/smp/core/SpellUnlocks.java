package dev.blackclover.smp.core;

/** Each grimoire has four spells, unlocked at levels 1, 5, 10 and 20. */
public final class SpellUnlocks {

    private static final int[] LEVELS = {1, 5, 10, 20};

    private SpellUnlocks() {
    }

    public static int slots() {
        return LEVELS.length;
    }

    public static int levelForSlot(int slot) {
        return LEVELS[slot];
    }

    public static int unlockedCount(int level) {
        int n = 0;
        for (int l : LEVELS) {
            if (level >= l) {
                n++;
            }
        }
        return n;
    }
}
