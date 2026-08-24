package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PlayerTransportCoverageContractTest {
    @Test
    void joinLobbyAndSpectatorUseTheBoundedGuard() throws Exception {
        String listener = source("src/main/java/com/cookiebuild/cookiedough/listener/PlayerWrapperListener.java");
        String lobby = source("src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java");
        String game = source("src/main/java/com/cookiebuild/cookiedough/game/Game.java");
        assertTrue(listener.contains("PersistentResumeRoute.resolve"));
        assertTrue(listener.contains("getPlayerTransitionFlightGuard().protectLoading"));
        assertTrue(listener.contains("onTransitionFlightToggle"));
        assertTrue(lobby.contains("getPlayerTransitionFlightGuard()"));
        assertTrue(game.contains("teleportPlayerSafely"));
    }

    @Test
    void everyInstalledMinigameAdmissionUsesTheBoundedGuard() throws Exception {
        assumeTrue(Files.isDirectory(Path.of("../BedWars")),
                "Cross-module contract runs from the parent Cookies checkout");

        for (String moduleSource : List.of(
                "../BedWars/src/main/java/com/cookiebuild/bedwars/game/BedWarsGame.java",
                "../BuildBattles/src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java",
                "../MicroBattles/src/main/java/com/cookiebuild/microbattles/game/MicroBattlesGame.java",
                "../Pitchout/src/main/java/com/cookiebuild/pitchout/game/PitchoutGame.java",
                "../SkyWars/src/main/java/com/cookiebuild/skywars/game/SkyWarsGame.java",
                "../TurfWars/src/main/java/com/cookiebuild/turfwars/game/TurfWarsGame.java")) {
            assertTrue(source(moduleSource).contains("teleportPlayerSafely"), moduleSource);
        }
        String micro = source("../MicroBattles/src/main/java/com/cookiebuild/microbattles/game/MicroBattlesGame.java");
        int microAdmission = micro.indexOf("protected void teleportToGame(CookiePlayer player)");
        int microPostAdmission = micro.indexOf("if (this.getState() != GameState.RUNNING)", microAdmission);
        assertTrue(microAdmission >= 0 && microPostAdmission > microAdmission);
        assertTrue(micro.substring(microAdmission, microPostAdmission).contains("teleportPlayerSafely"));
        String bedWars = source("../BedWars/src/main/java/com/cookiebuild/bedwars/game/BedWarsGame.java");
        int bedWarsReconnect = bedWars.indexOf("private void restoreReconnectState");
        int bedWarsCapture = bedWars.indexOf("private static ReconnectState captureReconnectState", bedWarsReconnect);
        assertTrue(bedWarsReconnect >= 0 && bedWarsCapture > bedWarsReconnect);
        assertTrue(bedWars.substring(bedWarsReconnect, bedWarsCapture).contains("protectLanding"));
        assertTrue(bedWars.contains("cloneItem(player.getInventory().getItemInOffHand())"));
        int bedWarsReconnectEntry = bedWars.indexOf("public boolean reconnect(CookiePlayer cookiePlayer)");
        String bedWarsTransaction = bedWars.substring(bedWarsReconnectEntry, bedWarsReconnect);
        assertTrue(bedWarsTransaction.indexOf("tryTeleportPlayerSafely")
                < bedWarsTransaction.indexOf("reconnectTracker.consume"));
        assertTrue(bedWarsTransaction.indexOf("reconnectTracker.consume")
                < bedWarsTransaction.indexOf("restorePlayerAfterReconnect"));

        String turf = source("../TurfWars/src/main/java/com/cookiebuild/turfwars/game/TurfWarsGame.java");
        int turfReconnect = turf.indexOf("public boolean reconnect(CookiePlayer cookiePlayer)");
        int turfReservation = turf.indexOf("public boolean hasReconnectReservation", turfReconnect);
        String turfTransaction = turf.substring(turfReconnect, turfReservation);
        assertTrue(turfTransaction.indexOf("tryTeleportPlayerSafely")
                < turfTransaction.indexOf("completeReconnect"));
        assertTrue(turfTransaction.indexOf("completeReconnect")
                < turfTransaction.indexOf("restorePlayerAfterReconnect"));

        String builds = source("../BuildBattles/src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java");
        int buildReconnect = builds.indexOf("public boolean reconnect(CookiePlayer cookiePlayer)");
        int buildReservation = builds.indexOf("public boolean hasReconnectReservation", buildReconnect);
        String buildTransaction = builds.substring(buildReconnect, buildReservation);
        assertTrue(buildTransaction.indexOf("tryTeleportPlayerSafely")
                < buildTransaction.indexOf("restorePlayerAfterReconnect"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
