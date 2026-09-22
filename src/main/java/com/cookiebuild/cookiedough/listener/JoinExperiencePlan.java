package com.cookiebuild.cookiedough.listener;

/** Keeps the first join focused instead of stacking normal returning-player prompts. */
record JoinExperiencePlan(boolean showOnboarding, boolean showUpdates, boolean showAppPromotion) {
    static JoinExperiencePlan forPlayer(boolean onboardingPending) {
        return onboardingPending
                ? new JoinExperiencePlan(true, false, false)
                : new JoinExperiencePlan(false, true, true);
    }
}
