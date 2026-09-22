package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChangelogDigest(
        List<ChangelogEntry> entries,
        int totalUnread,
        Instant cursorPublishedAt,
        UUID cursorPostId) {

    public ChangelogDigest {
        entries = List.copyOf(entries);
        totalUnread = Math.max(entries.size(), totalUnread);
        if (entries.isEmpty()) {
            cursorPublishedAt = null;
            cursorPostId = null;
        }
    }

    public static ChangelogDigest empty() {
        return new ChangelogDigest(List.of(), 0, null, null);
    }

    public int hiddenCount() {
        return Math.max(0, totalUnread - entries.size());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
