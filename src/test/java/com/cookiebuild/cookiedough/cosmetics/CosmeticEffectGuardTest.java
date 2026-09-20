package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.PlayerState;

class CosmeticEffectGuardTest {
    @Test
    void hubEffectsRequireSelectionLobbyWorldAndCooldown() {
        long now = 20_000L;
        assertFalse(CosmeticEffectGuard.canUseHubEffect(null, CosmeticCatalog.COOKIE_CHEER,
                PlayerState.LOBBY, true, now, 0, 10_000));
        assertFalse(CosmeticEffectGuard.canUseHubEffect(CosmeticCatalog.COOKIE_CHEER,
                CosmeticCatalog.COOKIE_CHEER, PlayerState.IN_GAME, true, now, 0, 10_000));
        assertFalse(CosmeticEffectGuard.canUseHubEffect(CosmeticCatalog.COOKIE_CHEER,
                CosmeticCatalog.COOKIE_CHEER, PlayerState.LOBBY, false, now, 0, 10_000));
        assertFalse(CosmeticEffectGuard.canUseHubEffect(CosmeticCatalog.COOKIE_CHEER,
                CosmeticCatalog.COOKIE_CHEER, PlayerState.LOBBY, true, now, 15_000, 10_000));
        assertTrue(CosmeticEffectGuard.canUseHubEffect(CosmeticCatalog.COOKIE_CHEER,
                CosmeticCatalog.COOKIE_CHEER, PlayerState.LOBBY, true, now, 0, 10_000));
    }

    @Test
    void effectAuthorizationRequiresAnActiveSelectedEntitlement() {
        InMemoryCosmeticRepository repository = new InMemoryCosmeticRepository();
        CosmeticService service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        assertFalse(CosmeticEffectAuthorization.isSelected(service.inventory(player),
                CosmeticSlot.VICTORY_EFFECT, CosmeticCatalog.GOLDEN_COOKIE_BURST));

        service.grantPermanent(player, CosmeticCatalog.GOLDEN_COOKIE_BURST, "test");
        service.select(player, CosmeticSlot.VICTORY_EFFECT, CosmeticCatalog.GOLDEN_COOKIE_BURST);
        assertTrue(CosmeticEffectAuthorization.isSelected(service.inventory(player),
                CosmeticSlot.VICTORY_EFFECT, CosmeticCatalog.GOLDEN_COOKIE_BURST));

        service.revoke(player, CosmeticCatalog.GOLDEN_COOKIE_BURST);
        assertFalse(CosmeticEffectAuthorization.isSelected(service.inventory(player),
                CosmeticSlot.VICTORY_EFFECT, CosmeticCatalog.GOLDEN_COOKIE_BURST));
    }

    @Test
    void victoryEffectCannotRunForOfflineOrWithinCooldown() {
        assertFalse(CosmeticEffectGuard.canUseVictoryEffect(CosmeticCatalog.GOLDEN_COOKIE_BURST,
                PlayerState.OFFLINE, 20_000, 0, 3_000));
        assertFalse(CosmeticEffectGuard.canUseVictoryEffect(CosmeticCatalog.GOLDEN_COOKIE_BURST,
                PlayerState.IN_GAME, 20_000, 19_000, 3_000));
        assertTrue(CosmeticEffectGuard.canUseVictoryEffect(CosmeticCatalog.GOLDEN_COOKIE_BURST,
                PlayerState.IN_GAME, 20_000, 0, 3_000));
    }
}
