package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SoireeMotdPolicyTest {
    private static final WeeklyEventSchedule SOIREE = WeeklyEventSchedule.parse(
            List.of("WEDNESDAY", "SATURDAY"), "21:00", 60, "Europe/Paris", 2);

    @Test
    void announcesTonightThenLiveAndOtherwiseKeepsTheStaticMotd() {
        // Saturday 3 Oct 2026 (CEST): 21:00 Paris = 19:00 UTC.
        assertEquals(Optional.of("Ce soir 21h : Soiree Cookie, pieces x2"),
                SoireeMotdPolicy.secondLine(SOIREE, Instant.parse("2026-10-03T08:00:00Z")));
        assertEquals(Optional.of("Ce soir 21h : Soiree Cookie, pieces x2"),
                SoireeMotdPolicy.secondLine(SOIREE, Instant.parse("2026-10-03T18:59:59Z")));
        assertEquals(Optional.of("Soiree Cookie EN COURS : pieces x2"),
                SoireeMotdPolicy.secondLine(SOIREE, Instant.parse("2026-10-03T19:00:00Z")));
        assertEquals(SoireeMotdPolicy.Phase.LIVE,
                SoireeMotdPolicy.phase(SOIREE, Instant.parse("2026-10-03T19:59:59Z")));
        assertEquals(Optional.empty(), SoireeMotdPolicy.secondLine(SOIREE, Instant.parse("2026-10-03T20:00:00Z")));
        // Friday and Sunday are ordinary days.
        assertEquals(Optional.empty(), SoireeMotdPolicy.secondLine(SOIREE, Instant.parse("2026-10-02T18:00:00Z")));
        assertEquals(Optional.empty(), SoireeMotdPolicy.secondLine(SOIREE, Instant.parse("2026-10-04T18:00:00Z")));
        assertEquals(Optional.empty(), SoireeMotdPolicy.secondLine(null, Instant.now()));
    }

    @Test
    void linesAreAsciiAndOnlyTheSecondLineIsReplaced() {
        for (String instant : List.of("2026-10-03T08:00:00Z", "2026-10-03T19:30:00Z")) {
            String line = SoireeMotdPolicy.secondLine(SOIREE, Instant.parse(instant)).orElseThrow();
            assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(line), line);
        }
        assertEquals("§6Cookie Build", SoireeMotdPolicy.firstLine("§6Cookie Build\n§7Old line two"));
        assertEquals("Cookie Build", SoireeMotdPolicy.firstLine("Cookie Build"));
        assertEquals("", SoireeMotdPolicy.firstLine(null));
        assertEquals("21h", SoireeMotdPolicy.hour(LocalTime.of(21, 0)));
        assertEquals("20h30", SoireeMotdPolicy.hour(LocalTime.of(20, 30)));
    }
}
