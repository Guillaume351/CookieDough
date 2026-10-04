package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.lobby.HubGameMenuModel;
import com.cookiebuild.cookiedough.ui.BedrockFormSupport;
import com.cookiebuild.cookiedough.ui.PlatformText;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Gets every player to a cosmetic in their first minutes, without a single
 * extra prompt: the free trail is equipped once per account, a hotbar entry
 * opens the cosmetics menu in one tap, the post-match choice mentions a coin
 * cosmetic the player can already afford, and the ten-matches achievement
 * unlocks a reward trail. Nothing here changes gameplay.
 */
public final class CosmeticActivation implements Listener {
    /** Lobby hotbar slot of the "Cosmetics" emerald, next to the menu compass in slot 0. */
    public static final int HOTBAR_SLOT = 1;
    static final String TEN_MATCHES_ACHIEVEMENT = "ten_matches";
    static final String TEN_MATCHES_REWARD_KEY = "achievement:" + TEN_MATCHES_ACHIEVEMENT;
    static final String REPLAY_ANCHOR_ACTION = "replay:auto";
    private static final long WELCOME_RETRY_TICKS = 20L;
    /** The profile normally loads within a few seconds; stop waiting after 90 s. */
    private static final int WELCOME_MAX_ATTEMPTS = 90;
    private static final long OFFER_TTL_MILLIS = 120_000L;

    record Offer(String cosmeticId, int coins, long createdAtMillis) {
    }

    private final CookieDough plugin;
    private final CosmeticService service;
    private final CosmeticEffects effects;
    private final CosmeticsMenu menu;
    private final NamespacedKey hotbarKey;
    private final Set<UUID> welcomeChecked = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();
    private final AtomicBoolean giftStoreWarned = new AtomicBoolean();

    public CosmeticActivation(CookieDough plugin, CosmeticService service, CosmeticEffects effects,
            CosmeticsMenu menu) {
        this.plugin = plugin;
        this.service = service;
        this.effects = effects;
        this.menu = menu;
        this.hotbarKey = new NamespacedKey(plugin, "cosmetics_hotbar");
        effects.onTrailIntroduction(this::introduceWelcomeGift);
    }

    // ------------------------------------------------------------------ hotbar

    /** Called on every lobby arrival, right after the menu compass is given. */
    public void giveHotbarItem(Player player) {
        if (player == null || !player.isOnline()) return;
        ItemStack stack = new ItemStack(Material.EMERALD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(message(player, "cosmetics.hotbar.name"), NamedTextColor.GREEN));
        meta.lore(List.of(
                Component.text(message(player, "cosmetics.hotbar.lore"), NamedTextColor.GRAY),
                Component.text(PlatformText.message(player, "cosmetics.hotbar.action"), NamedTextColor.YELLOW)));
        meta.getPersistentDataContainer().set(hotbarKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        player.getInventory().setItem(HOTBAR_SLOT, stack);
    }

    boolean isHotbarItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(hotbarKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !isHotbarItem(event.getItem())) return;
        // A lobby game sign keeps its own behaviour even while holding the emerald.
        if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof Sign) return;
        event.setCancelled(true);
        menu.open(event.getPlayer(), "hotbar");
    }

    // ------------------------------------------------------------ welcome gift

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        welcomeChecked.remove(event.getPlayer().getUniqueId());
        scheduleWelcome(event.getPlayer(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        welcomeChecked.remove(playerId);
        offers.remove(playerId);
    }

    private void scheduleWelcome(Player player, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID playerId = player.getUniqueId();
            if (!player.isOnline() || welcomeChecked.contains(playerId)) return;
            // cosmetic rows reference playerdata: wait until the profile exists.
            if (!PlayerWrapperListener.isPlayerDataReady(playerId)) {
                if (attempt + 1 < WELCOME_MAX_ATTEMPTS) scheduleWelcome(player, attempt + 1);
                return;
            }
            welcomeChecked.add(playerId);
            welcome(player);
        }, WELCOME_RETRY_TICKS);
    }

    private void welcome(Player player) {
        UUID playerId = player.getUniqueId();
        String edition = BedrockFormSupport.isBedrock(player) ? "bedrock" : "java";
        boolean tenMatches = plugin.getGoalTracker() != null
                && plugin.getGoalTracker().hasAchievement(playerId, TEN_MATCHES_ACHIEVEMENT);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CosmeticService.WelcomeGiftResult gift = null;
            try {
                gift = service.claimWelcomeGift(playerId, edition);
            } catch (RuntimeException error) {
                // Typically the 0026 table is not deployed yet; retried next session.
                if (giftStoreWarned.compareAndSet(false, true)) {
                    plugin.getLogger().warning("Welcome cosmetic unavailable: " + rootMessage(error));
                }
            }
            boolean luckyGranted = tenMatches && grantTenMatchesRewardSafely(playerId);
            CosmeticService.WelcomeGiftResult result = gift;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (result == CosmeticService.WelcomeGiftResult.EQUIPPED) {
                    effects.introduceTrailWhenVisible(playerId);
                }
                if (result == CosmeticService.WelcomeGiftResult.EQUIPPED
                        || result == CosmeticService.WelcomeGiftResult.SLOT_TAKEN) {
                    FunnelTelemetry.record(player, FunnelTelemetry.Event.COSMETIC_GIFTED,
                            "cosmetic=" + CosmeticService.WELCOME_GIFT + " result="
                                    + result.name().toLowerCase(java.util.Locale.ROOT));
                }
                if (luckyGranted) announceReward(player, CosmeticCatalog.LUCKY_CLOVER_TRAIL);
            });
        });
    }

    /** First time the gifted trail is rendered: the player is walking in the lobby and can see it. */
    void introduceWelcomeGift(Player player) {
        player.sendMessage(Component.text(message(player, "cosmetics.gift.equipped"), NamedTextColor.GOLD));
        Component change = Component.text(PlatformText.message(player, "cosmetics.gift.change"),
                NamedTextColor.AQUA);
        if (!BedrockFormSupport.isBedrock(player)) change = change.clickEvent(ClickEvent.runCommand("/cosmetics"));
        player.sendMessage(change);
        Location location = player.getLocation();
        player.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 1.0, 0), 16,
                0.5, 0.6, 0.5, 0.02);
        player.playSound(location, "entity.player.levelup", 0.5f, 1.8f);
    }

    // -------------------------------------------------------- achievement reward

    /** Called with the reward keys that were credited for the first time (any thread). */
    public void onRewardsClaimed(UUID playerId, Collection<String> rewardKeys) {
        if (playerId == null || rewardKeys == null || !rewardKeys.contains(TEN_MATCHES_REWARD_KEY)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!grantTenMatchesRewardSafely(playerId)) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) announceReward(player, CosmeticCatalog.LUCKY_CLOVER_TRAIL);
            });
        });
    }

    /** Idempotent: true only when the reward was missing and is now granted. */
    private boolean grantTenMatchesRewardSafely(UUID playerId) {
        try {
            if (service.inventory(playerId).entitled(CosmeticCatalog.LUCKY_CLOVER_TRAIL)) return false;
            return service.grantReward(playerId, CosmeticCatalog.LUCKY_CLOVER_TRAIL, TEN_MATCHES_REWARD_KEY)
                    == CosmeticService.GrantResult.GRANTED;
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not grant the ten-matches cosmetic: " + rootMessage(error));
            return false;
        }
    }

    private void announceReward(Player player, String cosmeticId) {
        String name = CosmeticCatalog.find(cosmeticId).map(item -> message(player, item.nameKey())).orElse(cosmeticId);
        player.sendMessage(Component.text(message(player, "cosmetics.reward.unlocked", name), NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "entity.player.levelup", 0.6f, 1.4f);
    }

    // ------------------------------------------------------- post-match offer

    /** Reads coins and inventory off-thread so the post-match menu can mention an affordable cosmetic. */
    public void prefetchOffer(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Integer coins = CookieDough.createMinigameProgressionService().getCoins(playerId, null);
                Optional<CosmeticDefinition> best = coins == null ? Optional.empty()
                        : bestAffordable(service.inventory(playerId), coins);
                if (best.isPresent()) {
                    offers.put(playerId, new Offer(best.get().id(), coins, System.currentTimeMillis()));
                } else {
                    offers.remove(playerId);
                }
            } catch (RuntimeException error) {
                offers.remove(playerId);
            }
        });
    }

    /** "What next?" with one extra choice when a coin cosmetic is affordable. */
    public HubGameMenuModel withOffer(Player player, HubGameMenuModel model) {
        if (player == null || model == null) return model;
        Offer offer = offers.get(player.getUniqueId());
        if (offer == null || System.currentTimeMillis() - offer.createdAtMillis() > OFFER_TTL_MILLIS) return model;
        Optional<CosmeticDefinition> cosmetic = CosmeticCatalog.find(offer.cosmeticId());
        if (cosmetic.isEmpty()) return model;
        HubGameMenuModel.Entry entry = new HubGameMenuModel.Entry("shop", cosmetic.get().icon(), "actions/shop",
                message(player, "cosmetics.offer.label", message(player, cosmetic.get().nameKey())),
                message(player, "cosmetics.offer.detail", cosmetic.get().coinPrice(), offer.coins()));
        return new HubGameMenuModel(model.title(), model.content(), insertOffer(model.entries(), entry));
    }

    /** The most expensive coin cosmetic the player does not own and can pay for now. */
    static Optional<CosmeticDefinition> bestAffordable(CosmeticService.Inventory inventory, int coins) {
        return inventory.items().stream()
                .filter(item -> !item.entitled() && item.cosmetic().coinPurchasable()
                        && item.cosmetic().coinPrice() <= coins)
                .map(CosmeticService.InventoryItem::cosmetic)
                .max(Comparator.comparingInt(CosmeticDefinition::coinPrice));
    }

    /** Keeps "play again" first: the offer goes just before the auto-replay toggle. */
    static List<HubGameMenuModel.Entry> insertOffer(List<HubGameMenuModel.Entry> entries,
            HubGameMenuModel.Entry offer) {
        List<HubGameMenuModel.Entry> result = new ArrayList<>(entries);
        int anchor = -1;
        for (int index = 0; index < result.size(); index++) {
            if (REPLAY_ANCHOR_ACTION.equals(result.get(index).action())) {
                anchor = index;
                break;
            }
        }
        result.add(anchor < 0 ? Math.max(0, result.size() - 1) : anchor, offer);
        return List.copyOf(result);
    }

    private static String message(Player player, String key, Object... arguments) {
        return LocaleManager.getMessage(key, player.locale(), arguments);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
