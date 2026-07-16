package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JoinExperiencePlanTest {
    @Test
    void pendingOnboardingShowsOnlyTheCompactPrompt() {
        assertEquals(new JoinExperiencePlan(true, false, false), JoinExperiencePlan.forPlayer(true));
    }

    @Test
    void completedOnboardingKeepsNormalUpdateAndAppPrompts() {
        assertEquals(new JoinExperiencePlan(false, true, true), JoinExperiencePlan.forPlayer(false));
    }
}
