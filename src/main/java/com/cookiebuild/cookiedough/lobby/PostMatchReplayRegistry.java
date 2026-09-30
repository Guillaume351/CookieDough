package com.cookiebuild.cookiedough.lobby;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cookiebuild.cookiedough.player.PlayerState;

/**
 * Remembers completed matches until the player is back in the lobby. The
 * "What next?" choice was retired on 2026-09-23 because it opened while the
 * arena was still transferring the player; it is now only presented from the
 * lobby-arrival hook, after a short delay and a fresh state check.
 */
final class PostMatchReplayRegistry {
    static final long PENDING_TTL_MILLIS = 180_000L;
    static final long OFFER_DELAY_TICKS = 30L;
    static final long REPLAY_ITEM_TICKS = 60L * 20L;

    record OfferContext(boolean online, boolean dataReady, boolean inLobbyWorld, PlayerState state,
            boolean ownedByGame, boolean queueIntent, boolean persistentActivity) { }

    private record Pending(String gameName, long completedAtMillis) { }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastCompleted = new ConcurrentHashMap<>();

    void recordCompletion(UUID playerId, String gameName, long nowMillis) {
        if (playerId == null || gameName == null || gameName.isBlank()) return;
        pending.put(playerId, new Pending(gameName, nowMillis));
        lastCompleted.put(playerId, gameName);
    }

    /**
     * Consumes the completion to present on this lobby arrival. A finished
     * arena reported by the lobby transfer counts even when its module did
     * not call {@code offerReplay()}.
     */
    Optional<String> consumeArrival(UUID playerId, String finishedGameName, long nowMillis) {
        if (playerId == null) return Optional.empty();
        Pending recorded = pending.remove(playerId);
        if (recorded != null && nowMillis - recorded.completedAtMillis() <= PENDING_TTL_MILLIS
                && nowMillis >= recorded.completedAtMillis()) {
            return Optional.of(recorded.gameName());
        }
        if (finishedGameName != null && !finishedGameName.isBlank()) {
            lastCompleted.put(playerId, finishedGameName);
            return Optional.of(finishedGameName);
        }
        return Optional.empty();
    }

    Optional<String> lastCompleted(UUID playerId) {
        return playerId == null ? Optional.empty() : Optional.ofNullable(lastCompleted.get(playerId));
    }

    void clear(UUID playerId) {
        if (playerId == null) return;
        pending.remove(playerId);
        lastCompleted.remove(playerId);
    }

    /** Presents the choice only to a player idle in the lobby: never queued, in a game or in Skyblock. */
    static boolean shouldOffer(OfferContext context) {
        return context != null && context.online() && context.dataReady() && context.inLobbyWorld()
                && context.state() == PlayerState.LOBBY && !context.ownedByGame()
                && !context.queueIntent() && !context.persistentActivity();
    }
}
