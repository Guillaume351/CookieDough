package com.cookiebuild.cookiedough.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CommandDeduplicatorTest {
    @Test
    void sharesOneExecutionAcrossRedeliveries() {
        CommandDeduplicator deduplicator = new CommandDeduplicator(Duration.ofMinutes(1));
        AtomicInteger executions = new AtomicInteger();

        CompletableFuture<AdminCommandResult> first = deduplicator.executeOnce("same", () -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(AdminCommandResult.completed(null));
        });
        CompletableFuture<AdminCommandResult> duplicate = deduplicator.executeOnce("same", () -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(AdminCommandResult.failed("must not run"));
        });

        assertSame(first, duplicate);
        assertEquals(1, executions.get());
        assertEquals(1, deduplicator.size());
    }
}
