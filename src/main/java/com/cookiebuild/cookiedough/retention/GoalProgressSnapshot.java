package com.cookiebuild.cookiedough.retention;

import java.time.LocalDate;
import java.util.Set;

public record GoalProgressSnapshot(
        LocalDate day,
        int dailyMatches,
        int dailyWins,
        LocalDate firstWinDate,
        String week,
        int weeklyMatches,
        int weeklyWins,
        int weeklyKills,
        Set<String> achievements) {
    public GoalProgressSnapshot {
        achievements = Set.copyOf(achievements);
    }
}
