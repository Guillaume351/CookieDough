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
        String rewardCosmetic, String rewardSource, Celebration celebration) {
    /**
     * What a player sees and hears on a find: an item-break burst of {@code item}
     * ({@value #SPOT_ITEM} for the block the spot was shown as), a few
     * {@code particle}s and a short {@code sound} next to a chime.
     */
    public record Celebration(String item, org.bukkit.Particle particle, String sound) {
        public static final String SPOT_ITEM = "SPOT";

        /** Halloween's pumpkin burst, souls and witch laugh: the default of a theme file. */
        public static final Celebration HALLOWEEN = new Celebration("JACK_O_LANTERN", org.bukkit.Particle.SOUL,
                "entity.witch.celebrate");

        public Celebration {
            if (item == null || item.isBlank() || particle == null || sound == null || sound.isBlank()) {
                throw new IllegalArgumentException("A hunt celebration needs an item, a particle and a sound");
            }
            item = item.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Entitlement source of the reward cosmetic when a theme file names none.
     * It keeps Halloween 2026's {@code halloween-2026:pumpkins}: grants are
     * idempotent per (player, cosmetic, source), so it must never change for
     * an edition that already paid rewards.
     */
    public static String defaultRewardSource(String huntId) {
        return huntId + ":pumpkins";
    }

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
        if (rewardSource == null || rewardSource.isBlank()) rewardSource = defaultRewardSource(id);
        if (rewardSource.length() > 128) throw new IllegalArgumentException("Reward source too long");
        if (celebration == null) celebration = Celebration.HALLOWEEN;
    }

    /** Same hunt with only the spots that matched the real world. */
    public HuntPlan withSpots(List<Spot> validSpots) {
        return new HuntPlan(id, world, validSpots, coinsPerFind, completionCoins, rewardCosmetic, rewardSource,
                celebration);
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
