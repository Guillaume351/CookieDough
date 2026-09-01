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
            "actions/purchase", "actions/upgrades", "actions/home", "actions/storage", "actions/palette",
            "actions/generator", "actions/progress", "actions/workers", "actions/coop", "actions/market",
            "actions/quick_play", "actions/games", "actions/goals", "actions/friends",
            "actions/party", "actions/events", "actions/app", "actions/help",
            "categories/quick_buy", "categories/blocks", "categories/weapons", "categories/armor",
            "categories/tools", "categories/ranged", "categories/utility", "categories/cookie_specials",
            "kits/microbattles/default", "kits/microbattles/explosive_archer",
            "kits/microbattles/enderman", "kits/microbattles/knockback_warrior",
            "kits/microbattles/tank", "kits/microbattles/ninja", "kits/microbattles/archer",
            "kits/microbattles/berserker", "kits/microbattles/chemist", "kits/microbattles/assassin",
            "kits/microbattles/miner", "kits/microbattles/vampire", "kits/microbattles/frost_mage",
            "kits/microbattles/juggernaut", "kits/microbattles/trapper", "kits/microbattles/alchemist",
            "kits/microbattles/mobility",
            "kits/skywars/scout", "kits/skywars/armorer", "kits/skywars/builder", "kits/skywars/healer",
            "bedwars/offers/wool", "bedwars/offers/oak_planks", "bedwars/offers/end_stone",
            "bedwars/offers/ladder", "bedwars/offers/blastproof_glass", "bedwars/offers/obsidian",
            "bedwars/offers/stone_sword", "bedwars/offers/iron_sword", "bedwars/offers/diamond_sword",
            "bedwars/offers/chainmail_armor", "bedwars/offers/iron_armor",
            "bedwars/offers/diamond_armor", "bedwars/offers/shears", "bedwars/offers/wooden_pickaxe",
            "bedwars/offers/wooden_axe", "bedwars/offers/bow", "bedwars/offers/arrows",
            "bedwars/offers/fireball", "bedwars/offers/tnt", "bedwars/offers/water_bucket",
            "bedwars/offers/ender_pearl", "bedwars/offers/golden_apple", "bedwars/offers/crumb_bridge",
            "bedwars/offers/sugar_rush", "bedwars/offers/cookie_bomb",
            "bedwars/upgrades/sharpened_swords", "bedwars/upgrades/reinforced_armor",
            "bedwars/upgrades/manic_miner", "bedwars/upgrades/cookie_forge",
            "bedwars/upgrades/heal_pool");

    private BedrockFormImages() { }

    public static void button(SimpleForm.Builder builder, String text, String imageId) {
        path(imageId, enabled(System.getenv(ENABLED_ENVIRONMENT_VARIABLE))).ifPresentOrElse(
                path -> builder.button(text, FormImage.Type.PATH, path),
                () -> builder.button(text));
    }

    public static Optional<String> path(String imageId) {
        return path(imageId, enabled(System.getenv(ENABLED_ENVIRONMENT_VARIABLE)));
    }

    /** True only for an immutable ID supplied by the versioned Bedrock pack. */
    public static boolean isKnown(String imageId) {
        return imageId != null && PACK_IMAGES.contains(imageId);
    }

    /** Review/test surface for proving every rendered action belongs to the pack contract. */
    public static Set<String> knownImageIds() {
        return PACK_IMAGES;
    }

    static Optional<String> path(String imageId, boolean enabled) {
        if (!enabled || !isKnown(imageId)) return Optional.empty();
        return Optional.of(ROOT + imageId + ".png");
    }

    static boolean enabled(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }
}
