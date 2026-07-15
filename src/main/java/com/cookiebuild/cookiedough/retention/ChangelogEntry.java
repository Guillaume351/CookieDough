package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.util.UUID;

public record ChangelogEntry(UUID id, String title, String summary, Instant publishedAt) {
    public ChangelogEntry {
        title = clean(title, 160);
        summary = clean(summary, 500);
    }

    private static String clean(String value, int maxLength) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }
}
