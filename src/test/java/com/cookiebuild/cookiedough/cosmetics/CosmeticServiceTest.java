package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CosmeticServiceTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private InMemoryCosmeticRepository repository;
    private CosmeticService service;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCosmeticRepository();
        clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        service = new CosmeticService(repository, clock);
    }

    @Test
    void selectionRequiresAnActiveEntitlement() {
        assertEquals(CosmeticService.SelectionResult.NOT_ENTITLED,
                service.select(PLAYER, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE));
        assertEquals(0, repository.selectionWrites());
        assertTrue(service.inventory(PLAYER).selections().isEmpty());
    }

    @Test
    void permanentGrantEnablesInventorySelectionAndDeselect() {
        assertEquals(CosmeticService.GrantResult.GRANTED,
                service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "order:verified-preview"));
        assertEquals(CosmeticService.SelectionResult.SELECTED,
                service.select(PLAYER, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE));
        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertEquals(CosmeticCatalog.SUPPORTER_BADGE, inventory.selections().get(CosmeticSlot.BADGE));
        assertTrue(inventory.items().stream()
                .filter(item -> item.cosmetic().id().equals(CosmeticCatalog.SUPPORTER_BADGE))
                .allMatch(item -> item.entitled() && item.selected()));

        assertEquals(CosmeticService.SelectionResult.DESELECTED,
                service.deselect(PLAYER, CosmeticSlot.BADGE));
        assertEquals(CosmeticService.SelectionResult.ALREADY_DESELECTED,
                service.deselect(PLAYER, CosmeticSlot.BADGE));
    }

    @Test
    void catalogueAndRequestedSlotMustMatchBeforePersistence() {
        service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "manual-review");
        assertEquals(CosmeticService.SelectionResult.SLOT_MISMATCH,
                service.select(PLAYER, CosmeticSlot.EMOTE, CosmeticCatalog.SUPPORTER_BADGE));
        assertEquals(CosmeticService.SelectionResult.UNKNOWN_COSMETIC,
                service.select(PLAYER, CosmeticSlot.BADGE, "invented-item"));
        assertEquals(0, repository.selectionWrites());
    }

    @Test
    void revokedOrStaleSelectionsAreNeverExposedAsSelected() {
        service.grantPermanent(PLAYER, CosmeticCatalog.COOKIE_CHEER, "manual-review");
        service.select(PLAYER, CosmeticSlot.EMOTE, CosmeticCatalog.COOKIE_CHEER);
        assertTrue(service.revoke(PLAYER, CosmeticCatalog.COOKIE_CHEER));
        assertFalse(service.inventory(PLAYER).selections().containsKey(CosmeticSlot.EMOTE));

        repository.injectSelection(PLAYER, CosmeticSlot.BADGE, CosmeticCatalog.COOKIE_CHEER);
        assertFalse(service.inventory(PLAYER).selections().containsKey(CosmeticSlot.BADGE));
    }

    @Test
    void trustedGrantRejectsUnknownItemsAndUnboundedSources() {
        assertEquals(CosmeticService.GrantResult.UNKNOWN_COSMETIC,
                service.grantPermanent(PLAYER, "not-in-catalogue", "manual"));
        assertThrows(IllegalArgumentException.class,
                () -> service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, " "));
        assertThrows(IllegalArgumentException.class,
                () -> service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "x".repeat(129)));
    }

    @Test
    void temporaryEntitlementIsInactiveAtItsExclusiveExpiry() {
        Instant expiry = clock.instant().plusSeconds(60);
        service.grantUntil(PLAYER, CosmeticCatalog.LOBBY_FLIGHT, "subscription:test", expiry);
        service.select(PLAYER, CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT);
        assertTrue(service.inventory(PLAYER).entitled(CosmeticCatalog.LOBBY_FLIGHT));

        clock.set(expiry);

        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.LOBBY_FLIGHT));
        assertFalse(service.inventory(PLAYER).selections().containsKey(CosmeticSlot.LOBBY_FLIGHT));
        assertEquals(CosmeticService.SelectionResult.NOT_ENTITLED,
                service.select(PLAYER, CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT));
    }

    @Test
    void supporterSubscriptionGrantIsAtomicExactAndSelectsOnlyDefaultSlots() {
        Instant expiry = clock.instant().plusSeconds(2_592_000);
        service.grantSupporterSubscription(PLAYER, "subscription:alpha", expiry);
        CosmeticService.Inventory inventory = service.inventory(PLAYER);

        Set<String> supporterItems = inventory.items().stream()
                .filter(item -> item.entitled() && !item.cosmetic().free())
                .map(item -> item.cosmetic().id())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                CosmeticCatalog.SUPPORTER_BADGE,
                CosmeticCatalog.LOBBY_FLIGHT,
                CosmeticCatalog.SUPPORTER_JOIN_FLAIR,
                CosmeticCatalog.SUPPORTER_PROFILE_FRAME), supporterItems);
        assertEquals(CosmeticCatalog.SUPPORTER_BADGE,
                inventory.selections().get(CosmeticSlot.BADGE));
        assertEquals(CosmeticCatalog.SUPPORTER_PROFILE_FRAME,
                inventory.selections().get(CosmeticSlot.PROFILE_FRAME));
        assertFalse(inventory.selections().containsKey(CosmeticSlot.LOBBY_FLIGHT));
        assertTrue(inventory.items().stream()
                .filter(item -> item.cosmetic().id().equals(CosmeticCatalog.SUPPORTER_JOIN_FLAIR))
                .allMatch(CosmeticService.InventoryItem::selected));
        assertEquals(Set.of(expiry), Set.copyOf(inventory.entitlementExpirations().values()));
    }

    @Test
    void renewalIsIdempotentMonotoneAndNeverConvertsPermanentToTemporary() {
        service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "purchase:badge");
        Instant firstExpiry = clock.instant().plusSeconds(2_592_000);
        service.grantSupporterSubscription(PLAYER, "subscription:alpha", firstExpiry);
        service.grantSupporterSubscription(PLAYER, "subscription:alpha", firstExpiry);
        service.grantSupporterSubscription(PLAYER, "subscription:alpha", firstExpiry.minusSeconds(60));
        assertEquals(firstExpiry,
                repository.expiresAt(PLAYER, CosmeticCatalog.LOBBY_FLIGHT,
                        "subscription:alpha").toInstant());
        assertNull(repository.expiresAt(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "purchase:badge"));
        assertEquals(Set.of("purchase:badge", "subscription:alpha"),
                repository.sources(PLAYER, CosmeticCatalog.SUPPORTER_BADGE));

        Instant extended = firstExpiry.plusSeconds(2_592_000);
        service.grantSupporterSubscription(PLAYER, "subscription:alpha", extended);
        assertEquals(extended,
                repository.expiresAt(PLAYER, CosmeticCatalog.LOBBY_FLIGHT,
                        "subscription:alpha").toInstant());
    }

    @Test
    void permanentGrantUpgradesTemporaryAccessWithoutKeepingAnExpiry() {
        Instant expiry = clock.instant().plusSeconds(2_592_000);
        service.grantUntil(PLAYER, CosmeticCatalog.SUPPORTER_BADGE,
                "subscription:alpha", expiry);

        service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "purchase:badge");

        assertNull(repository.expiresAt(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "purchase:badge"));
        assertEquals(Set.of("subscription:alpha", "purchase:badge"),
                repository.sources(PLAYER, CosmeticCatalog.SUPPORTER_BADGE));
        clock.set(expiry.plusSeconds(1));
        assertTrue(service.inventory(PLAYER).entitled(CosmeticCatalog.SUPPORTER_BADGE));
    }

    @Test
    void subscriptionCancellationPreservesCoexistingPermanentPurchases() {
        service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "purchase:badge");
        Instant expiry = clock.instant().plusSeconds(2_592_000);
        service.grantSupporterSubscription(PLAYER, "subscription:alpha", expiry);

        assertEquals(4, service.revokeSupporterSubscription(PLAYER, "subscription:alpha"));

        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertTrue(inventory.entitled(CosmeticCatalog.SUPPORTER_BADGE));
        assertEquals(CosmeticCatalog.SUPPORTER_BADGE,
                inventory.selections().get(CosmeticSlot.BADGE));
        assertFalse(inventory.entitled(CosmeticCatalog.LOBBY_FLIGHT));
        assertFalse(inventory.entitled(CosmeticCatalog.SUPPORTER_JOIN_FLAIR));
        assertFalse(inventory.entitled(CosmeticCatalog.SUPPORTER_PROFILE_FRAME));
        assertFalse(inventory.selections().containsKey(CosmeticSlot.LOBBY_FLIGHT));
        assertFalse(inventory.selections().containsKey(CosmeticSlot.PROFILE_FRAME));
    }

    @Test
    void revokedPermanentThenMonthlyGrantBecomesTemporaryAndExpires() {
        service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "purchase:badge");
        assertTrue(service.revoke(PLAYER, CosmeticCatalog.SUPPORTER_BADGE));
        Instant expiry = clock.instant().plusSeconds(60);

        service.grantSupporterSubscription(PLAYER, "subscription:alpha", expiry);

        assertEquals(expiry, repository.expiresAt(PLAYER, CosmeticCatalog.SUPPORTER_BADGE,
                "subscription:alpha").toInstant());
        assertEquals(Set.of("purchase:badge", "subscription:alpha"),
                repository.sources(PLAYER, CosmeticCatalog.SUPPORTER_BADGE));
        clock.set(expiry);
        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.SUPPORTER_BADGE));
    }

    @Test
    void refundRevokesOnlyItsStableSourceAndKeepsAnotherGrantSelected() {
        service.grantPermanent(PLAYER, CosmeticCatalog.SUPPORTER_BADGE, "stripe:line:item-1");
        service.select(PLAYER, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE);
        Instant expiry = clock.instant().plusSeconds(3_600);
        service.grantUntil(PLAYER, CosmeticCatalog.SUPPORTER_BADGE,
                "stripe:subscription:sub-1", expiry);

        assertEquals(1, service.revokeEntitlementSource(PLAYER, "stripe:line:item-1"));
        assertEquals(0, service.revokeEntitlementSource(PLAYER, "stripe:line:item-1"));

        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertTrue(inventory.entitled(CosmeticCatalog.SUPPORTER_BADGE));
        assertEquals(CosmeticCatalog.SUPPORTER_BADGE,
                inventory.selections().get(CosmeticSlot.BADGE));
        assertEquals(expiry, inventory.entitlementExpirations()
                .get(CosmeticCatalog.SUPPORTER_BADGE));
    }

    @Test
    void effectiveExpiryFallsBackToTheNextActiveSourceAfterRefund() {
        Instant shorter = clock.instant().plusSeconds(1_800);
        Instant longer = clock.instant().plusSeconds(3_600);
        service.grantUntil(PLAYER, CosmeticCatalog.SUPPORTER_BADGE,
                "stripe:subscription:short", shorter);
        service.grantUntil(PLAYER, CosmeticCatalog.SUPPORTER_BADGE,
                "stripe:subscription:long", longer);
        service.select(PLAYER, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE);
        assertEquals(longer, service.inventory(PLAYER).entitlementExpirations()
                .get(CosmeticCatalog.SUPPORTER_BADGE));

        assertEquals(1, service.revokeEntitlementSource(PLAYER, "stripe:subscription:long"));

        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertEquals(shorter, inventory.entitlementExpirations().get(CosmeticCatalog.SUPPORTER_BADGE));
        assertEquals(CosmeticCatalog.SUPPORTER_BADGE,
                inventory.selections().get(CosmeticSlot.BADGE));
    }

    @Test
    void refundedOrDisputedSourceCannotBeResurrectedByReplayedSuccess() {
        String source = "stripe:line:item-refunded";
        service.grantPermanent(PLAYER, CosmeticCatalog.COOKIE_CHEER, source);
        service.select(PLAYER, CosmeticSlot.EMOTE, CosmeticCatalog.COOKIE_CHEER);

        assertEquals(1, service.revokeEntitlementSource(PLAYER, source));
        service.grantPermanent(PLAYER, CosmeticCatalog.COOKIE_CHEER, source);

        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.COOKIE_CHEER));
        assertFalse(service.inventory(PLAYER).selections().containsKey(CosmeticSlot.EMOTE));
        assertTrue(repository.revokedAt(PLAYER, CosmeticCatalog.COOKIE_CHEER, source) != null);
    }

    @Test
    void cancelAtPeriodEndKeepsAccessUntilExclusiveExpiry() {
        Instant periodEnd = clock.instant().plusSeconds(3_600);
        service.grantSupporterSubscription(PLAYER, "stripe:subscription:sub-1", periodEnd);
        service.select(PLAYER, CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT);

        clock.set(periodEnd.minusMillis(1));
        assertTrue(service.inventory(PLAYER).entitled(CosmeticCatalog.LOBBY_FLIGHT));
        clock.set(periodEnd);
        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.LOBBY_FLIGHT));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
