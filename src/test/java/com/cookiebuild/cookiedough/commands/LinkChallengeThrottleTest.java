package com.cookiebuild.cookiedough.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class LinkChallengeThrottleTest {
    @Test
    void preventsConcurrentAndRapidDuplicateSupportCodes() {
        LinkChallengeThrottle throttle = new LinkChallengeThrottle(30_000L);
        UUID playerId = UUID.randomUUID();

        assertEquals(LinkChallengeThrottle.Decision.ALLOWED, throttle.begin(playerId, 10_000L));
        assertEquals(LinkChallengeThrottle.Decision.IN_FLIGHT, throttle.begin(playerId, 10_001L));
        throttle.complete(playerId);
        assertEquals(LinkChallengeThrottle.Decision.COOLDOWN, throttle.begin(playerId, 39_999L));
        assertEquals(LinkChallengeThrottle.Decision.ALLOWED, throttle.begin(playerId, 40_000L));
    }

    @Test
    void differentPlayersDoNotBlockEachOther() {
        LinkChallengeThrottle throttle = new LinkChallengeThrottle(30_000L);

        assertEquals(LinkChallengeThrottle.Decision.ALLOWED,
                throttle.begin(UUID.randomUUID(), 10_000L));
        assertEquals(LinkChallengeThrottle.Decision.ALLOWED,
                throttle.begin(UUID.randomUUID(), 10_000L));
    }
}
