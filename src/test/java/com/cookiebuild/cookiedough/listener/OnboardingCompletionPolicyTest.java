package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OnboardingCompletionPolicyTest {
    @Test
    void onlySuccessfulAdmissionsCompleteTheFlow() {
        assertTrue(OnboardingCompletionPolicy.completes("admission:MicroBattles"));
        assertTrue(OnboardingCompletionPolicy.completes("activity:Skyblock"));
        assertFalse(OnboardingCompletionPolicy.completes("quick"));
        assertFalse(OnboardingCompletionPolicy.completes("games"));
        assertFalse(OnboardingCompletionPolicy.completes("community"));
        assertFalse(OnboardingCompletionPolicy.completes("back"));
        assertFalse(OnboardingCompletionPolicy.completes("noop"));
        assertFalse(OnboardingCompletionPolicy.completes(null));
    }
}
