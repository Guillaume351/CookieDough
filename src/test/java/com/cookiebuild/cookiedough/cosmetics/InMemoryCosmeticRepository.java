package com.cookiebuild.cookiedough.cosmetics;

import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class InMemoryCosmeticRepository implements CosmeticRepository {
    private record EntitlementKey(String cosmeticId, String source) {
    }

    private record StoredEntitlement(Date grantedAt, Date expiresAt, Date revokedAt) {
        private boolean active(Date at) {
            return revokedAt == null && (expiresAt == null || expiresAt.after(at));
        }
    }

    private final Map<UUID, Map<EntitlementKey, StoredEntitlement>> entitlements = new HashMap<>();
    private final Map<UUID, EnumMap<CosmeticSlot, String>> selections = new HashMap<>();
    private int selectionWrites;

    @Override
    public synchronized Snapshot load(UUID playerId, Date activeAt) {
        Map<EntitlementKey, StoredEntitlement> rows = entitlements.getOrDefault(playerId, Map.of());
        Set<String> active = new HashSet<>();
        Set<String> permanent = new HashSet<>();
        Map<String, Date> expirations = new HashMap<>();
        rows.forEach((key, row) -> {
            if (!row.active(activeAt)) return;
            active.add(key.cosmeticId());
            if (row.expiresAt() == null) {
                permanent.add(key.cosmeticId());
                expirations.remove(key.cosmeticId());
            } else if (!permanent.contains(key.cosmeticId())) {
                expirations.merge(key.cosmeticId(), row.expiresAt(),
                        (left, right) -> left.after(right) ? left : right);
            }
        });
        return new Snapshot(active, expirations,
                selections.getOrDefault(playerId, new EnumMap<>(CosmeticSlot.class)));
    }

    @Override
    public synchronized PersistenceResult selectIfEntitled(
            UUID playerId, CosmeticSlot slot, String cosmeticId, Date selectedAt) {
        boolean entitled = entitlements.getOrDefault(playerId, Map.of()).entrySet().stream()
                .anyMatch(entry -> entry.getKey().cosmeticId().equals(cosmeticId)
                        && entry.getValue().active(selectedAt));
        if (!entitled) return PersistenceResult.NOT_ENTITLED;
        selections.computeIfAbsent(playerId, ignored -> new EnumMap<>(CosmeticSlot.class))
                .put(slot, cosmeticId);
        selectionWrites++;
        return PersistenceResult.SELECTED;
    }

    @Override
    public synchronized boolean deselect(UUID playerId, CosmeticSlot slot) {
        Map<CosmeticSlot, String> equipped = selections.get(playerId);
        return equipped != null && equipped.remove(slot) != null;
    }

    @Override
    public synchronized void grantAll(
            UUID playerId,
            List<String> cosmeticIds,
            String source,
            Date grantedAt,
            Date expiresAt,
            Map<CosmeticSlot, String> selectIfEmpty) {
        Map<EntitlementKey, StoredEntitlement> rows = entitlements.computeIfAbsent(playerId,
                ignored -> new HashMap<>());
        for (String cosmeticId : cosmeticIds) {
            EntitlementKey key = new EntitlementKey(cosmeticId, source);
            StoredEntitlement existing = rows.get(key);
            if (existing == null) {
                rows.put(key, new StoredEntitlement(grantedAt, expiresAt, null));
                continue;
            }
            // A refund/dispute is terminal for a stable grant source. A late or
            // replayed success event cannot resurrect it.
            if (existing.revokedAt() != null) continue;
            Date firstGrant = existing.grantedAt().before(grantedAt) ? existing.grantedAt() : grantedAt;
            Date effectiveExpiry = existing.expiresAt() == null || expiresAt == null
                    ? null
                    : (existing.expiresAt().after(expiresAt) ? existing.expiresAt() : expiresAt);
            rows.put(key, new StoredEntitlement(firstGrant, effectiveExpiry, null));
        }
        EnumMap<CosmeticSlot, String> equipped = selections.computeIfAbsent(playerId,
                ignored -> new EnumMap<>(CosmeticSlot.class));
        selectIfEmpty.forEach(equipped::putIfAbsent);
    }

    @Override
    public synchronized int revokeTemporary(
            UUID playerId, List<String> cosmeticIds, String source, Date revokedAt) {
        Map<EntitlementKey, StoredEntitlement> rows = entitlements.getOrDefault(playerId, Map.of());
        int revoked = 0;
        for (Map.Entry<EntitlementKey, StoredEntitlement> entry : rows.entrySet()) {
            EntitlementKey key = entry.getKey();
            StoredEntitlement row = entry.getValue();
            if (!cosmeticIds.contains(key.cosmeticId()) || !key.source().equals(source)
                    || row.expiresAt() == null || row.revokedAt() != null) continue;
            entry.setValue(new StoredEntitlement(row.grantedAt(), row.expiresAt(), revokedAt));
            revoked++;
        }
        return revoked;
    }

    @Override
    public synchronized int revokeSource(UUID playerId, String source, Date revokedAt) {
        Map<EntitlementKey, StoredEntitlement> rows = entitlements.getOrDefault(playerId, Map.of());
        int revoked = 0;
        for (Map.Entry<EntitlementKey, StoredEntitlement> entry : rows.entrySet()) {
            StoredEntitlement row = entry.getValue();
            if (!entry.getKey().source().equals(source) || row.revokedAt() != null) continue;
            entry.setValue(new StoredEntitlement(row.grantedAt(), row.expiresAt(), revokedAt));
            revoked++;
        }
        return revoked;
    }

    @Override
    public synchronized boolean revoke(UUID playerId, String cosmeticId, Date revokedAt) {
        Map<EntitlementKey, StoredEntitlement> rows = entitlements.getOrDefault(playerId, Map.of());
        boolean revoked = false;
        for (Map.Entry<EntitlementKey, StoredEntitlement> entry : rows.entrySet()) {
            StoredEntitlement row = entry.getValue();
            if (!entry.getKey().cosmeticId().equals(cosmeticId) || !row.active(revokedAt)) continue;
            entry.setValue(new StoredEntitlement(row.grantedAt(), row.expiresAt(), revokedAt));
            revoked = true;
        }
        return revoked;
    }

    void injectSelection(UUID playerId, CosmeticSlot slot, String cosmeticId) {
        selections.computeIfAbsent(playerId, ignored -> new EnumMap<>(CosmeticSlot.class))
                .put(slot, cosmeticId);
    }

    int selectionWrites() {
        return selectionWrites;
    }

    Date expiresAt(UUID playerId, String cosmeticId, String source) {
        StoredEntitlement row = entitlements.getOrDefault(playerId, Map.of())
                .get(new EntitlementKey(cosmeticId, source));
        return row == null ? null : row.expiresAt();
    }

    Date revokedAt(UUID playerId, String cosmeticId, String source) {
        StoredEntitlement row = entitlements.getOrDefault(playerId, Map.of())
                .get(new EntitlementKey(cosmeticId, source));
        return row == null ? null : row.revokedAt();
    }

    Set<String> sources(UUID playerId, String cosmeticId) {
        Set<String> result = new HashSet<>();
        entitlements.getOrDefault(playerId, Map.of()).keySet().stream()
                .filter(key -> key.cosmeticId().equals(cosmeticId))
                .map(EntitlementKey::source)
                .forEach(result::add);
        return Set.copyOf(result);
    }
}
