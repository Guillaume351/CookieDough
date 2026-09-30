package com.cookiebuild.cookiedough.retention;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;

import com.cookiebuild.cookiedough.cosmetics.CosmeticCatalog;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Plain-text 7-day calendar (no clickable components, readable on Bedrock). */
public final class LoginCalendarView {
    private LoginCalendarView() {
    }

    public static List<String> lines(Locale locale, LoginCalendarPolicy.State state, LocalDate today) {
        boolean claimedToday = state != null && state.lastClaimDay() != null
                && !state.lastClaimDay().isBefore(today);
        boolean alive = state != null && state.lastClaimDay() != null
                && !state.lastClaimDay().isBefore(today.minusDays(1));
        int streak = alive ? state.streak() : 0;
        int doneInCycle = streak == 0 ? 0 : LoginCalendarPolicy.cycleDay(streak);
        if (!claimedToday && doneInCycle == LoginCalendarPolicy.CYCLE_LENGTH) doneInCycle = 0;
        int upcoming = LoginCalendarPolicy.upcomingCycleDay(state, today);
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + message(locale, "calendar.title"));
        StringBuilder row = new StringBuilder();
        for (int day = 1; day <= LoginCalendarPolicy.CYCLE_LENGTH; day++) {
            if (day > 1) row.append(ChatColor.DARK_GRAY).append(" | ");
            boolean done = day <= doneInCycle;
            boolean next = day == upcoming && day > doneInCycle;
            row.append(done ? ChatColor.GREEN + "✔" : next ? ChatColor.YELLOW + "▶" : ChatColor.GRAY + "·")
                    .append(message(locale, "calendar.day_short", day)).append(" ")
                    .append(ChatColor.GOLD).append(LoginCalendarPolicy.coinsForCycleDay(day));
        }
        lines.add(row.toString());
        lines.add(ChatColor.LIGHT_PURPLE + message(locale, "calendar.day7_reward",
                message(locale, "cosmetics.item." + CosmeticCatalog.STREAK_STAR_TRAIL + ".name")));
        lines.add(ChatColor.YELLOW + message(locale, "calendar.streak", streak,
                state == null ? 0 : state.bestStreak()));
        lines.add(ChatColor.GRAY + message(locale, claimedToday ? "calendar.come_back" : "calendar.claim_soon"));
        return List.copyOf(lines);
    }

    private static String message(Locale locale, String key, Object... args) {
        return LocaleManager.getMessage(key, locale, args);
    }
}
