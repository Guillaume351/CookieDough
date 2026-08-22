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
                GamePresentation.games().stream().map(game -> new Entry(
                        "game:details:" + game.gameName(), game.icon(), game.bedrockTexture(),
                        game.displayName(locale), game.description(locale))).toList());
    }

    public static HubGameMenuModel detail(String gameName, Locale locale) {
        GamePresentation game = GamePresentation.find(gameName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown game: " + gameName));
        String content = game.description(locale)
                + "\n\n" + message("hub.game.detail.rules", locale) + "\n" + game.rules(locale)
                + "\n\n" + message("hub.game.detail.status", locale) + "\n" + game.statusHint(locale);
        return new HubGameMenuModel(game.displayName(locale), content, List.of(
                new Entry("game:join:" + game.gameName(), Material.LIME_DYE,
                        "actions/join",
                        message("hub.game.detail.join", locale, game.gameName()),
                        message("hub.game.detail.join_lore", locale)),
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
