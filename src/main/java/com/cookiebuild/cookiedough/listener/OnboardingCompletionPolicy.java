package com.cookiebuild.cookiedough.listener;

import java.util.Set;

/**
 * Defines the explicit onboarding choices that demonstrate a player action.
 * "Playing alone?" (community links) is deliberately excluded: it sends the
 * player out of the game (Discord, app) and must not close onboarding.
 */
public final class OnboardingCompletionPolicy {
    private static final Set<String> MENU_CHOICES = Set.of("quick", "games", "back");

    private OnboardingCompletionPolicy() { }

    public static boolean completes(String action) {
        return action != null && (MENU_CHOICES.contains(action)
                || action.startsWith("admission:") || action.startsWith("activity:"));
    }
}
