package com.cookiebuild.cookiedough.lobby;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.bukkit.entity.Camel;
import org.bukkit.entity.Piglin;
import org.junit.jupiter.api.Test;

class GameNPCBetaSelectorTest {
    @Test
    void fatKingStaysAnAdultPiglinInTheOverworld() {
        Piglin piglin = mock(Piglin.class);

        GameNPC.configureBetaSelector(piglin, "FatKing");

        verify(piglin).setImmuneToZombification(true);
        verify(piglin).setAdult();
    }

    @Test
    void nomadCamelHasAStableStandingAdultHitbox() {
        Camel camel = mock(Camel.class);

        GameNPC.configureBetaSelector(camel, "NomadWars");

        verify(camel).setAdult();
        verify(camel).setAgeLock(true);
        verify(camel).setSitting(false);
    }

    @Test
    void doesNotChangeOtherSelectors() {
        Piglin piglin = mock(Piglin.class);
        Camel camel = mock(Camel.class);

        GameNPC.configureBetaSelector(piglin, "SkyWars");
        GameNPC.configureBetaSelector(camel, "Skyblock");

        verifyNoInteractions(piglin, camel);
    }
}
