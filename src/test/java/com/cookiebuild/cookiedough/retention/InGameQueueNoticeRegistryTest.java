package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InGameQueueNoticeRegistryTest {
    @Test
    void invitationIsRecipientScopedIdempotentAndExpiring() {
        InGameQueueNoticeRegistry registry = new InGameQueueNoticeRegistry();
        UUID gameId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        InGameQueueNoticeRegistry.Notice notice = registry.publish(
                gameId, "BedWars", Set.of(recipient), 1_000L).orElseThrow();

        assertTrue(registry.accept(notice.id(), outsider, 1_001L).isEmpty());
        assertEquals(gameId, registry.accept(notice.id(), recipient, 1_001L).orElseThrow().gameId());
        assertTrue(registry.accept(notice.id(), recipient, 1_002L).isEmpty());
        assertTrue(registry.accept(notice.id(), recipient,
                1_000L + InGameQueueNoticeRegistry.LIFETIME.toMillis()).isEmpty());
    }

    @Test
    void publicationIsDeduplicatedAndCooldownProtected() {
        InGameQueueNoticeRegistry registry = new InGameQueueNoticeRegistry();
        UUID gameId = UUID.randomUUID();
        Set<UUID> recipients = Set.of(UUID.randomUUID());

        assertTrue(registry.publish(gameId, "SkyWars", recipients, 10_000L).isPresent());
        assertTrue(registry.publish(gameId, "SkyWars", recipients, 10_001L).isEmpty());
        registry.expire(10_000L + InGameQueueNoticeRegistry.LIFETIME.toMillis());
        assertTrue(registry.publish(gameId, "SkyWars", recipients,
                10_000L + InGameQueueNoticeRegistry.GAME_COOLDOWN.toMillis() - 1L).isEmpty());
        assertTrue(registry.publish(gameId, "SkyWars", recipients,
                10_000L + InGameQueueNoticeRegistry.GAME_COOLDOWN.toMillis()).isPresent());
    }

    @Test
    void postMatchRecipientJoinsTheExistingInvitationWithoutResettingItsLifetime() {
        InGameQueueNoticeRegistry registry = new InGameQueueNoticeRegistry();
        UUID gameId = UUID.randomUUID();
        UUID waiting = UUID.randomUUID();
        UUID justFinished = UUID.randomUUID();
        InGameQueueNoticeRegistry.Notice original = registry.publish(
                gameId, "BedWars", Set.of(waiting), 20_000L).orElseThrow();

        InGameQueueNoticeRegistry.Notice extended = registry.includeRecipient(
                gameId, "BedWars", justFinished, 25_000L).orElseThrow();

        assertEquals(original.id(), extended.id());
        assertEquals(original.expiresAtMillis(), extended.expiresAtMillis());
        assertTrue(extended.recipients().containsAll(Set.of(waiting, justFinished)));
        assertTrue(registry.accept(extended.id(), justFinished, 25_001L).isPresent());
    }
}
