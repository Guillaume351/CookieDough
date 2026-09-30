package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ParisCalendarTest {
    @Test
    void dayChangesAtParisMidnightNotUtc() {
        // 22:30 UTC on 30 Sep 2026 is already 00:30 on 1 Oct in Paris (CEST, UTC+2).
        Clock lateEvening = Clock.fixed(Instant.parse("2026-09-30T22:30:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.parse("2026-10-01"), ParisCalendar.today(lateEvening));
        // Winter time (CET, UTC+1).
        Clock winter = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.parse("2027-01-01"), ParisCalendar.today(winter));
    }

    @Test
    void weekKeysUseIsoWeeksOfTheParisDay() {
        assertEquals("2026-W40", ParisCalendar.weekKey(LocalDate.parse("2026-09-28")));
        assertEquals("2026-W53", ParisCalendar.weekKey(LocalDate.parse("2027-01-01")));
        assertEquals(Instant.parse("2026-09-30T22:00:00Z"),
                ParisCalendar.nextMidnight(Instant.parse("2026-09-30T12:00:00Z")));
    }
}
