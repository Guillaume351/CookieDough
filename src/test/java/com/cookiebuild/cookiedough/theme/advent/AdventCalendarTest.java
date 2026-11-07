package com.cookiebuild.cookiedough.theme.advent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AdventCalendarTest {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final AdventCalendar CALENDAR = new AdventCalendar("christmas-2026-advent", PARIS, 2026, 12, 1,
            24, 10, 50);

    private static Instant paris(String local) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(local), PARIS).toInstant();
    }

    @Test
    void eachDecemberDayMapsToItsGift() {
        assertEquals(Optional.of(1), CALENDAR.dayAt(paris("2026-12-01T00:00")));
        assertEquals(Optional.of(7), CALENDAR.dayAt(paris("2026-12-07T15:30")));
        assertEquals(Optional.of(24), CALENDAR.dayAt(paris("2026-12-24T23:59:59")));
        Set<String> ids = new HashSet<>();
        for (int day = 1; day <= 24; day++) {
            assertEquals(Optional.of(day), CALENDAR.dayAt(paris("2026-12-%02dT12:00".formatted(day))));
            ids.add(AdventCalendar.collectibleId(day));
        }
        assertEquals(24, ids.size());
        assertEquals("d01", AdventCalendar.collectibleId(1));
        assertEquals("d24", AdventCalendar.collectibleId(24));
        assertEquals("christmas-2026-advent:d05", CALENDAR.rewardKey(5));
    }

    @Test
    void tenCoinsADayAndFiftyOnChristmasEve() {
        for (int day = 1; day <= 23; day++) assertEquals(10, CALENDAR.coinsFor(day));
        assertEquals(50, CALENDAR.coinsFor(24));
        assertTrue(CALENDAR.isLastDay(24));
        assertFalse(CALENDAR.isLastDay(23));
        assertThrows(IllegalArgumentException.class, () -> CALENDAR.coinsFor(25));
        assertThrows(IllegalArgumentException.class, () -> CALENDAR.coinsFor(0));
    }

    @Test
    void closedBeforeTheFirstAndAfterTheTwentyFourth() {
        assertEquals(AdventCalendar.Phase.BEFORE, CALENDAR.phase(paris("2026-11-30T23:59:59")));
        assertTrue(CALENDAR.dayAt(paris("2026-11-30T23:59:59")).isEmpty());
        assertTrue(CALENDAR.dayAt(paris("2026-11-07T12:00")).isEmpty());
        assertEquals(AdventCalendar.Phase.AFTER, CALENDAR.phase(paris("2026-12-25T00:00")));
        assertTrue(CALENDAR.dayAt(paris("2026-12-25T00:00")).isEmpty());
        assertTrue(CALENDAR.dayAt(paris("2027-01-04T20:00")).isEmpty());
        assertTrue(CALENDAR.dayAt(paris("2027-12-10T20:00")).isEmpty(), "next year is another edition");
        assertEquals(AdventCalendar.Phase.OPEN, CALENDAR.phase(paris("2026-12-12T08:00")));
    }

    @Test
    void theDayChangesAtParisMidnightNotUtc() {
        // 30 Nov 23:30 UTC is already 1 December 00:30 in Paris (UTC+1).
        assertEquals(Optional.of(1), CALENDAR.dayAt(Instant.parse("2026-11-30T23:30:00Z")));
        assertTrue(CALENDAR.dayAt(Instant.parse("2026-11-30T22:59:59Z")).isEmpty());
        // 7 Dec 22:59:59 UTC is 23:59:59 in Paris; one second later it is the 8th.
        assertEquals(Optional.of(7), CALENDAR.dayAt(Instant.parse("2026-12-07T22:59:59Z")));
        assertEquals(Optional.of(8), CALENDAR.dayAt(Instant.parse("2026-12-07T23:00:00Z")));
        // 24 Dec 23:30 UTC is already Christmas Day in Paris: closed.
        assertTrue(CALENDAR.dayAt(Instant.parse("2026-12-24T23:30:00Z")).isEmpty());
        assertEquals(Optional.of(24), CALENDAR.dayAt(Instant.parse("2026-12-24T22:59:59Z")));
    }

    @Test
    void parsesTheThemeSectionAndRejectsBadValues() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader("""
                advent:
                  id: christmas-2026-advent
                  year: 2026
                  coins: 10
                  last-day-coins: 50
                """));
        assertEquals(CALENDAR, AdventCalendar.parse(yaml.getConfigurationSection("advent"), PARIS));
        assertEquals(null, AdventCalendar.parse(null, PARIS));
        assertThrows(IllegalArgumentException.class, () -> new AdventCalendar("Bad Id", PARIS, 2026, 12, 1, 24, 10, 50));
        assertThrows(IllegalArgumentException.class, () -> new AdventCalendar("a", PARIS, 2026, 12, 25, 24, 10, 50));
        assertThrows(RuntimeException.class, () -> new AdventCalendar("a", PARIS, 2026, 11, 1, 31, 10, 50));
        assertThrows(IllegalArgumentException.class, () -> new AdventCalendar("a", PARIS, 2026, 12, 1, 24, -1, 50));
    }
}
