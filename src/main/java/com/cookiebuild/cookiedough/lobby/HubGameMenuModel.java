package com.cookiebuild.cookiedough.lobby;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Shared action model rendered by both Java inventories and Bedrock forms. */
public record HubGameMenuModel(String title, String content, List<Entry> entries) {
    public HubGameMenuModel {
        entries = List.copyOf(entries);
    }

    public record Entry(String action, Material icon, String bedrockTexture, String label, String detail) { }

    public static HubGameMenuModel index(Locale locale) {
        return new HubGameMenuModel(
                message("hub.games.title", locale),
                message("hub.games.content", locale),
                GamePresentation.games().stream().map(game -> {
                    ModePopulationService.Snapshot snapshot = ModePopulationService.snapshot(game.gameName());
                    String detail = game.description(locale) + " · "
                            + message("hub.games.players", locale, snapshot.players()) + " · "
                            + message(snapshot.stateKey(), locale);
                    return new Entry("game:details:" + game.gameName(), game.icon(), game.bedrockTexture(),
                            game.displayName(locale), detail);
                }).toList());
    }

    public static HubGameMenuModel detail(String gameName, Locale locale) {
        GamePresentation game = GamePresentation.find(gameName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown game: " + gameName));
        String content = game.description(locale)
                + "\n\n" + message("hub.game.detail.rules", locale) + "\n" + game.rules(locale)
                + "\n\n" + message("hub.game.detail.status", locale) + "\n" + game.statusHint(locale);
        ModePopulationService.Snapshot snapshot = ModePopulationService.snapshot(game.gameName());
        String availability = message("hub.games.players", locale, snapshot.players()) + " · "
                + message(snapshot.stateKey(), locale);
        return new HubGameMenuModel(game.displayName(locale), content + "\n\n" + availability, List.of(
                new Entry(snapshot.available() ? "game:join:" + game.gameName() : "games",
                        snapshot.available() ? Material.LIME_DYE : Material.GRAY_DYE,
                        snapshot.available() ? "actions/join" : "actions/back",
                        message(snapshot.available() ? "hub.game.detail.join" : "hub.game.detail.unavailable",
                                locale, game.gameName()),
                        message(snapshot.available() ? "hub.game.detail.join_lore"
                                : "hub.game.detail.unavailable_lore", locale)),
                new Entry("games", Material.ARROW, "actions/back",
                        message("hub.game.detail.back", locale),
                        message("hub.game.detail.back_lore", locale)),
                new Entry("close", Material.BARRIER, "actions/close",
                        message("hub.game.detail.close", locale),
                        message("hub.game.detail.close_lore", locale))));
    }

    private static String message(String key, Locale locale, Object... arguments) {
        return LocaleManager.getMessage(key, locale, arguments);
    }
}
