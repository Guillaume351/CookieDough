package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class WeeklyEventScheduleTest {
    private static final WeeklyEventSchedule SOIREE = WeeklyEventSchedule.parse(
            List.of("WEDNESDAY", "saturday"), "21:00", 60, "Europe/Paris", 2);

    @Test
    void nextOccurrenceIsWednesdayOrSaturdayAtNinePmParis() {
        // Wednesday 30 Sep 2026, 12:00 UTC -> same day 21:00 CEST = 19:00 UTC.
        WeeklyEventSchedule.Occurrence next = SOIREE.next(Instant.parse("2026-09-30T12:00:00Z"));
        assertEquals(Instant.parse("2026-09-30T19:00:00Z"), next.start());
        assertEquals(Instant.parse("2026-09-30T20:00:00Z"), next.end());
        // After Wednesday's event, the next one is Saturday 3 Oct.
        assertEquals(Instant.parse("2026-10-03T19:00:00Z"),
                SOIREE.next(Instant.parse("2026-09-30T19:30:00Z")).start());
    }

    @Test
    void handlesTheSwitchToWinterTime() {
        // Saturday 31 Oct 2026: CET (UTC+1) after 25 Oct -> 21:00 Paris = 20:00 UTC.
        assertEquals(Instant.parse("2026-10-31T20:00:00Z"),
                SOIREE.next(Instant.parse("2026-10-29T12:00:00Z")).start());
    }

    @Test
    void multiplierOnlyAppliesDuringTheWindow() {
        assertEquals(1, SOIREE.multiplierAt(Instant.parse("2026-09-30T18:59:59Z")));
        assertEquals(2, SOIREE.multiplierAt(Instant.parse("2026-09-30T19:00:00Z")));
        assertEquals(2, SOIREE.multiplierAt(Instant.parse("2026-09-30T19:59:59Z")));
        assertEquals(1, SOIREE.multiplierAt(Instant.parse("2026-09-30T20:00:00Z")));
        assertEquals(1, SOIREE.multiplierAt(Instant.parse("2026-10-01T19:30:00Z")));
    }

    @Test
    void announcementsAtThirtyAndFiveMinutesAndStart() {
        WeeklyEventSchedule.Occurrence next = SOIREE.next(Instant.parse("2026-09-30T12:00:00Z"));
        assertEquals(Optional.empty(), SOIREE.dueAnnouncement(next, Instant.parse("2026-09-30T18:29:00Z")));
        assertEquals(Optional.of(WeeklyEventSchedule.Phase.THIRTY_MINUTES),
                SOIREE.dueAnnouncement(next, Instant.parse("2026-09-30T18:30:00Z")));
        assertEquals(Optional.of(WeeklyEventSchedule.Phase.FIVE_MINUTES),
                SOIREE.dueAnnouncement(next, Instant.parse("2026-09-30T18:55:00Z")));
        WeeklyEventSchedule.Occurrence live = SOIREE.current(Instant.parse("2026-09-30T19:02:00Z")).orElseThrow();
        assertEquals(Optional.of(WeeklyEventSchedule.Phase.STARTED),
                SOIREE.dueAnnouncement(live, Instant.parse("2026-09-30T19:02:00Z")));
        assertEquals(Optional.empty(), SOIREE.dueAnnouncement(live, Instant.parse("2026-09-30T19:30:00Z")));
        assertFalse(WeeklyEventSchedule.announcementKey(next, WeeklyEventSchedule.Phase.THIRTY_MINUTES)
                .equals(WeeklyEventSchedule.announcementKey(next, WeeklyEventSchedule.Phase.FIVE_MINUTES)));
    }

    @Test
    void rejectsInvalidConfigurationAndCapsTheMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> WeeklyEventSchedule.parse(List.of(), "21:00", 60, "Europe/Paris", 2));
        assertThrows(RuntimeException.class,
                () -> WeeklyEventSchedule.parse(List.of("FUNDAY"), "21:00", 60, "Europe/Paris", 2));
        assertEquals(5, WeeklyEventSchedule.parse(List.of("MONDAY"), "21:00", 60, "Europe/Paris", 50)
                .coinMultiplier());
        assertTrue(SOIREE.activeAt(Instant.parse("2026-10-03T19:30:00Z")));
    }
}
