package com.cookiebuild.cookiedough.retention;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;

/**
 * Player-facing calendar. Cookie Build is a French community, so daily and
 * weekly resets happen at midnight in Europe/Paris rather than UTC.
 */
public final class ParisCalendar {
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private ParisCalendar() {
    }

    public static LocalDate today() {
        return today(Clock.systemUTC());
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    public static LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** ISO week key such as {@code 2026-W40}, computed on the Paris calendar day. */
    public static String weekKey(LocalDate parisDay) {
        WeekFields iso = WeekFields.ISO;
        return parisDay.get(iso.weekBasedYear()) + "-W"
                + String.format("%02d", parisDay.get(iso.weekOfWeekBasedYear()));
    }

    public static Instant nextMidnight(Instant now) {
        return dayOf(now).plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}
