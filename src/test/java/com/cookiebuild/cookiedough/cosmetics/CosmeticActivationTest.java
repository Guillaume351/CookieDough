package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.lobby.HubGameMenuModel;

class CosmeticActivationTest {
    @Test
    void welcomeGiftEquipsTheFreeTrailOncePerAccountAndRespectsItsRemoval() {
        InMemoryCosmeticRepository repository = new InMemoryCosmeticRepository();
        CosmeticService service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        List<UUID> changes = new java.util.ArrayList<>();
        service.onChange(changes::add);

        assertEquals(CosmeticService.WelcomeGiftResult.EQUIPPED, service.claimWelcomeGift(player, "bedrock"));
        assertEquals(CosmeticCatalog.COOKIE_SPARKLE_TRAIL, service.inventory(player).selections()
                .get(CosmeticSlot.HUB_TRAIL));
        assertEquals("bedrock", repository.welcomeGiftEdition(player));
        assertEquals(List.of(player), changes);
        // The free baseline still needs no entitlement row.
        assertTrue(repository.sources(player, CosmeticCatalog.COOKIE_SPARKLE_TRAIL).isEmpty());

        service.deselect(player, CosmeticSlot.HUB_TRAIL);
        assertEquals(CosmeticService.WelcomeGiftResult.ALREADY_GIFTED, service.claimWelcomeGift(player, "bedrock"));
        assertFalse(service.inventory(player).selections().containsKey(CosmeticSlot.HUB_TRAIL));
    }

    @Test
    void welcomeGiftNeverReplacesAnEquippedTrail() {
        InMemoryCosmeticRepository repository = new InMemoryCosmeticRepository();
        CosmeticService service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        repository.setCoins(player, 1_000);
        service.purchaseWithCoins(player, CosmeticCatalog.CHERRY_PETAL_TRAIL);

        assertEquals(CosmeticService.WelcomeGiftResult.SLOT_TAKEN, service.claimWelcomeGift(player, "toaster"));
        assertEquals(CosmeticCatalog.CHERRY_PETAL_TRAIL, service.inventory(player).selections()
                .get(CosmeticSlot.HUB_TRAIL));
        assertEquals("unknown", repository.welcomeGiftEdition(player));
        assertEquals(CosmeticService.WelcomeGiftResult.ALREADY_GIFTED, service.claimWelcomeGift(player, "java"));
    }

    @Test
    void theOfferIsTheMostExpensiveCoinCosmeticThePlayerCanPayForAndDoesNotOwn() {
        InMemoryCosmeticRepository repository = new InMemoryCosmeticRepository();
        CosmeticService service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        assertTrue(CosmeticActivation.bestAffordable(service.inventory(player), 149).isEmpty());
        assertEquals(CosmeticCatalog.CHOCOLATE_CHIP_TRAIL,
                CosmeticActivation.bestAffordable(service.inventory(player), 150).orElseThrow().id());
        assertEquals(CosmeticCatalog.COOKIE_RAIN_VICTORY,
                CosmeticActivation.bestAffordable(service.inventory(player), 599).orElseThrow().id());

        repository.grant(player, CosmeticCatalog.COOKIE_RAIN_VICTORY, "coins:test", new Date(), null);
        assertEquals(CosmeticCatalog.CHERRY_PETAL_TRAIL,
                CosmeticActivation.bestAffordable(service.inventory(player), 599).orElseThrow().id());
        // Rewards and premium items are never offered for coins.
        assertEquals(CosmeticCatalog.RAINBOW_TRAIL,
                CosmeticActivation.bestAffordable(service.inventory(player), 1_000_000).orElseThrow().id());
    }

    @Test
    void theOfferKeepsPlayAgainFirstAndSitsBeforeTheAutoReplayToggle() {
        HubGameMenuModel.Entry same = entry("replay:same");
        HubGameMenuModel.Entry quick = entry("replay:quick");
        HubGameMenuModel.Entry auto = entry("replay:auto");
        HubGameMenuModel.Entry lobby = entry("replay:lobby");
        HubGameMenuModel.Entry offer = entry("shop");
        assertEquals(List.of(same, quick, offer, auto, lobby),
                CosmeticActivation.insertOffer(List.of(same, quick, auto, lobby), offer));
        assertEquals(List.of(same, offer, lobby), CosmeticActivation.insertOffer(List.of(same, lobby), offer));
        assertEquals(List.of(offer), CosmeticActivation.insertOffer(List.of(), offer));
    }

    @Test
    void tenMatchesRewardKeyMatchesTheGoalTrackerAchievement() {
        assertEquals("achievement:" + com.cookiebuild.cookiedough.retention.GoalRules.TEN_MATCHES.key(),
                CosmeticActivation.TEN_MATCHES_REWARD_KEY);
    }

    private static HubGameMenuModel.Entry entry(String action) {
        return new HubGameMenuModel.Entry(action, Material.COOKIE, null, action, action);
    }
}
