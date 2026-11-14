package dev.bloodbound.core;

/** Pure arithmetic for earning and losing blood. */
public record BloodRules(
        Tiers tiers,
        int killReward,
        int mobReward,
        double catchUpPerTier,
        double shatterFraction,
        double killerShare,
        double environmentalLossFraction
) {
    public record Shatter(int lost, int killerGain) {}

    public int killReward(int killerTier, int victimTier, double multiplier) {
        int tiersAbove = Math.max(0, victimTier - killerTier);
        double reward = killReward * (1.0 + catchUpPerTier * tiersAbove);
        return (int) Math.round(reward * multiplier);
    }

    public int mobReward(double multiplier) {
        return (int) Math.round(mobReward * multiplier);
    }

    /** Blood lost by a victim killed by a player, capped so they drop at most one tier. */
    public Shatter shatter(int victimBlood) {
        if (victimBlood <= 0) return new Shatter(0, 0);
        int tier = tiers.tierFor(victimBlood);
        int lowestAllowed = tiers.floorOf(Math.max(1, tier - 1));
        int cap = victimBlood - lowestAllowed;
        int lost = Math.min(cap, (int) Math.round(victimBlood * shatterFraction));
        return new Shatter(lost, (int) Math.round(lost * killerShare));
    }

    public int environmentalLoss(int blood) {
        if (blood <= 0) return 0;
        return (int) Math.round(blood * environmentalLossFraction);
    }
}
