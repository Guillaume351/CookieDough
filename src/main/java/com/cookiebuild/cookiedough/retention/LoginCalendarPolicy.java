package com.cookiebuild.cookiedough.retention;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Pure rules of the in-game 7-day login calendar and of the returning-player
 * welcome. Days are Europe/Paris calendar days.
 */
public final class LoginCalendarPolicy {
    /** Coins for cycle days 1..7. Day 7 also unlocks the streak cosmetic once. */
    public static final List<Integer> DAY_COINS = List.of(20, 25, 30, 40, 50, 60, 100);
    /** Extra coins on day 7 when the streak cosmetic is already owned. */
    public static final int DAY_SEVEN_OWNED_BONUS = 50;
    public static final int CYCLE_LENGTH = 7;
    public static final int WELCOME_BACK_ABSENCE_DAYS = 14;
    public static final int WELCOME_BACK_COINS = 100;

    /** Persisted state; {@code lastClaimDay} is the last Paris day a reward was claimed. */
    public record State(LocalDate lastClaimDay, int streak, int bestStreak, int totalClaims) {
    }

    public record Claim(LocalDate day, int streak, int cycleDay, int coins, boolean streakReset,
            boolean welcomeBack, long absenceDays) {
        public boolean daySeven() {
            return cycleDay == CYCLE_LENGTH;
        }

        public int welcomeBackCoins() {
            return welcomeBack ? WELCOME_BACK_COINS : 0;
        }

        public State next(State previous) {
            int best = Math.max(previous == null ? 0 : previous.bestStreak(), streak);
            int total = (previous == null ? 0 : previous.totalClaims()) + 1;
            return new State(day, streak, best, total);
        }
    }

    private LoginCalendarPolicy() {
    }

    /**
     * @param previous persisted calendar state, or null for a player who never claimed
     * @param lastVisitFallback last Paris day with a previous session, used only when
     *        {@code previous} is null (players from before the calendar existed)
     * @return the claim for today, or empty when today is already claimed
     */
    public static Optional<Claim> evaluate(State previous, LocalDate lastVisitFallback, LocalDate today) {
        if (previous != null && previous.lastClaimDay() != null && !previous.lastClaimDay().isBefore(today)) {
            return Optional.empty();
        }
        boolean continues = previous != null && previous.lastClaimDay() != null
                && previous.lastClaimDay().plusDays(1).equals(today);
        int streak = continues ? previous.streak() + 1 : 1;
        boolean reset = previous != null && !continues && previous.streak() > 1;
        int cycleDay = cycleDay(streak);
        LocalDate lastVisit = previous != null && previous.lastClaimDay() != null
                ? previous.lastClaimDay() : lastVisitFallback;
        long absence = lastVisit == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(lastVisit, today));
        boolean welcomeBack = lastVisit != null && absence >= WELCOME_BACK_ABSENCE_DAYS;
        return Optional.of(new Claim(today, streak, cycleDay, DAY_COINS.get(cycleDay - 1), reset, welcomeBack,
                absence));
    }

    public static int cycleDay(int streak) {
        return ((Math.max(1, streak) - 1) % CYCLE_LENGTH) + 1;
    }

    public static int coinsForCycleDay(int cycleDay) {
        return DAY_COINS.get(Math.max(1, Math.min(CYCLE_LENGTH, cycleDay)) - 1);
    }

    /**
     * Cycle day the player would reach by claiming on {@code today}: used by
     * /calendrier to show the upcoming reward without mutating anything.
     */
    public static int upcomingCycleDay(State state, LocalDate today) {
        if (state == null || state.lastClaimDay() == null) return 1;
        if (!state.lastClaimDay().isBefore(today)) return cycleDay(state.streak() + 1);
        return state.lastClaimDay().plusDays(1).equals(today) ? cycleDay(state.streak() + 1) : 1;
    }
}
