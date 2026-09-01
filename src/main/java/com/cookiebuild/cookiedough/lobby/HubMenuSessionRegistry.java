package com.cookiebuild.cookiedough.lobby;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Exact, single-use sessions for delayed Bedrock hub form responses. */
public final class HubMenuSessionRegistry {
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public UUID issue(UUID playerId, String scope) {
        if (playerId == null || scope == null || scope.isBlank()) throw new IllegalArgumentException("scope required");
        UUID nonce = UUID.randomUUID();
        sessions.put(playerId, new Session(nonce, scope));
        return nonce;
    }

    public boolean consume(UUID playerId, UUID nonce, String scope) {
        return playerId != null && nonce != null && scope != null
                && sessions.remove(playerId, new Session(nonce, scope));
    }

    public void invalidate(UUID playerId, UUID nonce, String scope) {
        if (playerId != null && nonce != null && scope != null) {
            sessions.remove(playerId, new Session(nonce, scope));
        }
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) sessions.remove(playerId);
    }

    int size() { return sessions.size(); }
    private record Session(UUID nonce, String scope) { }
}
