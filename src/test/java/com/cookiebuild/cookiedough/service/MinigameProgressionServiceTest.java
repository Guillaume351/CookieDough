package com.cookiebuild.cookiedough.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MinigameProgressionServiceTest {
    @Test
    void recognizesTurfWarsProgressionForMatchAndGoalRewards() {
        assertEquals(MinigameProgressionService.TURFWARS,
                MinigameProgressionService.supportedMinigameKey("TurfWars"));
        assertEquals(MinigameProgressionService.TURFWARS,
                MinigameProgressionService.supportedMinigameKey("turfwars"));
    }

    @Test
    void goalRewardsFailClosedForUnknownGames() {
        assertFalse(new MinigameProgressionService(null).claimGoalReward(
                UUID.randomUUID(), "unknown-game", 20, 15, "daily-test"));
    }
}
