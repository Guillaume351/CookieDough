package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SkyblockLobbyGuideContractTest {
    @Test
    void wiresEveryLiveEligibilitySignalAndPlayerOnlyParticles() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/SkyblockLobbyGuide.java"));

        assertTrue(source.contains("lobbyWorld.getPlayers().size()"));
        assertTrue(source.contains("GameManager.getGameOfPlayer(cookiePlayer)"));
        assertTrue(source.contains("ActivityRegistry.owner(player.getUniqueId())"));
        assertTrue(source.contains("PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())"));
        assertTrue(source.contains("ModePopulationService.isPersistentActivityAvailable(SKYBLOCK)"));
        assertTrue(source.contains("ModePopulationService.hasReadyMatchForOneMorePlayer()"));
        assertTrue(source.contains("hasOnlinePartyCompanions(player.getUniqueId())"));
        assertTrue(source.contains("getPracticeManager().isActive(player)"));
        assertTrue(source.contains("player.spawnParticle("));
        assertFalse(source.contains("player.getWorld().spawnParticle("));
    }

    @Test
    void startsAfterNpcSetupAndStopsBeforeNpcShutdown() throws Exception {
        String plugin = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/CookieDough.java"));
        String lobby = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java"));

        assertTrue(plugin.indexOf("lobbyManager.setupSkyblockBillboard()")
                < plugin.indexOf("lobbyManager.startSkyblockGuide()"));
        assertTrue(lobby.indexOf("skyblockGuide.shutdown()")
                < lobby.indexOf("gameNpcs.forEach(GameNPC::shutdown)"));
    }
}
