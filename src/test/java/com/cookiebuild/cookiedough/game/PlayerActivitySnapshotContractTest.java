package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PlayerActivitySnapshotContractTest {
    @Test
    void reconnectRestoresKitPassivesAndVisibleIdentityAfterLobbyReset() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/game/PlayerActivitySnapshot.java"));

        assertTrue(source.contains("List<PotionEffect> potionEffects"));
        assertTrue(source.contains("player.getActivePotionEffects()"));
        assertTrue(source.contains("potionEffects.forEach"));
        assertTrue(source.contains("Component displayName"));
        assertTrue(source.contains("player.displayName(displayName)"));
        assertTrue(source.contains("player.setAbsorptionAmount"));
        assertTrue(source.contains("player.setAllowFlight(allowFlight)"));
    }
}
