package com.cookiebuild.cookiedough.cosmetics;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-authoritative entitlement and equipment rules. */
public final class CosmeticService {
    public enum SelectionResult {
        SELECTED,
        DESELECTED,
        ALREADY_DESELECTED,
        UNKNOWN_COSMETIC,
        SLOT_MISMATCH,
        NOT_ENTITLED
    }

    public enum GrantResult {
        GRANTED,
        UNKNOWN_COSMETIC
    }

    public record InventoryItem(CosmeticDefinition cosmetic, boolean entitled, boolean selected) {
    }

    public record Inventory(
            List<InventoryItem> items,
            Map<CosmeticSlot, String> selections,
            Map<String, Instant> entitlementExpirations,
            Map<CosmeticSlot, Instant> selectionExpirations) {
        public Inventory {
            items = List.copyOf(items);
            selections = Map.copyOf(selections);
            entitlementExpirations = Map.copyOf(entitlementExpirations);
            selectionExpirations = Map.copyOf(selectionExpirations);
        }

        public boolean entitled(String cosmeticId) {
            return items.stream().anyMatch(item -> item.entitled()
                    && item.cosmetic().id().equals(cosmeticId));
        }
    }

    private final CosmeticRepository repository;
    private final Clock clock;
    private volatile Consumer<UUID> changeListener = ignored -> { };

    public CosmeticService(CosmeticRepository repository) {
        this(repository, Clock.systemUTC());
    }

    CosmeticService(CosmeticRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Inventory inventory(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CosmeticRepository.Snapshot snapshot = repository.load(playerId, Date.from(clock.instant()));
        EnumMap<CosmeticSlot, String> validSelections = new EnumMap<>(CosmeticSlot.class);
        EnumMap<CosmeticSlot, Instant> selectionExpirations = new EnumMap<>(CosmeticSlot.class);
        snapshot.selections().forEach((slot, cosmeticId) -> CosmeticCatalog.find(cosmeticId)
                .filter(item -> item.slot() == slot)
                .filter(item -> item.free() || snapshot.activeEntitlements().contains(item.id()))
                .ifPresent(item -> {
                    validSelections.put(slot, item.id());
                    Date expiresAt = item.free() ? null : snapshot.entitlementExpirations().get(item.id());
                    if (expiresAt != null) selectionExpirations.put(slot, expiresAt.toInstant());
                }));
        List<InventoryItem> items = new ArrayList<>();
        for (CosmeticDefinition item : CosmeticCatalog.items()) {
            boolean entitled = item.free() || snapshot.activeEntitlements().contains(item.id());
            boolean selected = entitled && (item.selectionRequired()
                    ? item.id().equals(validSelections.get(item.slot()))
                    : true);
            items.add(new InventoryItem(item, entitled, selected));
        }
        Map<String, Instant> expirations = new java.util.HashMap<>();
        snapshot.entitlementExpirations().forEach((id, date) -> {
            if (!CosmeticCatalog.isFree(id)) expirations.put(id, date.toInstant());
        });
        return new Inventory(items, validSelections, expirations, selectionExpirations);
    }

    public SelectionResult select(UUID playerId, CosmeticSlot requestedSlot, String cosmeticId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(requestedSlot, "requestedSlot");
        Optional<CosmeticDefinition> definition = CosmeticCatalog.find(cosmeticId);
        if (definition.isEmpty()) return SelectionResult.UNKNOWN_COSMETIC;
        if (definition.get().slot() != requestedSlot) return SelectionResult.SLOT_MISMATCH;
        CosmeticRepository.PersistenceResult persisted = repository.selectIfEntitled(
                playerId, requestedSlot, cosmeticId, Date.from(clock.instant()));
        SelectionResult result = persisted == CosmeticRepository.PersistenceResult.SELECTED
                ? SelectionResult.SELECTED
                : SelectionResult.NOT_ENTITLED;
        if (result == SelectionResult.SELECTED) notifyChange(playerId);
        return result;
    }

    public SelectionResult deselect(UUID playerId, CosmeticSlot slot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(slot, "slot");
        SelectionResult result = repository.deselect(playerId, slot)
                ? SelectionResult.DESELECTED
                : SelectionResult.ALREADY_DESELECTED;
        if (result == SelectionResult.DESELECTED) notifyChange(playerId);
        return result;
    }

    /** Trusted grant hook for a future verified order/admin flow; never called by player UI. */
    public GrantResult grantPermanent(UUID playerId, String cosmeticId, String source) {
        Objects.requireNonNull(playerId, "playerId");
        if (CosmeticCatalog.find(cosmeticId).isEmpty()) return GrantResult.UNKNOWN_COSMETIC;
        validateSource(source);
        repository.grant(playerId, cosmeticId, source, Date.from(clock.instant()), null);
        notifyChange(playerId);
        return GrantResult.GRANTED;
    }

    /** Trusted subscription hook; expiry is exclusive and must be in the future. */
    public GrantResult grantUntil(UUID playerId, String cosmeticId, String source, Instant expiresAt) {
        Objects.requireNonNull(playerId, "playerId");
        if (CosmeticCatalog.find(cosmeticId).isEmpty()) return GrantResult.UNKNOWN_COSMETIC;
        validateSource(source);
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("A future entitlement expiry is required");
        }
        repository.grant(playerId, cosmeticId, source,
                Date.from(clock.instant()), Date.from(expiresAt));
        notifyChange(playerId);
        return GrantResult.GRANTED;
    }

    /**
     * Atomic, provider-neutral monthly Supporter grant. Badge and profile frame
     * are selected only when their slots are currently empty; flight stays an
     * explicit player toggle and join flair is automatic while entitled.
     */
    public GrantResult grantSupporterSubscription(UUID playerId, String source, Instant expiresAt) {
        Objects.requireNonNull(playerId, "playerId");
        validateSource(source);
        Instant now = clock.instant();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("A future subscription expiry is required");
        }
        repository.grantAll(playerId, List.of(
                        CosmeticCatalog.SUPPORTER_BADGE,
                        CosmeticCatalog.LOBBY_FLIGHT,
                        CosmeticCatalog.SUPPORTER_JOIN_FLAIR,
                        CosmeticCatalog.SUPPORTER_PROFILE_FRAME),
                source, Date.from(now), Date.from(expiresAt), Map.of(
                        CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE,
                        CosmeticSlot.PROFILE_FRAME, CosmeticCatalog.SUPPORTER_PROFILE_FRAME));
        notifyChange(playerId);
        return GrantResult.GRANTED;
    }

    /**
     * Immediately terminates temporary rows from this subscription source.
     * A cancel-at-period-end webhook must leave the rows active with their
     * existing expiry and must not call this method before the effective end.
     */
    public int revokeSupporterSubscription(UUID playerId, String source) {
        Objects.requireNonNull(playerId, "playerId");
        validateSource(source);
        int revoked = repository.revokeTemporary(playerId, List.of(
                        CosmeticCatalog.SUPPORTER_BADGE,
                        CosmeticCatalog.LOBBY_FLIGHT,
                        CosmeticCatalog.SUPPORTER_JOIN_FLAIR,
                        CosmeticCatalog.SUPPORTER_PROFILE_FRAME),
                source, Date.from(clock.instant()));
        if (revoked > 0) notifyChange(playerId);
        return revoked;
    }

    /**
     * Provider-neutral refund/dispute hook. The source must identify the stable
     * commercial grant, never an individual webhook delivery/event.
     */
    public int revokeEntitlementSource(UUID playerId, String source) {
        Objects.requireNonNull(playerId, "playerId");
        validateSource(source);
        int revoked = repository.revokeSource(playerId, source, Date.from(clock.instant()));
        if (revoked > 0) notifyChange(playerId);
        return revoked;
    }

    public boolean revoke(UUID playerId, String cosmeticId) {
        Objects.requireNonNull(playerId, "playerId");
        if (CosmeticCatalog.find(cosmeticId).isEmpty()) return false;
        boolean revoked = repository.revoke(playerId, cosmeticId, Date.from(clock.instant()));
        if (revoked) notifyChange(playerId);
        return revoked;
    }

    void onChange(Consumer<UUID> listener) {
        changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void notifyChange(UUID playerId) {
        try {
            changeListener.accept(playerId);
        } catch (RuntimeException ignored) {
            // Persistence already committed; cache polling remains the fallback.
        }
    }

    private static void validateSource(String source) {
        if (source == null || source.isBlank() || source.length() > 128) {
            throw new IllegalArgumentException("A bounded entitlement source is required");
        }
    }
}
