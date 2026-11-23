package dev.blackclover.smp.core;

/**
 * XP for player kills and the XP lost on death.
 */
public record KillRewards(int base, double perLevel, double maxBonus, double minFactor, double penaltyFraction) {

    public static KillRewards defaults() {
        return new KillRewards(100, 0.10, 1.0, 0.20, 0.15);
    }

    public int killXp(int killerLevel, int victimLevel) {
        double factor = 1.0 + perLevel * (victimLevel - killerLevel);
        factor = Math.max(minFactor, Math.min(1.0 + maxBonus, factor));
        return (int) Math.round(base * factor);
    }

    public int xpAfterDeath(int xp) {
        if (xp <= 0) {
            return 0;
        }
        int loss = (int) Math.floor(xp * penaltyFraction);
        return xp - loss;
    }
}
