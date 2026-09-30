package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JoinExperiencePlanTest {
    @Test
    void pendingOnboardingShowsOnlyTheCompactPrompt() {
        assertEquals(new JoinExperiencePlan(true, false, false, true), JoinExperiencePlan.forPlayer(true));
    }

    @Test
    void firstSessionDefersTheAppPromptInsteadOfWaitingForSessionTwo() {
        JoinExperiencePlan first = JoinExperiencePlan.forPlayer(true);
        assertEquals(false, first.showAppPromotion());
        assertEquals(true, first.deferAppPromotion());
    }

    @Test
    void completedOnboardingKeepsNormalUpdateAndAppPrompts() {
        assertEquals(new JoinExperiencePlan(false, true, true, false), JoinExperiencePlan.forPlayer(false));
    }
}
