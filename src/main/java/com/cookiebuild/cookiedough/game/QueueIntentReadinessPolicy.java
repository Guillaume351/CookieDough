package com.cookiebuild.cookiedough.game;

/** Pure readiness rule for passive-activity queue intentions. */
final class QueueIntentReadinessPolicy {
    private QueueIntentReadinessPolicy() { }

    static boolean shouldActivate(int admittedPlayers, int validIntents, int minimumPlayers, int capacity) {
        if (admittedPlayers < 0 || validIntents < 0 || minimumPlayers < 1 || capacity < minimumPlayers) {
            return false;
        }
        return admittedPlayers < capacity && validIntents > 0
                && admittedPlayers + Math.min(validIntents, capacity - admittedPlayers) >= minimumPlayers;
    }
}
