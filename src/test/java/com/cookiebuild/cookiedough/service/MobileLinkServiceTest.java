package com.cookiebuild.cookiedough.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MobileLinkServiceTest {
    private static final String PEPPER = "a-cookie-build-test-pepper-with-32-chars";

    @Test
    void generatedCodesAvoidAmbiguousCharacters() {
        String code = MobileLinkService.generateCode(new SecureRandom());

        assertEquals(8, code.length());
        assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}"));
        assertFalse(code.matches(".*[01IO].*"));
    }

    @Test
    void hashesAreCaseInsensitiveAndPepperSpecific() {
        String first = MobileLinkService.hmacHex("ABCD2345", PEPPER);

        assertEquals(first, MobileLinkService.hmacHex("abcd2345", PEPPER));
        assertEquals(64, first.length());
        assertNotEquals(first, MobileLinkService.hmacHex("ABCD2345", PEPPER + "-different"));
    }

    @Test
    void rejectsKnownExamplePeppers() {
        assertFalse(MobileLinkService.isValidPepper(null));
        assertFalse(MobileLinkService.isValidPepper("replace-with-a-long-random-secret"));
        assertFalse(MobileLinkService.isValidPepper("change-me-change-me-change-me-change-me"));
        assertTrue(MobileLinkService.isValidPepper(PEPPER));
    }

    @Test
    void challengePurposesAreDisjointAndExpiryRemainsTenMinutes() {
        assertEquals("mobile_link", MobileLinkService.Purpose.MOBILE_LINK.wireValue());
        assertEquals("commerce_session", MobileLinkService.Purpose.COMMERCE_SESSION.wireValue());
        assertEquals(Duration.ofMinutes(10), MobileLinkService.CHALLENGE_TTL);
    }

    @Test
    void codeReservationRetriesAHashCollision() {
        AtomicInteger reservations = new AtomicInteger();

        String code = MobileLinkService.generateUniqueCode(new SecureRandom(),
                ignored -> reservations.incrementAndGet() == 2);

        assertEquals(2, reservations.get());
        assertEquals(8, code.length());
    }
}
