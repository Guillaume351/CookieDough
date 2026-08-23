package com.cookiebuild.cookiedough.ui;

import java.util.Optional;
import java.util.Set;

import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;

/** Versioned custom-pack image contract with a text-only fail-safe. */
public final class BedrockFormImages {
    public static final String ENABLED_ENVIRONMENT_VARIABLE = "COOKIEBUILD_BEDROCK_UI_PACK_ENABLED";
    public static final String ROOT = "textures/ui/cookiebuild/";
    private static final Set<String> PACK_IMAGES = Set.of(
            "modes/microbattles", "modes/pitchout", "modes/skywars", "modes/buildbattles",
            "modes/turfwars", "modes/bedwars", "modes/skyblock",
            "actions/join", "actions/back", "actions/close", "actions/shop", "actions/preview",
            "actions/select", "actions/purchase", "actions/upgrades", "actions/home", "actions/storage",
            "actions/generator", "actions/progress", "actions/workers", "actions/coop", "actions/market",
            "actions/quick_play", "actions/games", "actions/goals", "actions/friends",
            "actions/party", "actions/events", "actions/app", "actions/help",
            "categories/quick_buy", "categories/blocks", "categories/weapons", "categories/armor",
            "categories/tools", "categories/ranged", "categories/utility", "categories/cookie_specials");

    private BedrockFormImages() { }

    public static void button(SimpleForm.Builder builder, String text, String imageId) {
        path(imageId, enabled(System.getenv(ENABLED_ENVIRONMENT_VARIABLE))).ifPresentOrElse(
                path -> builder.button(text, FormImage.Type.PATH, path),
                () -> builder.button(text));
    }

    public static Optional<String> path(String imageId) {
        return path(imageId, enabled(System.getenv(ENABLED_ENVIRONMENT_VARIABLE)));
    }

    static Optional<String> path(String imageId, boolean enabled) {
        if (!enabled || imageId == null || !PACK_IMAGES.contains(imageId)) return Optional.empty();
        return Optional.of(ROOT + imageId + ".png");
    }

    static boolean enabled(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }
}
