package com.cookiebuild.cookiedough.admin;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Shares one execution future across RabbitMQ redeliveries and duplicate keys. */
final class CommandDeduplicator {
    private record Entry(long expiresAtMillis, CompletableFuture<AdminCommandResult> result) { }

    private final long retentionMillis;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    CommandDeduplicator(Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Deduplication retention must be positive");
        }
        retentionMillis = retention.toMillis();
    }

    CompletableFuture<AdminCommandResult> executeOnce(
            String key, Supplier<CompletableFuture<AdminCommandResult>> execution) {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        return entries.compute(key, (ignored, existing) -> {
            if (existing != null && existing.expiresAtMillis() > now) return existing;
            CompletableFuture<AdminCommandResult> result;
            try {
                result = execution.get();
            } catch (RuntimeException error) {
                result = CompletableFuture.completedFuture(AdminCommandResult.failed(error.getMessage()));
            }
            return new Entry(now + retentionMillis, result);
        }).result();
    }

    int size() {
        return entries.size();
    }
}
