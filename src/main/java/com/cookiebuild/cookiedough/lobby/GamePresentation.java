package com.cookiebuild.cookiedough.lobby;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Shared, compact presentation metadata for lobby selectors and player menus. */
public record GamePresentation(
        String gameName,
        Material icon,
        EntityType npcType,
        String descriptionKey) {
    private static final List<GamePresentation> GAMES = List.of(
            new GamePresentation("MicroBattles", Material.RED_CONCRETE, EntityType.IRON_GOLEM,
                    "game.description.microbattles"),
            new GamePresentation("Pitchout", Material.SLIME_BALL, EntityType.SLIME,
                    "game.description.pitchout"),
            new GamePresentation("SkyWars", Material.ENDER_EYE, EntityType.ALLAY,
                    "game.description.skywars"),
            new GamePresentation("BuildBattles", Material.CRAFTING_TABLE, EntityType.VILLAGER,
                    "game.description.buildbattles"),
            new GamePresentation("TurfWars", Material.BOW, EntityType.SHEEP,
                    "game.description.turfwars"));

    public static List<GamePresentation> games() {
        return GAMES;
    }

    public static GamePresentation forGame(String gameName) {
        return GAMES.stream()
                .filter(game -> game.gameName().equalsIgnoreCase(gameName))
                .findFirst()
                .orElse(new GamePresentation(gameName, Material.NETHER_STAR, EntityType.VILLAGER,
                        "game.description.unknown"));
    }

    public String description(Locale locale) {
        return LocaleManager.getMessage(descriptionKey, locale);
    }
}
