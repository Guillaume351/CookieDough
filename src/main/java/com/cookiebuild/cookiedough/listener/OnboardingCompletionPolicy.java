package com.cookiebuild.cookiedough.listener;

/** Defines the explicit onboarding choices that demonstrate a player action. */
public final class OnboardingCompletionPolicy {
    private OnboardingCompletionPolicy() { }

    public static boolean completes(String action) {
        return action != null && (action.startsWith("admission:") || action.startsWith("activity:"));
    }
}
