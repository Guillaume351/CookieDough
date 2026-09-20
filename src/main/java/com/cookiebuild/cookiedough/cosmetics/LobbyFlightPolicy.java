package com.cookiebuild.cookiedough.cosmetics;

import org.bukkit.GameMode;

import com.cookiebuild.cookiedough.player.PlayerState;

/** Pure lobby-only flight rules, independent from Bukkit mutation timing. */
final class LobbyFlightPolicy {
    private LobbyFlightPolicy() {
    }

    static boolean shouldEnable(boolean selected, boolean inLobbyWorld, PlayerState state) {
        return selected && inLobbyWorld
                && (state == PlayerState.LOBBY || state == PlayerState.QUEUED);
    }

    static boolean preservesIndependentFlight(GameMode gameMode, boolean preservePermission) {
        return gameMode == GameMode.CREATIVE
                || gameMode == GameMode.SPECTATOR
                || preservePermission;
    }

    static boolean cancelsTransitionFallDamage(boolean safetyArmed, boolean inLobbyWorld) {
        return safetyArmed && inLobbyWorld;
    }
}
