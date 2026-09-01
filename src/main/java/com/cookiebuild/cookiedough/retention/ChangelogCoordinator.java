package com.cookiebuild.cookiedough.retention;

import java.util.Objects;
import java.util.UUID;

public final class ChangelogCoordinator {
    private final ChangelogRepository repository;

    public ChangelogCoordinator(ChangelogRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public ChangelogDigest unread(UUID playerId) {
        return repository.findUnread(playerId);
    }

    public void acknowledge(UUID playerId, ChangelogDigest digest) {
        if (!digest.isEmpty()) {
            repository.acknowledge(playerId, digest.cursorPublishedAt(), digest.cursorPostId());
        }
    }
}
