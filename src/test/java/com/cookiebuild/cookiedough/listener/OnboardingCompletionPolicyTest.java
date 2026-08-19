package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OnboardingCompletionPolicyTest {
    @Test
    void onlyExplicitOnboardingChoicesCompleteTheFlow() {
        assertTrue(OnboardingCompletionPolicy.completes("quick"));
        assertTrue(OnboardingCompletionPolicy.completes("games"));
        assertTrue(OnboardingCompletionPolicy.completes("community"));
        assertTrue(OnboardingCompletionPolicy.completes("back"));
        assertFalse(OnboardingCompletionPolicy.completes("noop"));
        assertFalse(OnboardingCompletionPolicy.completes(null));
    }
}
