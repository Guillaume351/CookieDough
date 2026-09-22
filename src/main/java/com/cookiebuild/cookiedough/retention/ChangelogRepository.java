package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.util.UUID;

public interface ChangelogRepository {
    ChangelogDigest findUnread(UUID playerId);

    void acknowledge(UUID playerId, Instant publishedAt, UUID postId);
}
