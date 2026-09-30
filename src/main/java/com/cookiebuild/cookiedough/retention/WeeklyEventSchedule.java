package com.cookiebuild.cookiedough.retention;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pure weekly recurring community event ("Soirée Cookie"): fixed days, a
 * local start time and a duration in a named time zone (DST-safe).
 */
public record WeeklyEventSchedule(Set<DayOfWeek> days, LocalTime start, Duration duration, ZoneId zone,
        int coinMultiplier) {
    public enum Phase {
        THIRTY_MINUTES,
        FIVE_MINUTES,
        STARTED
    }

    public record Occurrence(Instant start, Instant end) {
        public boolean activeAt(Instant now) {
            return !now.isBefore(start) && now.isBefore(end);
        }
    }

    private static final Duration START_ANNOUNCEMENT_WINDOW = Duration.ofMinutes(10);

    public WeeklyEventSchedule {
        if (days == null || days.isEmpty()) throw new IllegalArgumentException("At least one day is required");
        if (start == null || zone == null) throw new IllegalArgumentException("Start time and zone are required");
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(12)) > 0) {
            throw new IllegalArgumentException("Duration must be between 1 minute and 12 hours");
        }
        days = Set.copyOf(EnumSet.copyOf(days));
        coinMultiplier = Math.max(1, Math.min(coinMultiplier, 5));
    }

    public static WeeklyEventSchedule parse(List<String> dayNames, String startText, int durationMinutes,
            String zoneId, int coinMultiplier) {
        EnumSet<DayOfWeek> parsedDays = EnumSet.noneOf(DayOfWeek.class);
        for (String name : dayNames) {
            parsedDays.add(DayOfWeek.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        }
        return new WeeklyEventSchedule(parsedDays, LocalTime.parse(startText.trim()),
                Duration.ofMinutes(durationMinutes), ZoneId.of(zoneId), coinMultiplier);
    }

    /** The occurrence running at {@code now}, if any. */
    public Optional<Occurrence> current(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        for (LocalDate day : List.of(today.minusDays(1), today)) {
            if (!days.contains(day.getDayOfWeek())) continue;
            Occurrence occurrence = occurrenceOn(day);
            if (occurrence.activeAt(now)) return Optional.of(occurrence);
        }
        return Optional.empty();
    }

    /** The next occurrence starting strictly after {@code now}. */
    public Occurrence next(Instant now) {
        LocalDate day = LocalDate.ofInstant(now, zone);
        for (int offset = 0; offset <= 8; offset++) {
            LocalDate candidate = day.plusDays(offset);
            if (!days.contains(candidate.getDayOfWeek())) continue;
            Occurrence occurrence = occurrenceOn(candidate);
            if (occurrence.start().isAfter(now)) return occurrence;
        }
        throw new IllegalStateException("A weekly schedule always has a next occurrence");
    }

    public boolean activeAt(Instant now) {
        return current(now).isPresent();
    }

    /** The multiplier to apply to match coins at {@code now}. */
    public int multiplierAt(Instant now) {
        return activeAt(now) ? coinMultiplier : 1;
    }

    /**
     * Announcement due at {@code now} for the relevant occurrence. Callers
     * deduplicate by {@link #announcementKey(Occurrence, Phase)} so a phase is
     * sent at most once even if the timer runs several times in its window.
     */
    public Optional<Phase> dueAnnouncement(Occurrence occurrence, Instant now) {
        Duration until = Duration.between(now, occurrence.start());
        if (!until.isNegative() && !until.isZero()) {
            if (until.compareTo(Duration.ofMinutes(5)) <= 0) return Optional.of(Phase.FIVE_MINUTES);
            if (until.compareTo(Duration.ofMinutes(30)) <= 0) return Optional.of(Phase.THIRTY_MINUTES);
            return Optional.empty();
        }
        if (occurrence.activeAt(now) && Duration.between(occurrence.start(), now)
                .compareTo(START_ANNOUNCEMENT_WINDOW) < 0) {
            return Optional.of(Phase.STARTED);
        }
        return Optional.empty();
    }

    public static String announcementKey(Occurrence occurrence, Phase phase) {
        return occurrence.start().toEpochMilli() + ":" + phase.name();
    }

    private Occurrence occurrenceOn(LocalDate day) {
        Instant startsAt = day.atTime(start).atZone(zone).toInstant();
        return new Occurrence(startsAt, startsAt.plus(duration));
    }
}
