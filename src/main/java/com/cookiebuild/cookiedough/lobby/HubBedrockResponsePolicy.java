package com.cookiebuild.cookiedough.lobby;

/** Pure state guard applied after nonce validation and before any delayed form action. */
public final class HubBedrockResponsePolicy {
    private HubBedrockResponsePolicy() { }

    public static boolean queueValid(String formGame, String queuedGame, boolean queuedState, String currentGame) {
        return formGame != null && queuedGame != null && currentGame != null && queuedState
                && formGame.equalsIgnoreCase(queuedGame) && formGame.equalsIgnoreCase(currentGame);
    }

    public static boolean replayValid(boolean lobbyState, boolean hasCurrentGame) {
        return lobbyState && !hasCurrentGame;
    }
}
