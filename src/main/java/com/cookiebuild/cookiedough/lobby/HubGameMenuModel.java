package com.cookiebuild.cookiedough.lobby;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

import org.bukkit.Material;

import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Shared action model rendered by both Java inventories and Bedrock forms. */
public record HubGameMenuModel(String title, String content, List<Entry> entries) {
    public HubGameMenuModel {
        entries = List.copyOf(entries);
    }

    public record Entry(String action, Material icon, String bedrockTexture, String label, String detail) { }

    public static HubGameMenuModel index(Locale locale) {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("quick", Material.NETHER_STAR, "actions/quick_play",
                message("hub.quick.name", locale), message("hub.quick.lore", locale)));
        List<GamePresentation> games = GamePresentation.games();
        java.util.Map<String, ModePopulationService.Snapshot> snapshots = new java.util.HashMap<>();
        games.forEach(game -> snapshots.put(game.gameName(), ModePopulationService.snapshot(game.gameName())));
        entries.addAll(sortedForMenu(games, game -> snapshots.get(game.gameName()).players()).stream().map(game -> {
            ModePopulationService.Snapshot snapshot = snapshots.get(game.gameName());
            String detail = (game.featured() ? message("hub.games.featured", locale) + " · " : "")
                    + game.description(locale) + " · "
                    + availability(snapshot, locale) + " · "
                    + message(snapshot.stateKey(), locale);
            return new Entry("game:details:" + game.gameName(), game.icon(), game.bedrockTexture(),
                    game.displayName(locale), detail);
        }).toList());
        return new HubGameMenuModel(
                message("hub.games.title", locale),
                message("hub.games.content", locale),
                entries);
    }

    public static HubGameMenuModel detail(String gameName, Locale locale) {
        GamePresentation game = GamePresentation.find(gameName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown game: " + gameName));
        String content = game.description(locale)
                + "\n\n" + message("hub.game.detail.rules", locale) + "\n" + game.rules(locale)
                + "\n\n" + message("hub.game.detail.status", locale) + "\n" + game.statusHint(locale);
        ModePopulationService.Snapshot snapshot = ModePopulationService.snapshot(game.gameName());
        String availability = availability(snapshot, locale) + " · "
                + message(snapshot.stateKey(), locale);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(snapshot.available() ? "game:join:" + game.gameName() : "games",
                        snapshot.available() ? Material.LIME_DYE : Material.GRAY_DYE,
                        snapshot.available() ? "actions/join" : "actions/back",
                        message(snapshot.available() ? "hub.game.detail.join" : "hub.game.detail.unavailable",
                                locale, game.readableName(locale)),
                        message(snapshot.available() ? "hub.game.detail.join_lore"
                                : "hub.game.detail.unavailable_lore", locale)));
        if (GameManager.getSpectatableGameByName(game.gameName()) != null) {
            entries.add(new Entry("game:spectate:" + game.gameName(), Material.ENDER_EYE, "actions/preview",
                    message("hub.game.detail.spectate", locale),
                    message("hub.game.detail.spectate_lore", locale)));
        }
        entries.add(new Entry("games", Material.ARROW, "actions/back",
                        message("hub.game.detail.back", locale),
                        message("hub.game.detail.back_lore", locale)));
        entries.add(new Entry("close", Material.BARRIER, "actions/close",
                        message("hub.game.detail.close", locale),
                        message("hub.game.detail.close_lore", locale)));
        return new HubGameMenuModel(game.displayName(locale), content + "\n\n" + availability, entries);
    }

    /** Featured mode first, then the busiest modes; the catalogue order breaks ties. */
    static List<GamePresentation> sortedForMenu(List<GamePresentation> games,
            java.util.function.ToIntFunction<GamePresentation> population) {
        List<GamePresentation> sorted = new ArrayList<>(games);
        sorted.sort(java.util.Comparator.<GamePresentation, Boolean>comparing(game -> !game.featured())
                .thenComparing(java.util.Comparator.comparingInt(population).reversed())
                .thenComparingInt(games::indexOf));
        return sorted;
    }

    /** Encouraging population text: never a bare "0 players". */
    static String availability(ModePopulationService.Snapshot snapshot, Locale locale) {
        if (snapshot.players() <= 0) {
            return snapshot.persistent()
                    ? message("hub.games.solo_ready", locale)
                    : message("hub.games.starts_at", locale, Math.max(2, snapshot.minimumPlayers()));
        }
        return snapshot.players() == 1
                ? message("hub.games.players_one", locale)
                : message("hub.games.players", locale, snapshot.players());
    }

    private static String message(String key, Locale locale, Object... arguments) {
        return LocaleManager.getMessage(key, locale, arguments);
    }
}
