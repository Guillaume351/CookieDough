package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertTrue(source.contains("for (PotionEffect effect : potionEffects)"));
        assertTrue(source.contains("Component displayName"));
        assertTrue(source.contains("player.displayName(displayName)"));
        assertTrue(source.contains("player.setAbsorptionAmount"));
        assertTrue(source.contains("player.setAllowFlight(allowFlight)"));
        assertTrue(source.contains("capturedAtEpochMillis"));
        assertEquals(80, PlayerActivitySnapshot.remainingPotionTicks(100, false, 1_000L, 2_000L));
        assertEquals(0, PlayerActivitySnapshot.remainingPotionTicks(20, false, 1_000L, 2_001L));
        assertEquals(-1, PlayerActivitySnapshot.remainingPotionTicks(100, true, 1_000L, 20_000L));
        assertEquals(0, PlayerActivitySnapshot.remainingTimedTicks(40, 1_000L, 3_001L));
        assertEquals(0.0, PlayerActivitySnapshot.remainingAbsorption(8.0, true, false));
        assertEquals(8.0, PlayerActivitySnapshot.remainingAbsorption(8.0, true, true));
        assertTrue(source.contains("capturedAbsorptionEffect && !restoredAbsorptionEffect"));
    }
}
