package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.PlayerState;

class SkyblockGuidancePolicyTest {
    @Test
    void guidesOnlyAnEligibleSoloLobbyPlayer() {
        assertTrue(SkyblockGuidancePolicy.shouldGuide(context(
                true, PlayerState.LOBBY, 1, false, false, true, true,
                false, false, false, 100.0)));
    }

    @Test
    void rejectsEveryUnsafeOrIrrelevantState() {
        List<SkyblockGuidancePolicy.Context> rejected = List.of(
                context(false, PlayerState.LOBBY, 1, false, false, true, true,
                        false, false, false, 100.0),
                context(true, PlayerState.QUEUED, 1, false, false, true, true,
                        false, false, false, 100.0),
                context(true, PlayerState.LOBBY, 2, false, false, true, true,
                        false, false, false, 100.0),
                context(true, PlayerState.LOBBY, 1, true, false, true, true,
                        false, false, false, 100.0),
                context(true, PlayerState.LOBBY, 1, false, true, true, true,
                        false, false, false, 100.0),
                context(true, PlayerState.LOBBY, 1, false, false, false, true,
                        false, false, false, 100.0),
                context(true, PlayerState.LOBBY, 1, false, false, true, false,
                        false, false, false, 100.0),
                context(true, PlayerState.LOBBY, 1, false, false, true, true,
                        true, false, false, 100.0),
                context(true, PlayerState.LOBBY, 1, false, false, true, true,
                        false, true, false, 100.0),
                context(true, PlayerState.LOBBY, 1, false, false, true, true,
                        false, false, true, 100.0),
                context(true, PlayerState.LOBBY, 1, false, false, true, true,
                        false, false, false, SkyblockGuidancePolicy.MIN_DISTANCE
                                * SkyblockGuidancePolicy.MIN_DISTANCE),
                context(true, PlayerState.LOBBY, 1, false, false, true, true,
                        false, false, false, Double.NaN));

        rejected.forEach(value -> assertFalse(SkyblockGuidancePolicy.shouldGuide(value)));
        assertFalse(SkyblockGuidancePolicy.shouldGuide(null));
    }

    private static SkyblockGuidancePolicy.Context context(
            boolean inLobbyWorld,
            PlayerState playerState,
            int lobbyPlayerCount,
            boolean gameOwned,
            boolean activityOwned,
            boolean profileReady,
            boolean skyblockAvailable,
            boolean readyMatch,
            boolean onlinePartyCompanion,
            boolean practicing,
            double distanceSquared) {
        return new SkyblockGuidancePolicy.Context(
                inLobbyWorld, playerState, lobbyPlayerCount, gameOwned, activityOwned,
                profileReady, skyblockAvailable, readyMatch, onlinePartyCompanion,
                practicing, distanceSquared);
    }
}
