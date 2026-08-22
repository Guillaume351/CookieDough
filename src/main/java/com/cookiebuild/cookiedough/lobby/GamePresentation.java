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
        String descriptionKey,
        ReleaseStage releaseStage) {
    public enum ReleaseStage {
        STABLE,
        BETA,
        COMING_SOON
    }

    private static final List<GamePresentation> GAMES = List.of(
            new GamePresentation("MicroBattles", Material.RED_CONCRETE, EntityType.IRON_GOLEM,
                    "game.description.microbattles", ReleaseStage.STABLE),
            new GamePresentation("Pitchout", Material.SLIME_BALL, EntityType.SLIME,
                    "game.description.pitchout", ReleaseStage.STABLE),
            new GamePresentation("SkyWars", Material.ENDER_EYE, EntityType.ALLAY,
                    "game.description.skywars", ReleaseStage.STABLE),
            new GamePresentation("BuildBattles", Material.CRAFTING_TABLE, EntityType.VILLAGER,
                    "game.description.buildbattles", ReleaseStage.STABLE),
            new GamePresentation("TurfWars", Material.BOW, EntityType.SHEEP,
                    "game.description.turfwars", ReleaseStage.STABLE),
            new GamePresentation("BedWars", Material.RED_BED, EntityType.FOX,
                    "game.description.bedwars", ReleaseStage.BETA),
            new GamePresentation("Skyblock", Material.GRASS_BLOCK, EntityType.BEE,
                    "game.description.skyblock", ReleaseStage.BETA));

    public static List<GamePresentation> games() {
        return GAMES;
    }

    public static GamePresentation forGame(String gameName) {
        return GAMES.stream()
                .filter(game -> game.gameName().equalsIgnoreCase(gameName))
                .findFirst()
                .orElse(new GamePresentation(gameName, Material.NETHER_STAR, EntityType.VILLAGER,
                        "game.description.unknown", ReleaseStage.STABLE));
    }

    public String description(Locale locale) {
        return LocaleManager.getMessage(descriptionKey, locale);
    }

    public String displayName(Locale locale) {
        return switch (releaseStage) {
            case BETA -> gameName + " [" + LocaleManager.getMessage("game.stage.beta", locale) + "]";
            case COMING_SOON -> gameName + " ["
                    + LocaleManager.getMessage("game.stage.coming_soon", locale) + "]";
            case STABLE -> gameName;
        };
    }

    public String statusHint(Locale locale) {
        return switch (releaseStage) {
            case BETA -> LocaleManager.getMessage("game.stage.beta_hint", locale);
            case COMING_SOON -> LocaleManager.getMessage("game.stage.coming_soon_hint", locale);
            case STABLE -> LocaleManager.getMessage("game.stage.stable_hint", locale);
        };
    }

    public boolean persistent() {
        return "Skyblock".equalsIgnoreCase(gameName);
    }
}
