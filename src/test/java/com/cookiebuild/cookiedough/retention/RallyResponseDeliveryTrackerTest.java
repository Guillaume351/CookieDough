package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RallyResponseDeliveryTrackerTest {
    @Test
    void responseWaitsForTheTargetAndIsDisplayedOnceUntilAcknowledged() {
        RallyResponseDeliveryTracker tracker = new RallyResponseDeliveryTracker();
        UUID onlineTarget = UUID.randomUUID();
        UUID offlineTarget = UUID.randomUUID();
        RallyRepository.Response online = response(onlineTarget);
        RallyRepository.Response offline = response(offlineTarget);

        List<RallyResponseDeliveryTracker.Delivery> first = tracker.ready(
                List.of(online, offline), onlineTarget::equals);

        assertEquals(1, first.size());
        assertEquals(online.id(), first.getFirst().response().id());
        assertTrue(first.getFirst().firstDisplay());

        List<RallyResponseDeliveryTracker.Delivery> beforeAck = tracker.ready(
                List.of(online), ignored -> true);
        assertFalse(beforeAck.getFirst().firstDisplay());

        tracker.acknowledged(online.id());
        assertTrue(tracker.ready(List.of(online), ignored -> true).getFirst().firstDisplay());
    }

    private static RallyRepository.Response response(UUID targetPlayerId) {
        return new RallyRepository.Response(
                UUID.randomUUID(),
                UUID.randomUUID(),
                targetPlayerId,
                UUID.randomUUID(),
                "CookieFan",
                RallyRepository.ResponseKind.JOINING,
                "microbattles");
    }
}
