package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillRewardsTest {

    private final KillRewards rewards = KillRewards.defaults();

    @Test
    void killXpScalesWithLevelDifference() {
        assertEquals(100, rewards.killXp(5, 5));
        assertEquals(120, rewards.killXp(5, 7));
        assertEquals(200, rewards.killXp(1, 20));
        assertEquals(50, rewards.killXp(10, 5));
        assertEquals(20, rewards.killXp(20, 1));
    }

    @Test
    void deathCostsFifteenPercent() {
        assertEquals(850, rewards.xpAfterDeath(1000));
        assertEquals(5249, rewards.xpAfterDeath(6175));
    }

    @Test
    void penaltyNeverNegative() {
        assertEquals(0, rewards.xpAfterDeath(0));
        assertEquals(0, rewards.xpAfterDeath(-10));
    }
}
