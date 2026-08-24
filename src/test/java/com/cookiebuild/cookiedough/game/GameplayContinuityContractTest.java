package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GameplayContinuityContractTest {
    @Test
    void externalSpectatorsNeverConsumeParticipantSlotsAndKeepTheSelectorSlot() throws Exception {
        String game = source("game/Game.java");
        String hub = source("lobby/PlayerHubMenu.java");

        assertTrue(game.contains("private final Map<UUID, CookiePlayer> spectators"));
        assertTrue(game.contains("spectators.put(playerId, player)"));
        assertFalse(game.contains("players.add(player);\n        spectators.put"));
        assertTrue(game.indexOf("if (!teleportToSpectator(player)) return false;")
                < game.indexOf("spectators.put(playerId, player)"));
        assertTrue(game.contains("preflightSpectatorAdmission"));
        assertTrue(game.indexOf("if (!spectators.isEmpty()) ejectSpectatorsToLobby();")
                < game.indexOf("spectators.clear();"));
        assertTrue(hub.contains("setItem(7, item(Material.COMPASS,"));
        assertTrue(hub.contains("message(player, \"spectator.controls.item\"), \"spectator_games\""));
        assertFalse(hub.contains("setItem(8, item(Material.COMPASS,\n"
                + "                message(player, \"spectator.controls.item\"), \"spectator_games\""));
    }

    @Test
    void passiveQueueUsesIntentPreflightAndDoesNotMutateTheRosterEarly() throws Exception {
        String manager = source("game/GameManager.java");
        String lobby = source("lobby/LobbyManager.java");
        String game = source("game/Game.java");

        assertTrue(manager.contains("Map<UUID, QueueIntent> queueIntents"));
        assertTrue(manager.contains("lobby.canAdmitQueuedIntent(current, game)"));
        assertTrue(manager.contains("readyCount < needed"));
        assertTrue(manager.contains("registerPartyQueueIntent"));
        assertTrue(manager.contains("lobby.admitQueuedParty(members, game)"));
        assertFalse(manager.contains("if (replacement == null) {\n"
                + "            queueIntents.entrySet().removeIf"));
        assertTrue(manager.contains("adoptWaitingQueueIntents(game)"));
        assertTrue(manager.contains("registerPostMatchQueueIntent"));
        assertTrue(lobby.contains("\"lobby.queue.intent_replaced\""));
        assertTrue(source("lobby/PlayerHubMenu.java").contains("lobby.party.direct_solo_only"));
        assertTrue(game.indexOf("GameManager.activateReadyQueueIntents(this)")
                < game.indexOf("if (canStartCountdown())"));
        assertTrue(lobby.contains("ActivityRegistry.canLeave(cookiePlayer, \"returned_lobby\")"));
        assertTrue(lobby.contains("ActivityRegistry.leave(cookiePlayer, \"returned_lobby\")"));
        String spectate = lobby.substring(lobby.indexOf("public void requestSpectate"),
                lobby.indexOf("public void requestActivity"));
        assertTrue(spectate.indexOf("game.preflightSpectatorAdmission(cookiePlayer)")
                < spectate.indexOf("transitionFromPassiveActivity(cookiePlayer)"));
        assertTrue(spectate.contains("restorePassiveSource(cookiePlayer, source)"));
    }

    @Test
    void reconnectIsCoordinatedAfterFreshProfileRecoveryOnTheMainThread() throws Exception {
        String manager = source("game/GameManager.java");
        String listener = source("listener/PlayerWrapperListener.java");

        assertTrue(manager.contains("!Bukkit.isPrimaryThread()"));
        assertTrue(manager.contains("candidates.size() != 1"));
        assertTrue(manager.contains("game.hasReconnectReservation(playerId)"));
        assertTrue(listener.contains("GameManager.tryReconnect(activeCookiePlayer)"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/cookiebuild/cookiedough").resolve(relative));
    }
}
