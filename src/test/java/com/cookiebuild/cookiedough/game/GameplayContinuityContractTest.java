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
        assertTrue(game.contains("if (!getOwnedPlayers().isEmpty() && !ejectOwnedPlayersToLobby()) return;"));
        assertFalse(game.contains("spectators.clear();"));
        assertTrue(game.contains("return getSpectators().isEmpty();"));
        assertTrue(game.contains("return getOwnedPlayers().isEmpty();"));
        String manager = source("game/GameManager.java");
        assertTrue(manager.contains("if (game != null && !game.ejectOwnedPlayersToLobby())"));
        assertTrue(manager.indexOf("if (game != null && !game.ejectOwnedPlayersToLobby())")
                < manager.indexOf("games.remove(game);"));
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
        assertTrue(manager.contains("QueueIntentReadinessPolicy.shouldActivate(game.getPlayerCount(), readyCount"));
        assertTrue(manager.contains("registerPartyQueueIntent"));
        assertTrue(manager.contains("lobby.admitQueuedParty(members, game)"));
        assertFalse(manager.contains("if (replacement == null) {\n"
                + "            queueIntents.entrySet().removeIf"));
        assertTrue(manager.contains("adoptWaitingQueueIntents(game)"));
        assertTrue(manager.contains("registerPostMatchQueueIntent"));
        assertTrue(lobby.contains("\"lobby.queue.intent_replaced\""));
        assertTrue(source("lobby/PlayerHubMenu.java").contains("requestSelectedActivity(player, gameName)"));
        assertTrue(game.indexOf("GameManager.activateReadyQueueIntents(this)")
                < game.indexOf("if (canStartCountdown())"));
        assertTrue(lobby.contains("ActivityRegistry.canLeave(cookiePlayer, \"returned_lobby\")"));
        assertTrue(lobby.contains("ActivityRegistry.leave(cookiePlayer, \"returned_lobby\")"));
        String spectate = lobby.substring(lobby.indexOf("public void requestSpectate"),
                lobby.indexOf("public void requestActivity"));
        assertTrue(spectate.indexOf("game.preflightSpectatorAdmission(cookiePlayer)")
                < spectate.indexOf("transitionFromPassiveActivity(cookiePlayer)"));
        assertTrue(spectate.contains("restorePassiveSource(cookiePlayer, source)"));
        String activity = lobby.substring(lobby.indexOf("public void requestActivity"),
                lobby.indexOf("private boolean transitionFromPassiveActivity"));
        assertTrue(activity.indexOf("PassiveSource source = passiveSource(cookiePlayer)")
                < activity.indexOf("transitionFromPassiveActivity(cookiePlayer)"));
        assertTrue(activity.contains("restorePassiveSource(cookiePlayer, source);"));
        assertTrue(activity.contains("else {\n            restorePassiveSource(cookiePlayer, source);"));
        String lobbyTeleport = lobby.substring(lobby.indexOf("public static boolean teleportPlayerToLobby"),
                lobby.indexOf("public void joinAvailableGame"));
        assertTrue(lobbyTeleport.indexOf("lobbySpawnLocation.getChunk().load()")
                < lobbyTeleport.indexOf("ActivityRegistry.leave(cookiePlayer, \"returned_lobby\")"));
        assertTrue(lobbyTeleport.indexOf("ActivityRegistry.canLeave(cookiePlayer, \"returned_lobby\")")
                < lobbyTeleport.indexOf(".teleport(player, lobbySpawnLocation)"));
        assertTrue(lobbyTeleport.contains("ActivityRegistry.notifyLeaveBlocked(cookiePlayer, \"returned_lobby\")"));
        assertTrue(lobbyTeleport.indexOf(".teleport(player, lobbySpawnLocation)")
                < lobbyTeleport.indexOf("ActivityRegistry.leave(cookiePlayer, \"returned_lobby\")"));
        assertTrue(lobbyTeleport.contains(".teleport(player, sourceLocation)"));
        assertTrue(lobby.contains("return teleportPlayerToLobby(cookiePlayer);"));
        String quickPlay = lobby.substring(lobby.indexOf("public void joinAvailableGame"),
                lobby.indexOf("public void requestQuickPlay"));
        assertTrue(quickPlay.indexOf("game.addPlayerToAvailableTeam(player)")
                < quickPlay.indexOf("GameManager.cancelQueueIntent(player.getPlayer().getUniqueId())"));
        assertTrue(manager.contains("getAdmittableQueueIntentCount"));
        assertTrue(manager.contains("ActivityRegistry.notifyQueueIntentRegistered(player)"));
        assertTrue(manager.contains("ActivityRegistry.notifyQueueIntentRegistered(member)"));
        String hubCommand = source("commands/LobbyCommand.java");
        assertTrue(hubCommand.contains("boolean activityReady = ActivityRegistry.canLeave"));
        assertTrue(hubCommand.contains("if (LobbyManager.teleportPlayerToLobby(cookiePlayer))"));
        assertTrue(hubCommand.contains("? \"lobby.teleport_failed\" : \"lobby.queue.leave_failed\""));
        assertTrue(manager.contains("lobby.canAdmitQueuedIntent(current, game)"));
        assertTrue(manager.contains("QueueAdmissionPlan plan = queueAdmissionPlan(game, lobby)"));
        assertTrue(manager.contains("cohort.size() > remaining || game.getPartyAdmissionProblem(cohort.size())"));
        assertTrue(manager.contains("remaining -= cohort.size()"));
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
