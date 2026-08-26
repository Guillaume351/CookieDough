package com.cookiebuild.cookiedough.lobby;

import com.cookiebuild.cookiedough.player.PlayerState;

/** Pure eligibility rule for the contextual Skyblock lobby trail. */
final class SkyblockGuidancePolicy {
    static final double MIN_DISTANCE = 3.0;
    private static final double MIN_DISTANCE_SQUARED = MIN_DISTANCE * MIN_DISTANCE;

    record Context(
            boolean inLobbyWorld,
            PlayerState playerState,
            int lobbyPlayerCount,
            boolean gameOwned,
            boolean activityOwned,
            boolean profileReady,
            boolean skyblockAvailable,
            boolean readyMatch,
            boolean onlinePartyCompanion,
            boolean practicing,
            double distanceSquared) { }

    private SkyblockGuidancePolicy() { }

    static boolean shouldGuide(Context context) {
        return context != null
                && context.inLobbyWorld()
                && context.playerState() == PlayerState.LOBBY
                && context.lobbyPlayerCount() == 1
                && !context.gameOwned()
                && !context.activityOwned()
                && context.profileReady()
                && context.skyblockAvailable()
                && !context.readyMatch()
                && !context.onlinePartyCompanion()
                && !context.practicing()
                && Double.isFinite(context.distanceSquared())
                && context.distanceSquared() > MIN_DISTANCE_SQUARED;
    }
}
