package com.cookiebuild.cookiedough.game;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Quick Play selection that concentrates a tiny population instead of
 * spreading it: a match that one more player makes ready first, then the most
 * populated queue, then the fixed featured mode. Empty queues are never
 * rotated, so two players arriving minutes apart land in the same queue.
 */
public final class GameSelectionPolicy {
    /** The fixed featured mode: touch-friendly, non-PvP and the first mode of most activated players. */
    public static final String FEATURED_GAME = "BuildBattles";

    /**
     * Stable modes used, in order, only when the featured mode has no open
     * arena. Beta modes are deliberately absent so they are never the default.
     */
    static final List<String> FALLBACK_ORDER = List.of(
            FEATURED_GAME, "MicroBattles", "Pitchout", "SkyWars", "TurfWars");

    public Game select(Collection<? extends Game> candidates) {
        return select(candidates, game -> 0);
    }

    /**
     * @param waitingIntents passive players (e.g. in Skyblock) already queued
     *        for an arena; they count toward its population.
     */
    public Game select(Collection<? extends Game> candidates, ToIntFunction<Game> waitingIntents) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        ToIntFunction<Game> intents = waitingIntents == null ? game -> 0 : waitingIntents;
        List<Game> eligible = candidates.stream()
                .filter(Objects::nonNull)
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(Game::isAdmissionsOpen)
                .filter(Game::isQuickPlayEligible)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .map(game -> (Game) game)
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }

        ToIntFunction<Game> population = game -> game.getPlayerCount() + Math.max(0, intents.applyAsInt(game));
        List<Game> populated = eligible.stream().filter(game -> population.applyAsInt(game) > 0).toList();
        Comparator<Game> stableOrder = Comparator.comparingInt(GameSelectionPolicy::fallbackRank)
                .thenComparing(Game::getGameName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Game::getPlayerCount, Comparator.reverseOrder())
                .thenComparing(game -> game.getGameId().toString());
        if (!populated.isEmpty()) {
            // Ready-with-one-more first (fewest players missing), then the
            // busiest queue, then the featured/stable order as a tie-break.
            return populated.stream()
                    .min(Comparator.<Game>comparingInt(game -> playersNeededToStart(game, population))
                            .thenComparing(Comparator.comparingInt(population).reversed())
                            .thenComparing(stableOrder))
                    .orElse(null);
        }
        return eligible.stream().min(stableOrder).orElse(null);
    }

    /**
     * Busiest other mode whose queue already has (passive or admitted)
     * players and can still admit one more, or {@code null}. Used to offer a
     * consented switch to a player waiting alone.
     */
    public static Game busiestOtherQueue(Collection<? extends Game> candidates, String excludedGameName,
            ToIntFunction<Game> waitingIntents) {
        if (candidates == null) return null;
        ToIntFunction<Game> intents = waitingIntents == null ? game -> 0 : waitingIntents;
        ToIntFunction<Game> population = game -> game.getPlayerCount() + Math.max(0, intents.applyAsInt(game));
        return candidates.stream()
                .filter(Objects::nonNull)
                .map(game -> (Game) game)
                .filter(game -> excludedGameName == null || !game.getGameName().equalsIgnoreCase(excludedGameName))
                .filter(game -> game.getState() == GameState.OPEN && game.isAdmissionsOpen())
                .filter(Game::isQuickPlayEligible)
                .filter(game -> population.applyAsInt(game) > 0 && population.applyAsInt(game) < game.getCapacity())
                .max(Comparator.comparingInt(population)
                        .thenComparing(Comparator.comparingInt(GameSelectionPolicy::fallbackRank).reversed()))
                .orElse(null);
    }

    public static boolean isFeatured(String gameName) {
        return gameName != null && FEATURED_GAME.equalsIgnoreCase(gameName);
    }

    private static int playersNeededToStart(Game game, ToIntFunction<Game> population) {
        return Math.max(0, game.getMinimumPlayers() - population.applyAsInt(game));
    }

    private static int fallbackRank(Game game) {
        String name = game.getGameName().toLowerCase(Locale.ROOT);
        for (int index = 0; index < FALLBACK_ORDER.size(); index++) {
            if (FALLBACK_ORDER.get(index).toLowerCase(Locale.ROOT).equals(name)) return index;
        }
        return FALLBACK_ORDER.size();
    }
}
