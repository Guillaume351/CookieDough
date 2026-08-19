package com.cookiebuild.cookiedough.listener;

import java.util.Set;

/** Defines the explicit onboarding choices that demonstrate a player action. */
public final class OnboardingCompletionPolicy {
    private static final Set<String> COMPLETING_ACTIONS = Set.of("quick", "games", "community", "back");

    private OnboardingCompletionPolicy() { }

    public static boolean completes(String action) {
        return action != null && COMPLETING_ACTIONS.contains(action);
    }
}
