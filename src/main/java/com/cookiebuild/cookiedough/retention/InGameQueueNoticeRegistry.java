package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Main-thread registry for deduplicated, expiring queue invitations. */
final class InGameQueueNoticeRegistry {
    static final Duration LIFETIME = Duration.ofMinutes(2);
    static final Duration GAME_COOLDOWN = Duration.ofMinutes(5);

    record Notice(UUID id, UUID gameId, String gameName, long expiresAtMillis, Set<UUID> recipients) {
        Notice {
            recipients = Set.copyOf(recipients);
        }
    }

    private final Map<UUID, Notice> activeByGame = new HashMap<>();
    private final Map<UUID, Notice> activeById = new HashMap<>();
    private final Map<UUID, Long> lastPublishedAt = new HashMap<>();
    private final Set<String> accepted = new HashSet<>();

    Optional<Notice> publish(UUID gameId, String gameName, Set<UUID> recipients, long nowMillis) {
        expire(nowMillis);
        Long lastPublished = lastPublishedAt.get(gameId);
        if (gameId == null || gameName == null || gameName.isBlank() || recipients == null || recipients.isEmpty()
                || activeByGame.containsKey(gameId)
                || lastPublished != null && nowMillis - lastPublished < GAME_COOLDOWN.toMillis()) {
            return Optional.empty();
        }
        Notice notice = new Notice(UUID.randomUUID(), gameId, gameName,
                nowMillis + LIFETIME.toMillis(), recipients);
        activeByGame.put(gameId, notice);
        activeById.put(notice.id(), notice);
        lastPublishedAt.put(gameId, nowMillis);
        return Optional.of(notice);
    }

    Optional<Notice> accept(UUID noticeId, UUID playerId, long nowMillis) {
        expire(nowMillis);
        Notice notice = activeById.get(noticeId);
        if (notice == null || playerId == null || !notice.recipients().contains(playerId)
                || !accepted.add(noticeId + ":" + playerId)) {
            return Optional.empty();
        }
        return Optional.of(notice);
    }

    Optional<Notice> includeRecipient(UUID gameId, String gameName, UUID recipient, long nowMillis) {
        expire(nowMillis);
        Notice active = activeByGame.get(gameId);
        if (active == null) return publish(gameId, gameName, Set.of(recipient), nowMillis);
        if (active.recipients().contains(recipient)) return Optional.empty();
        Set<UUID> recipients = new HashSet<>(active.recipients());
        recipients.add(recipient);
        Notice extended = new Notice(active.id(), active.gameId(), active.gameName(),
                active.expiresAtMillis(), recipients);
        activeByGame.put(gameId, extended);
        activeById.put(active.id(), extended);
        return Optional.of(extended);
    }

    void expire(long nowMillis) {
        for (Notice notice : Set.copyOf(activeById.values())) {
            if (nowMillis < notice.expiresAtMillis()) continue;
            activeById.remove(notice.id(), notice);
            activeByGame.remove(notice.gameId(), notice);
            accepted.removeIf(value -> value.startsWith(notice.id() + ":"));
        }
    }
}
