package dev.blackclover.smp.core;

public record ManaMath(double base, double perLevel, double regen, double fiveLeafMultiplier) {

    public static ManaMath defaults() {
        return new ManaMath(100, 4, 4, 1.5);
    }

    public double maxMana(int level, boolean fiveLeaf) {
        double max = base + perLevel * (Math.max(1, level) - 1);
        return fiveLeaf ? max * fiveLeafMultiplier : max;
    }

    public double regenPerSecond(boolean fiveLeaf) {
        return fiveLeaf ? regen * fiveLeafMultiplier : regen;
    }
}
