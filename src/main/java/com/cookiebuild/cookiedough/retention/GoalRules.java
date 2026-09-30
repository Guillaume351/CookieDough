package com.cookiebuild.cookiedough.retention;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure, mode-neutral objective rules. Every objective must be reachable by a
 * player who only plays Build Battle (no eliminations required).
 */
public final class GoalRules {
    public static final int DAILY_MATCH_COINS = 15;
    public static final int DAILY_MATCH_XP = 20;
    public static final int DAILY_WIN_COINS = 25;
    public static final int DAILY_WIN_XP = 30;

    public static final int WEEKLY_MATCHES_TARGET = 3;
    public static final int WEEKLY_WINS_TARGET = 1;
    public static final int WEEKLY_FINISHED_TARGET = 5;

    public record Objective(String key, String labelKey, int coins, int experience) {
    }

    public record Achievement(String key, int coins) {
        public String labelKey() {
            return "goals.achievement." + key;
        }
    }

    public static final Objective WEEKLY_MATCHES = new Objective("weekly-matches",
            "goals.weekly.play_matches", 50, 25);
    public static final Objective WEEKLY_WIN = new Objective("weekly-win", "goals.weekly.win", 50, 25);
    public static final Objective WEEKLY_FINISHED = new Objective("weekly-finish5",
            "goals.weekly.finish_matches", 75, 40);

    public static final Achievement FIRST_MATCH = new Achievement("first_match", 10);
    public static final Achievement FIRST_WIN = new Achievement("first_win", 20);
    public static final Achievement TEN_KILLS = new Achievement("ten_kills", 25);
    public static final Achievement FIRST_BUILD_BATTLE_WIN = new Achievement("first_buildbattles_win", 40);
    public static final Achievement TEN_MATCHES = new Achievement("ten_matches", 50);
    public static final Achievement FIVE_MODES = new Achievement("five_modes", 75);
    public static final Achievement REACTION_ROOKIE = new Achievement("reaction_rookie", 10);

    /** Achievements that can be displayed and counted by every client. */
    public static final List<Achievement> CATALOG = List.of(FIRST_MATCH, FIRST_WIN, TEN_KILLS,
            FIRST_BUILD_BATTLE_WIN, TEN_MATCHES, FIVE_MODES, REACTION_ROOKIE);

    public static final int TEN_MATCHES_THRESHOLD = 10;
    public static final int FIVE_MODES_THRESHOLD = 5;

    private GoalRules() {
    }

    /** Weekly objectives completed by the given weekly counters, in display order. */
    public static List<Objective> completedWeekly(int weeklyMatches, int weeklyWins) {
        Map<String, Objective> completed = new LinkedHashMap<>();
        if (weeklyMatches >= WEEKLY_MATCHES_TARGET) completed.put(WEEKLY_MATCHES.key(), WEEKLY_MATCHES);
        if (weeklyWins >= WEEKLY_WINS_TARGET) completed.put(WEEKLY_WIN.key(), WEEKLY_WIN);
        if (weeklyMatches >= WEEKLY_FINISHED_TARGET) completed.put(WEEKLY_FINISHED.key(), WEEKLY_FINISHED);
        return List.copyOf(completed.values());
    }

    /** Achievements earned from one match result; lifetime milestones are handled separately. */
    public static List<Achievement> matchAchievements(String game, boolean won, int weeklyKills) {
        List<Achievement> result = new java.util.ArrayList<>();
        result.add(FIRST_MATCH);
        if (won) result.add(FIRST_WIN);
        if (won && isBuildBattle(game)) result.add(FIRST_BUILD_BATTLE_WIN);
        if (weeklyKills >= 10) result.add(TEN_KILLS);
        return List.copyOf(result);
    }

    /** Lifetime milestones computed from persisted match history. */
    public static List<Achievement> lifetimeAchievements(long totalMatches, long distinctModes,
            Set<String> alreadyUnlocked) {
        List<Achievement> result = new java.util.ArrayList<>();
        if (totalMatches >= TEN_MATCHES_THRESHOLD && !alreadyUnlocked.contains(TEN_MATCHES.key())) {
            result.add(TEN_MATCHES);
        }
        if (distinctModes >= FIVE_MODES_THRESHOLD && !alreadyUnlocked.contains(FIVE_MODES.key())) {
            result.add(FIVE_MODES);
        }
        return List.copyOf(result);
    }

    static boolean isBuildBattle(String game) {
        if (game == null) return false;
        String normalized = game.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        return normalized.equals("buildbattles") || normalized.equals("buildbattle");
    }
}
