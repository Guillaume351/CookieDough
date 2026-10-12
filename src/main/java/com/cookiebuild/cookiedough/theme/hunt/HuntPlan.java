package com.cookiebuild.cookiedough.theme.hunt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A collectible hunt: blocks of the decor that look like something to find
 * (pumpkins at Halloween, presents at Christmas). {@code id} identifies one
 * edition (e.g. {@code halloween-2026}) so next year's hunt starts fresh.
 */
public record HuntPlan(String id, String world, List<Spot> spots, int coinsPerFind, int completionCoins,
        String rewardCosmetic) {
    /** A real full block shown as {@code block} facing {@code facing} to players who haven't found it yet. */
    public record Spot(String id, int x, int y, int z, String expected, String block) {
        public Spot {
            if (id == null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Bad spot id " + id);
            if (expected == null || expected.isBlank() || block == null || block.isBlank()) {
                throw new IllegalArgumentException("Spot " + id + " needs the real block and the shown block");
            }
        }
    }

    public HuntPlan {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Bad hunt id " + id);
        spots = List.copyOf(spots);
        Set<String> ids = new HashSet<>();
        for (Spot spot : spots) {
            if (!ids.add(spot.id())) throw new IllegalArgumentException("Duplicate hunt spot " + spot.id());
        }
        if (coinsPerFind < 0 || completionCoins < 0) throw new IllegalArgumentException("Negative hunt reward");
    }

    /** What finding one more spot means for a player who had already found {@code before}. */
    public record Outcome(boolean newFind, int found, int total, boolean completed) {
    }

    public static Outcome outcome(Set<String> before, String spotId, int total) {
        boolean newFind = !before.contains(spotId);
        int found = before.size() + (newFind ? 1 : 0);
        return new Outcome(newFind, found, total, newFind && found >= total && total > 0);
    }
}
