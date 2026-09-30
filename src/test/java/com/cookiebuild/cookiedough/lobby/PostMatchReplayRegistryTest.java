package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.PlayerState;

class PostMatchReplayRegistryTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void aRecordedCompletionIsPresentedOnceOnTheNextLobbyArrival() {
        PostMatchReplayRegistry registry = new PostMatchReplayRegistry();
        registry.recordCompletion(PLAYER, "SkyWars", 1_000L);
        assertEquals(Optional.of("SkyWars"), registry.consumeArrival(PLAYER, null, 5_000L));
        assertEquals(Optional.empty(), registry.consumeArrival(PLAYER, null, 6_000L));
        assertEquals(Optional.of("SkyWars"), registry.lastCompleted(PLAYER));
    }

    @Test
    void aStaleCompletionIsDroppedButAFinishedArenaStillCounts() {
        PostMatchReplayRegistry registry = new PostMatchReplayRegistry();
        registry.recordCompletion(PLAYER, "SkyWars", 0L);
        assertEquals(Optional.empty(),
                registry.consumeArrival(PLAYER, null, PostMatchReplayRegistry.PENDING_TTL_MILLIS + 1));
        assertEquals(Optional.of("BuildBattles"), registry.consumeArrival(PLAYER, "BuildBattles", 10L));
        assertEquals(Optional.of("BuildBattles"), registry.lastCompleted(PLAYER));
        registry.clear(PLAYER);
        assertEquals(Optional.empty(), registry.lastCompleted(PLAYER));
    }

    @Test
    void offersOnlyToAnIdleReadyLobbyPlayer() {
        assertTrue(PostMatchReplayRegistry.shouldOffer(context(PlayerState.LOBBY, false, false, false)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(context(PlayerState.QUEUED, false, false, false)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(context(PlayerState.LOBBY, true, false, false)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(context(PlayerState.LOBBY, false, true, false)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(context(PlayerState.LOBBY, false, false, true)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(new PostMatchReplayRegistry.OfferContext(
                true, false, true, PlayerState.LOBBY, false, false, false)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(new PostMatchReplayRegistry.OfferContext(
                true, true, false, PlayerState.LOBBY, false, false, false)));
        assertFalse(PostMatchReplayRegistry.shouldOffer(null));
    }

    @Test
    void theChoiceIsScheduledFromTheLobbyArrivalNotDuringTheArenaTransfer() throws Exception {
        String game = Files.readString(Path.of("src/main/java/com/cookiebuild/cookiedough/game/Game.java"));
        String lobby = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/PlayerHubMenu.java"));
        String offerReplay = game.substring(game.indexOf("public void offerReplay()"));
        offerReplay = offerReplay.substring(0, offerReplay.indexOf("\n    }\n"));
        assertTrue(offerReplay.contains("FunnelTelemetry.Event.MATCH_COMPLETED"));
        assertTrue(offerReplay.contains("recordMatchCompleted(player, gameName)"));
        assertFalse(offerReplay.contains("openReplay"));
        assertTrue(lobby.indexOf("FunnelTelemetry.Event.LOBBY_READY") < lobby.indexOf("hub.onLobbyArrival("));
        assertTrue(menu.contains("PostMatchReplayRegistry.OFFER_DELAY_TICKS"));
        assertTrue(PostMatchReplayRegistry.OFFER_DELAY_TICKS >= 20L && PostMatchReplayRegistry.OFFER_DELAY_TICKS <= 40L);
    }

    private static PostMatchReplayRegistry.OfferContext context(PlayerState state, boolean owned, boolean intent,
            boolean activity) {
        return new PostMatchReplayRegistry.OfferContext(true, true, true, state, owned, intent, activity);
    }
}
