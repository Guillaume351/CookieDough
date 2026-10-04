package com.cookiebuild.cookiedough.cosmetics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;

/**
 * The Cookie Build cosmetic collection. IDs are persistence/API contracts and
 * must never be inferred from localized names or inventory titles. Every id is
 * mirrored in the website catalog and the database CHECK constraints.
 */
public final class CosmeticCatalog {
    public static final String COOKIE_SPARKLE_TRAIL = "cookie_sparkle_trail";
    public static final String SUPPORTER_BADGE = "supporter_badge";
    public static final String COOKIE_CRUMB_TRAIL = "cookie_crumb_trail";
    public static final String COOKIE_CHEER = "cookie_cheer";
    public static final String GOLDEN_COOKIE_BURST = "golden_cookie_burst";
    public static final String SUPPORTER_PROFILE_FRAME = "supporter_profile_frame";
    public static final String LOBBY_FLIGHT = "lobby_flight";
    public static final String SUPPORTER_JOIN_FLAIR = "supporter_join_flair";
    /** Coin shop (never sold for real money). */
    public static final String NOTE_TRAIL = "note_trail";
    /** Cheap first coin purchase (release bb-gallery-20261003; website migration 0025). */
    public static final String STARTER_SPARK_TRAIL = "starter_spark_trail";
    public static final String HEART_TRAIL = "heart_trail";
    /** Reward-only: day 7 of the in-game login calendar. */
    public static final String STREAK_STAR_TRAIL = "streak_star_trail";
    /** Reward-only: linking the mobile app (granted through player_reward_grants). */
    public static final String APP_COMPANION_BADGE = "app_companion_badge";
    /* Release polish-20261004 (website migration 0026): coin items at every price point. */
    public static final String CHOCOLATE_CHIP_TRAIL = "chocolate_chip_trail";
    public static final String CHERRY_PETAL_TRAIL = "cherry_petal_trail";
    public static final String SOUL_FLAME_TRAIL = "soul_flame_trail";
    public static final String RAINBOW_TRAIL = "rainbow_trail";
    public static final String COOKIE_RAIN_VICTORY = "cookie_rain_victory";
    public static final String TOTEM_VICTORY = "totem_victory";
    public static final String FIREWORK_VICTORY = "firework_victory";
    /** Reward-only: the lifetime "ten matches" achievement. */
    public static final String LUCKY_CLOVER_TRAIL = "lucky_clover_trail";

    /*
     * Prices follow measured earnings (Oct 2026): a median active day earns ~55
     * coins, the 75th percentile ~200. 150 is a first-day impulse buy for an
     * active player; 2 000 is a long-term goal.
     */
    public static final int CHOCOLATE_CHIP_TRAIL_PRICE = 150;
    public static final int STARTER_SPARK_TRAIL_PRICE = 250;
    public static final int CHERRY_PETAL_TRAIL_PRICE = 400;
    public static final int COOKIE_RAIN_VICTORY_PRICE = 500;
    public static final int SOUL_FLAME_TRAIL_PRICE = 600;
    public static final int NOTE_TRAIL_PRICE = 750;
    public static final int TOTEM_VICTORY_PRICE = 900;
    public static final int HEART_TRAIL_PRICE = 1_200;
    public static final int FIREWORK_VICTORY_PRICE = 1_500;
    public static final int RAINBOW_TRAIL_PRICE = 2_000;

    private static final List<CosmeticDefinition> ITEMS = List.of(
            definition(SUPPORTER_BADGE, CosmeticSlot.BADGE, Material.NAME_TAG),
            definition(COOKIE_CRUMB_TRAIL, CosmeticSlot.HUB_TRAIL, Material.COOKIE),
            definition(COOKIE_CHEER, CosmeticSlot.EMOTE, Material.FIREWORK_ROCKET),
            definition(GOLDEN_COOKIE_BURST, CosmeticSlot.VICTORY_EFFECT, Material.GOLDEN_APPLE),
            definition(SUPPORTER_PROFILE_FRAME, CosmeticSlot.PROFILE_FRAME, Material.ITEM_FRAME),
            definition(LOBBY_FLIGHT, CosmeticSlot.LOBBY_FLIGHT, Material.FEATHER),
            definition(SUPPORTER_JOIN_FLAIR, CosmeticSlot.JOIN_FLAIR, Material.GLOW_BERRIES, false),
            new CosmeticDefinition(COOKIE_SPARKLE_TRAIL, CosmeticSlot.HUB_TRAIL, Material.GLOWSTONE_DUST,
                    "cosmetics.item.cookie_sparkle_trail.name", "cosmetics.item.cookie_sparkle_trail.description", true, true),
            coins(CHOCOLATE_CHIP_TRAIL, CosmeticSlot.HUB_TRAIL, Material.COCOA_BEANS, CHOCOLATE_CHIP_TRAIL_PRICE),
            coins(STARTER_SPARK_TRAIL, CosmeticSlot.HUB_TRAIL, Material.FIREWORK_STAR, STARTER_SPARK_TRAIL_PRICE),
            coins(CHERRY_PETAL_TRAIL, CosmeticSlot.HUB_TRAIL, Material.PINK_PETALS, CHERRY_PETAL_TRAIL_PRICE),
            coins(COOKIE_RAIN_VICTORY, CosmeticSlot.VICTORY_EFFECT, Material.COOKIE, COOKIE_RAIN_VICTORY_PRICE),
            coins(SOUL_FLAME_TRAIL, CosmeticSlot.HUB_TRAIL, Material.SOUL_TORCH, SOUL_FLAME_TRAIL_PRICE),
            coins(NOTE_TRAIL, CosmeticSlot.HUB_TRAIL, Material.NOTE_BLOCK, NOTE_TRAIL_PRICE),
            coins(TOTEM_VICTORY, CosmeticSlot.VICTORY_EFFECT, Material.TOTEM_OF_UNDYING, TOTEM_VICTORY_PRICE),
            coins(HEART_TRAIL, CosmeticSlot.HUB_TRAIL, Material.POPPY, HEART_TRAIL_PRICE),
            coins(FIREWORK_VICTORY, CosmeticSlot.VICTORY_EFFECT, Material.FIREWORK_ROCKET, FIREWORK_VICTORY_PRICE),
            coins(RAINBOW_TRAIL, CosmeticSlot.HUB_TRAIL, Material.PRISMARINE_CRYSTALS, RAINBOW_TRAIL_PRICE),
            reward(STREAK_STAR_TRAIL, CosmeticSlot.HUB_TRAIL, Material.NETHER_STAR, "cosmetics.unlock.streak"),
            reward(LUCKY_CLOVER_TRAIL, CosmeticSlot.HUB_TRAIL, Material.LILY_PAD, "cosmetics.unlock.ten_matches"),
            reward(APP_COMPANION_BADGE, CosmeticSlot.BADGE, Material.COMPASS, "cosmetics.unlock.app"));
    private static final Map<String, CosmeticDefinition> BY_ID;

    static {
        Map<String, CosmeticDefinition> byId = new LinkedHashMap<>();
        for (CosmeticDefinition item : ITEMS) {
            if (byId.put(item.id(), item) != null) {
                throw new IllegalStateException("Duplicate cosmetic id " + item.id());
            }
        }
        BY_ID = Map.copyOf(byId);
    }

    private CosmeticCatalog() {
    }

    public static List<CosmeticDefinition> items() {
        return ITEMS;
    }

    public static boolean isFree(String id) {
        return find(id).map(CosmeticDefinition::free).orElse(false);
    }

    public static Optional<CosmeticDefinition> find(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    /** Cosmetics a player can buy in game; every price is a server-side constant. */
    public static List<CosmeticDefinition> coinShop() {
        return ITEMS.stream().filter(CosmeticDefinition::coinPurchasable).toList();
    }

    private static CosmeticDefinition coins(String id, CosmeticSlot slot, Material icon, int price) {
        return new CosmeticDefinition(id, slot, icon, "cosmetics.item." + id + ".name",
                "cosmetics.item." + id + ".description", true, false,
                CosmeticDefinition.Acquisition.COINS, price, null);
    }

    private static CosmeticDefinition reward(String id, CosmeticSlot slot, Material icon, String hintKey) {
        return new CosmeticDefinition(id, slot, icon, "cosmetics.item." + id + ".name",
                "cosmetics.item." + id + ".description", true, false,
                CosmeticDefinition.Acquisition.REWARD, 0, hintKey);
    }

    private static CosmeticDefinition definition(String id, CosmeticSlot slot, Material icon) {
        return definition(id, slot, icon, true);
    }

    private static CosmeticDefinition definition(
            String id, CosmeticSlot slot, Material icon, boolean selectionRequired) {
        return new CosmeticDefinition(id, slot, icon,
                "cosmetics.item." + id + ".name",
                "cosmetics.item." + id + ".description",
                selectionRequired, false);
    }
}
