package com.cookiebuild.cookiedough.ui;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Single-use, latest-only response token for Cumulus menus owned by a player. */
public final class BedrockMenuSessionRegistry {
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public UUID issue(UUID playerId, String scope) {
        UUID nonce = UUID.randomUUID();
        sessions.put(playerId, new Session(nonce, scope));
        return nonce;
    }

    public boolean consume(UUID playerId, UUID nonce, String scope) {
        return sessions.remove(playerId, new Session(nonce, scope));
    }

    public void invalidate(UUID playerId, UUID nonce, String scope) {
        sessions.remove(playerId, new Session(nonce, scope));
    }

    public void invalidate(UUID playerId) {
        sessions.remove(playerId);
    }

    int size() {
        return sessions.size();
    }

    private record Session(UUID nonce, String scope) { }
}
