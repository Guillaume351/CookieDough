package com.cookiebuild.cookiedough.theme.advent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;

/**
 * An advent calendar: one gift a day from {@code firstDay} to {@code lastDay}
 * of {@code month}/{@code year}, by the calendar date in {@code zone}. A gift
 * can only be opened on its own date; a missed day is lost. Pure date logic,
 * no Bukkit state.
 *
 * <p>Each opening is the row ({@code id}, {@code dNN}) of
 * {@code seasonal_collectibles}, and its coins use the ledger key
 * {@code id:dNN} (coin source {@code goal:theme:<id>:dNN}).
 */
public record AdventCalendar(String id, ZoneId zone, int year, int month, int firstDay, int lastDay, int coins,
        int lastDayCoins) {
    public AdventCalendar {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Bad advent id " + id);
        if (zone == null) throw new IllegalArgumentException("Advent calendar needs a time zone");
        if (month < 1 || month > 12 || firstDay < 1 || lastDay < firstDay || lastDay > 31) {
            throw new IllegalArgumentException("Bad advent days " + firstDay + ".." + lastDay + "/" + month);
        }
        LocalDate.of(year, month, lastDay); // rejects 31 November and friends
        if (coins < 0 || lastDayCoins < 0) throw new IllegalArgumentException("Negative advent reward");
    }

    /** Where we are in the calendar at an instant. */
    public enum Phase { BEFORE, OPEN, AFTER }

    public Phase phase(Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        if (today.isBefore(LocalDate.of(year, month, firstDay))) return Phase.BEFORE;
        if (today.isAfter(LocalDate.of(year, month, lastDay))) return Phase.AFTER;
        return Phase.OPEN;
    }

    /** Today's gift number (the day of the month), or empty when the calendar is closed. */
    public Optional<Integer> dayAt(Instant now) {
        if (phase(now) != Phase.OPEN) return Optional.empty();
        return Optional.of(now.atZone(zone).getDayOfMonth());
    }

    public boolean isLastDay(int day) {
        return day == lastDay;
    }

    public int coinsFor(int day) {
        if (day < firstDay || day > lastDay) throw new IllegalArgumentException("No gift on day " + day);
        return isLastDay(day) ? lastDayCoins : coins;
    }

    /** {@code d01}..{@code d24}: the collectible id of a day. */
    public static String collectibleId(int day) {
        return "d%02d".formatted(day);
    }

    /** Ledger key of a day's coins (the reward service prefixes it with {@code theme:}). */
    public String rewardKey(int day) {
        return id + ":" + collectibleId(day);
    }

    /** Reads an optional {@code advent:} section; {@code null} when the theme has none. */
    public static AdventCalendar parse(ConfigurationSection section, ZoneId defaultZone) {
        if (section == null) return null;
        ZoneId zone = section.isString("zone") ? ZoneId.of(section.getString("zone")) : defaultZone;
        return new AdventCalendar(section.getString("id"), zone, section.getInt("year"), section.getInt("month", 12),
                section.getInt("first-day", 1), section.getInt("last-day", 24), section.getInt("coins", 10),
                section.getInt("last-day-coins", section.getInt("coins", 10)));
    }
}
