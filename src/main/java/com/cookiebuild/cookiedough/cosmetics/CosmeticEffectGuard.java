package com.cookiebuild.cookiedough.cosmetics;

import com.cookiebuild.cookiedough.player.PlayerState;

/** Pure effect guards, kept separate so cosmetic changes cannot affect game rules. */
final class CosmeticEffectGuard {
    private CosmeticEffectGuard() {
    }

    static boolean canUseHubEffect(
            String selectedId, String requiredId, PlayerState state, boolean inLobbyWorld,
            long nowMillis, long lastUseMillis, long cooldownMillis) {
        return requiredId.equals(selectedId)
                && state == PlayerState.LOBBY
                && inLobbyWorld
                && nowMillis - lastUseMillis >= cooldownMillis;
    }

    static boolean canUseVictoryEffect(
            String selectedId, PlayerState state, long nowMillis, long lastUseMillis, long cooldownMillis) {
        return CosmeticCatalog.GOLDEN_COOKIE_BURST.equals(selectedId)
                && state != null
                && state != PlayerState.OFFLINE
                && nowMillis - lastUseMillis >= cooldownMillis;
    }
}
