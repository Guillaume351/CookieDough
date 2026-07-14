package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class RallyQueueTrackerTest {
    @Test
    void automaticRallyRequiresNinetyContinuousUnderfilledSeconds() {
        RallyQueueTracker tracker = new RallyQueueTracker();
        UUID gameId = UUID.randomUUID();
        RallyQueueTracker.QueueState underfilled = queue(gameId, true, 1, 2);

        assertTrue(tracker.observe(List.of(underfilled), 0).automaticCandidates().isEmpty());
        assertTrue(tracker.observe(List.of(underfilled), 89_999).automaticCandidates().isEmpty());
        assertEquals(List.of(gameId), tracker.observe(List.of(underfilled), 90_000).automaticCandidates());
    }

    @Test
    void emptyFilledOrStartedQueueCancelsAWaitingOutbox() {
        RallyQueueTracker tracker = new RallyQueueTracker();
        UUID gameId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        tracker.observe(List.of(queue(gameId, true, 1, 2)), 0);
        tracker.markScheduled(gameId, outboxId, 15_000, 300_000);

        RallyQueueTracker.Observation empty = tracker.observe(List.of(queue(gameId, true, 0, 2)), 1_000);

        assertEquals(1, empty.cancellations().size());
        assertEquals(outboxId, empty.cancellations().getFirst().outboxId());
        assertFalse(tracker.hasPending(gameId));

        tracker.observe(List.of(queue(gameId, true, 1, 2)), 2_000);
        tracker.markScheduled(gameId, UUID.randomUUID(), 20_000, 300_000);
        assertEquals(1, tracker.observe(List.of(queue(gameId, false, 1, 2)), 3_000).cancellations().size());

        tracker.observe(List.of(queue(gameId, true, 1, 2)), 4_000);
        tracker.markScheduled(gameId, UUID.randomUUID(), 20_000, 300_000);
        assertEquals(1, tracker.observe(List.of(queue(gameId, true, 2, 2)), 5_000).cancellations().size());
    }

    @Test
    void aQueueThatRecoversMustWaitAnotherFullWindow() {
        RallyQueueTracker tracker = new RallyQueueTracker();
        UUID gameId = UUID.randomUUID();
        tracker.observe(List.of(queue(gameId, true, 1, 2)), 0);
        tracker.observe(List.of(queue(gameId, true, 2, 2)), 60_000);

        assertTrue(tracker.observe(List.of(queue(gameId, true, 1, 2)), 61_000).automaticCandidates().isEmpty());
        assertTrue(tracker.observe(List.of(queue(gameId, true, 1, 2)), 150_999).automaticCandidates().isEmpty());
        assertEquals(List.of(gameId),
                tracker.observe(List.of(queue(gameId, true, 1, 2)), 151_000).automaticCandidates());
    }

    @Test
    void producerPayloadIsStrictStructuredAndContainsNoFreeNotificationCopy() throws Exception {
        UUID rallyId = UUID.randomUUID();
        RallyRepository.Request request = new RallyRepository.Request(
                rallyId, RallyRepository.Source.PLAYER, "microbattles", 1, 1, "CookieFan");

        Map<String, Object> payload = new ObjectMapper().readValue(
                PostgresRallyRepository.payload(request), new TypeReference<>() {
                });

        assertEquals(Set.of("schemaVersion", "rallyId", "source", "gamemode", "edition",
                "queuedCount", "neededCount", "actorDisplayName"), payload.keySet());
        assertEquals(rallyId.toString(), payload.get("rallyId"));
        assertEquals("crossplay", payload.get("edition"));
        assertEquals(1, payload.get("neededCount"));
        assertFalse(payload.containsKey("title"));
        assertFalse(payload.containsKey("body"));
        assertFalse(payload.containsKey("deepLink"));
    }

    @Test
    void requestContractRejectsUnsupportedOrUnattributedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new RallyRepository.Request(
                UUID.randomUUID(), RallyRepository.Source.PLAYER, "turfwars", 1, 1, "CookieFan"));
        assertThrows(IllegalArgumentException.class, () -> new RallyRepository.Request(
                UUID.randomUUID(), RallyRepository.Source.PLAYER, "pitchout", 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new RallyRepository.Request(
                UUID.randomUUID(), RallyRepository.Source.AUTOMATIC, "pitchout", 1, 1, "CookieFan"));
        assertThrows(IllegalArgumentException.class, () -> new RallyRepository.Request(
                UUID.randomUUID(), RallyRepository.Source.PLAYER, "pitchout", 1, 1, "hello\nplayers"));
    }

    @Test
    void publicPlayerNameMatchesTheBackendSafetyPattern() {
        assertEquals("Cookie_Fan.42", RallyManager.sanitizePlayerName(" Cookie_Fan.42 "));
        assertEquals("Bedrock_Player", RallyManager.sanitizePlayerName("Bedrock§Player"));
        assertEquals("_", RallyManager.sanitizePlayerName("§"));
        assertTrue(RallyRepository.ACTOR_DISPLAY_NAME_PATTERN
                .matcher(RallyManager.sanitizePlayerName("Joueur français"))
                .matches());
    }

    @Test
    void gamemodeNormalizationMatchesTheBackendContract() {
        assertEquals("microbattles", RallyManager.normalizeGamemode("Micro Battles"));
        assertEquals("buildbattles", RallyManager.normalizeGamemode("BuildBattles"));
        assertEquals("skywars", RallyManager.normalizeGamemode("SKY-WARS"));
        assertEquals(null, RallyManager.normalizeGamemode("TurfWars"));
    }

    private static RallyQueueTracker.QueueState queue(
            UUID gameId, boolean open, int queued, int minimum) {
        return new RallyQueueTracker.QueueState(gameId, open, queued, minimum);
    }
}
