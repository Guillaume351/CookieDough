package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class LoginCalendarPolicyTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-09-30");

    @Test
    void firstClaimStartsTheCycleAndTodayIsClaimedOnce() {
        LoginCalendarPolicy.Claim claim = LoginCalendarPolicy.evaluate(null, null, TODAY).orElseThrow();
        assertEquals(1, claim.streak());
        assertEquals(1, claim.cycleDay());
        assertEquals(20, claim.coins());
        assertFalse(claim.welcomeBack());
        LoginCalendarPolicy.State state = claim.next(null);
        assertTrue(LoginCalendarPolicy.evaluate(state, null, TODAY).isEmpty());
    }

    @Test
    void consecutiveDaysGrowTheStreakAndDaySevenIsSpecial() {
        LoginCalendarPolicy.State state = new LoginCalendarPolicy.State(TODAY.minusDays(1), 6, 6, 6);
        LoginCalendarPolicy.Claim claim = LoginCalendarPolicy.evaluate(state, null, TODAY).orElseThrow();
        assertEquals(7, claim.streak());
        assertTrue(claim.daySeven());
        assertEquals(100, claim.coins());
        // Day 8 starts a new cycle at day 1 while the streak keeps counting.
        LoginCalendarPolicy.Claim eighth = LoginCalendarPolicy.evaluate(claim.next(state), null, TODAY.plusDays(1))
                .orElseThrow();
        assertEquals(8, eighth.streak());
        assertEquals(1, eighth.cycleDay());
    }

    @Test
    void missingADayResetsTheStreak() {
        LoginCalendarPolicy.State state = new LoginCalendarPolicy.State(TODAY.minusDays(2), 5, 5, 5);
        LoginCalendarPolicy.Claim claim = LoginCalendarPolicy.evaluate(state, null, TODAY).orElseThrow();
        assertEquals(1, claim.streak());
        assertTrue(claim.streakReset());
        assertEquals(5, claim.next(state).bestStreak());
    }

    @Test
    void welcomeBackAfterFourteenDaysOnlyOncePerReturn() {
        LoginCalendarPolicy.State absent = new LoginCalendarPolicy.State(TODAY.minusDays(14), 3, 3, 3);
        LoginCalendarPolicy.Claim claim = LoginCalendarPolicy.evaluate(absent, null, TODAY).orElseThrow();
        assertTrue(claim.welcomeBack());
        assertEquals(100, claim.welcomeBackCoins());
        assertEquals(14, claim.absenceDays());
        assertTrue(LoginCalendarPolicy.evaluate(claim.next(absent), null, TODAY).isEmpty());
        assertFalse(LoginCalendarPolicy.evaluate(new LoginCalendarPolicy.State(TODAY.minusDays(13), 1, 1, 1),
                null, TODAY).orElseThrow().welcomeBack());
    }

    @Test
    void legacyPlayersWithoutCalendarUseTheirLastSession() {
        assertTrue(LoginCalendarPolicy.evaluate(null, TODAY.minusDays(30), TODAY).orElseThrow().welcomeBack());
        assertFalse(LoginCalendarPolicy.evaluate(null, TODAY.minusDays(2), TODAY).orElseThrow().welcomeBack());
    }

    @Test
    void upcomingDayForTheCalendarView() {
        assertEquals(1, LoginCalendarPolicy.upcomingCycleDay(null, TODAY));
        assertEquals(4, LoginCalendarPolicy.upcomingCycleDay(
                new LoginCalendarPolicy.State(TODAY.minusDays(1), 3, 3, 3), TODAY));
        assertEquals(1, LoginCalendarPolicy.upcomingCycleDay(
                new LoginCalendarPolicy.State(TODAY.minusDays(3), 3, 3, 3), TODAY));
        assertEquals(1, LoginCalendarPolicy.upcomingCycleDay(
                new LoginCalendarPolicy.State(TODAY, 7, 7, 7), TODAY));
    }

    @Test
    void tomorrowRewardFollowsTheStreakFromTodaysClaim() {
        assertEquals(new LoginCalendarPolicy.TomorrowReward(2, 25), LoginCalendarPolicy.tomorrow(null, TODAY));
        LoginCalendarPolicy.State claimedToday = new LoginCalendarPolicy.State(TODAY, 3, 3, 3);
        assertEquals(new LoginCalendarPolicy.TomorrowReward(4, 40), LoginCalendarPolicy.tomorrow(claimedToday, TODAY));
        LoginCalendarPolicy.State claimedYesterday = new LoginCalendarPolicy.State(TODAY.minusDays(1), 5, 5, 5);
        assertEquals(new LoginCalendarPolicy.TomorrowReward(7, 100),
                LoginCalendarPolicy.tomorrow(claimedYesterday, TODAY));
        LoginCalendarPolicy.State broken = new LoginCalendarPolicy.State(TODAY.minusDays(3), 5, 5, 5);
        assertEquals(new LoginCalendarPolicy.TomorrowReward(2, 25), LoginCalendarPolicy.tomorrow(broken, TODAY));
        LoginCalendarPolicy.State daySeven = new LoginCalendarPolicy.State(TODAY, 7, 7, 7);
        assertEquals(new LoginCalendarPolicy.TomorrowReward(1, 20), LoginCalendarPolicy.tomorrow(daySeven, TODAY));

        LoginCalendarPolicy.Claim claim = LoginCalendarPolicy.evaluate(claimedYesterday, null, TODAY).orElseThrow();
        assertEquals(LoginCalendarPolicy.tomorrow(claim.next(claimedYesterday), TODAY),
                LoginCalendarPolicy.tomorrowAfter(claim));
        assertEquals("Reviens demain : +40 pièces (jour 4/7)", RetentionRewardService.tomorrowText(
                java.util.Locale.FRENCH, new LoginCalendarPolicy.TomorrowReward(4, 40)));
    }
}
