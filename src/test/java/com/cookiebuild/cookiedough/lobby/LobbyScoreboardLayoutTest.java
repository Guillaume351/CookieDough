package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class LobbyScoreboardLayoutTest {
    @Test
    void beginnerLayoutClearsEveryLineLeftByTheFullStatsLayout() {
        assertEquals(List.of(8, 7, 6, 5, 4, 3, 2, 1, 0), LobbyScoreboard.unusedScores(8));
    }
}
