package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GoalRulesTest {
    @Test
    void everyWeeklyObjectiveIsReachableWithoutEliminations() {
        // A Build Battle-only player: five matches, one win, zero kills.
        List<GoalRules.Objective> completed = GoalRules.completedWeekly(5, 1);
        assertEquals(List.of(GoalRules.WEEKLY_MATCHES, GoalRules.WEEKLY_WIN, GoalRules.WEEKLY_FINISHED), completed);
        assertEquals(List.of(GoalRules.WEEKLY_MATCHES), GoalRules.completedWeekly(3, 0));
        assertTrue(GoalRules.completedWeekly(2, 0).isEmpty());
    }

    @Test
    void buildBattleWinUnlocksItsOwnAchievement() {
        assertTrue(GoalRules.matchAchievements("BuildBattles", true, 0).contains(GoalRules.FIRST_BUILD_BATTLE_WIN));
        assertFalse(GoalRules.matchAchievements("BuildBattles", false, 0).contains(GoalRules.FIRST_BUILD_BATTLE_WIN));
        assertFalse(GoalRules.matchAchievements("SkyWars", true, 0).contains(GoalRules.FIRST_BUILD_BATTLE_WIN));
        assertTrue(GoalRules.matchAchievements("SkyWars", true, 12).contains(GoalRules.TEN_KILLS));
    }

    @Test
    void lifetimeMilestonesAreGrantedOnce() {
        assertEquals(List.of(GoalRules.TEN_MATCHES, GoalRules.FIVE_MODES),
                GoalRules.lifetimeAchievements(10, 5, Set.of()));
        assertEquals(List.of(GoalRules.FIVE_MODES),
                GoalRules.lifetimeAchievements(40, 5, Set.of(GoalRules.TEN_MATCHES.key())));
        assertTrue(GoalRules.lifetimeAchievements(9, 4, Set.of()).isEmpty());
    }

    @Test
    void rewardMagnitudesStaySensible() {
        for (GoalRules.Achievement achievement : GoalRules.CATALOG) {
            assertTrue(achievement.coins() > 0 && achievement.coins() <= 100, achievement.key());
        }
        assertEquals(15, GoalRules.DAILY_MATCH_COINS);
        assertEquals(25, GoalRules.DAILY_WIN_COINS);
    }
}
