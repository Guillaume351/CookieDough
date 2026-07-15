package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChangelogCoordinatorTest {
    @Test
    void showsEveryUnreadEntryAndAcknowledgesTheNewestCursor() {
        FakeRepository repository = new FakeRepository();
        ChangelogCoordinator coordinator = new ChangelogCoordinator(repository);

        ChangelogDigest digest = coordinator.unread(repository.playerId);

        assertEquals(3, digest.entries().size());
        assertEquals(0, digest.hiddenCount());
        coordinator.acknowledge(repository.playerId, digest);
        assertEquals(repository.newest.id(), repository.acknowledgedPostId);
        assertEquals(repository.newest.publishedAt(), repository.acknowledgedAt);
    }

    @Test
    void sanitizesDatabaseTextAndKeepsDigestEntriesImmutable() {
        ChangelogEntry entry = new ChangelogEntry(
                UUID.randomUUID(),
                "  Hello\nplayers  ",
                "A\r\nsummary",
                Instant.parse("2026-07-15T08:00:00Z"));
        List<ChangelogEntry> mutable = new ArrayList<>(List.of(entry));
        ChangelogDigest digest = new ChangelogDigest(mutable, 1, entry.publishedAt(), entry.id());
        mutable.clear();

        assertEquals("Hello players", digest.entries().getFirst().title());
        assertEquals("A  summary", digest.entries().getFirst().summary());
        assertThrows(UnsupportedOperationException.class, () -> digest.entries().clear());
    }

    private static final class FakeRepository implements ChangelogRepository {
        private final UUID playerId = UUID.randomUUID();
        private final ChangelogEntry newest = new ChangelogEntry(
                UUID.randomUUID(), "Newest", "Latest changes", Instant.parse("2026-07-15T08:00:00Z"));
        private Instant acknowledgedAt;
        private UUID acknowledgedPostId;

        @Override
        public ChangelogDigest findUnread(UUID ignored) {
            ChangelogEntry second = new ChangelogEntry(
                    UUID.randomUUID(), "Second", "Second update", Instant.parse("2026-07-14T08:00:00Z"));
            ChangelogEntry third = new ChangelogEntry(
                    UUID.randomUUID(), "Third", "Third update", Instant.parse("2026-07-13T08:00:00Z"));
            return new ChangelogDigest(List.of(newest, second, third), 3, newest.publishedAt(), newest.id());
        }

        @Override
        public void acknowledge(UUID ignored, Instant publishedAt, UUID postId) {
            acknowledgedAt = publishedAt;
            acknowledgedPostId = postId;
        }
    }
}
