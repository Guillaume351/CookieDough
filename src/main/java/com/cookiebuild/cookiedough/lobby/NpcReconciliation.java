package com.cookiebuild.cookiedough.lobby;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure selection policy used when persisted lobby NPCs must be reconciled. */
final class NpcReconciliation {
    record Candidate(UUID id, boolean current, double distanceSquared) {
    }

    private NpcReconciliation() {
    }

    static UUID selectCanonical(List<Candidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparing(Candidate::current).reversed()
                        .thenComparingDouble(Candidate::distanceSquared)
                        .thenComparing(candidate -> candidate.id().toString()))
                .map(Candidate::id)
                .orElse(null);
    }
}
