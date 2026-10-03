package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Server-list MOTD on Soirée Cookie days. The first line of the configured
 * MOTD (server.properties) is kept; only the second line is replaced, before
 * the window ("tonight") and while it runs. The text is plain ASCII French
 * (no accents) because a server ping has no player locale and some Bedrock
 * server lists render accents poorly.
 */
public final class SoireeMotdPolicy {
    public enum Phase {
        /** Not a Soirée day, or the evening is over: keep the static MOTD. */
        NONE,
        /** Later today. */
        TONIGHT,
        /** The window is running. */
        LIVE
    }

    private SoireeMotdPolicy() {
    }

    public static Phase phase(WeeklyEventSchedule schedule, Instant now) {
        if (schedule == null || now == null) return Phase.NONE;
        if (schedule.activeAt(now)) return Phase.LIVE;
        WeeklyEventSchedule.Occurrence next = schedule.next(now);
        LocalDate today = LocalDate.ofInstant(now, schedule.zone());
        return LocalDate.ofInstant(next.start(), schedule.zone()).equals(today) ? Phase.TONIGHT : Phase.NONE;
    }

    /** Replacement second line, or empty to keep the static MOTD. */
    public static Optional<String> secondLine(WeeklyEventSchedule schedule, Instant now) {
        return switch (phase(schedule, now)) {
            case NONE -> Optional.empty();
            case TONIGHT -> Optional.of("Ce soir " + hour(schedule.start()) + " : Soiree Cookie, pieces x"
                    + schedule.coinMultiplier());
            case LIVE -> Optional.of("Soiree Cookie EN COURS : pieces x" + schedule.coinMultiplier());
        };
    }

    /** "21h" or "21h30". */
    static String hour(LocalTime time) {
        return time.getHour() + "h" + (time.getMinute() == 0 ? "" : String.format(java.util.Locale.ROOT, "%02d", time.getMinute()));
    }

    /** First line of a (legacy-serialized) MOTD; a one-line MOTD is returned unchanged. */
    public static String firstLine(String motd) {
        if (motd == null) return "";
        int newline = motd.indexOf('\n');
        return newline < 0 ? motd : motd.substring(0, newline);
    }
}
