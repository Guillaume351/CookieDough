package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VictoryEffectDispatcherTest {
    @Test
    void committedWinnerIdsAreDispatchedOnceAndLosersAreNot() {
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        List<UUID> dispatched = new ArrayList<>();
        VictoryEffectDispatcher dispatcher = dispatched::add;

        dispatcher.dispatchAll(List.of(winner, winner));

        assertEquals(List.of(winner), dispatched);
        assertEquals(false, dispatched.contains(loser));
    }
}
