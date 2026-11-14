package dev.bloodbound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BloodRulesTest {
    private final Tiers tiers = new Tiers(new int[]{0, 150, 400, 800, 1400});
    private final BloodRules rules = new BloodRules(tiers, 100, 2, 0.25, 0.30, 0.50, 0.10);

    @Test
    void killRewardWithCatchUp() {
        assertEquals(150, rules.killReward(1, 3, 1.0));
        assertEquals(100, rules.killReward(3, 1, 1.0));
        assertEquals(200, rules.killReward(1, 1, 2.0));
    }

    @Test
    void shatterTakesFractionAndGivesKillerShare() {
        BloodRules.Shatter s = rules.shatter(500);
        assertEquals(150, s.lost());
        assertEquals(75, s.killerGain());

        BloodRules.Shatter s2 = rules.shatter(1000);
        assertEquals(300, s2.lost());
        assertEquals(150, s2.killerGain());
    }

    @Test
    void shatterNeverDropsMoreThanOneTier() {
        BloodRules harsh = new BloodRules(tiers, 100, 2, 0.25, 0.90, 0.50, 0.10);
        // 1000 blood is tier 4; may drop to tier 3's floor (400) at most.
        assertEquals(600, harsh.shatter(1000).lost());
    }

    @Test
    void shatterAtZeroBloodLosesNothing() {
        BloodRules.Shatter s = rules.shatter(0);
        assertEquals(0, s.lost());
        assertEquals(0, s.killerGain());
    }

    @Test
    void environmentalLoss() {
        assertEquals(50, rules.environmentalLoss(500));
        assertEquals(0, rules.environmentalLoss(0));
    }
}
