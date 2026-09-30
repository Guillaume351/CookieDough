package com.cookiebuild.cookiedough.listener;

/**
 * Keeps the first join focused instead of stacking normal returning-player
 * prompts. The mobile app reminder is not shown at a first join, but it is
 * no longer postponed to the second session either: it is deferred to the
 * first meaningful moment (first completed match or 30 s in a queue).
 */
record JoinExperiencePlan(boolean showOnboarding, boolean showUpdates, boolean showAppPromotion,
        boolean deferAppPromotion) {
    static JoinExperiencePlan forPlayer(boolean onboardingPending) {
        return onboardingPending
                ? new JoinExperiencePlan(true, false, false, true)
                : new JoinExperiencePlan(false, true, true, false);
    }
}
