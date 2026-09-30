package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.OnboardingCompletionPolicy;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.retention.FriendManager;
import com.cookiebuild.cookiedough.retention.PlayerGoalTracker;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockFormSupport;
import com.cookiebuild.cookiedough.ui.BedrockButtonText;
import com.cookiebuild.cookiedough.ui.MenuLore;
import com.cookiebuild.cookiedough.ui.PlatformText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** One discoverable lobby menu, rendered natively for Java and Bedrock players. */
public final class PlayerHubMenu implements Listener {
    static final int GAMES_INVENTORY_SIZE = 36;
    static final int GAMES_BACK_SLOT = 31;

    static List<Integer> gameMenuSlots() {
        return List.of(4, 9, 11, 13, 15, 17, 19, 21, 23, 25);
    }

    private final CookieDough plugin;
    private final LobbyManager lobby;
    private final PlayerGoalTracker goals;
    private final FriendManager friends;
    private final NamespacedKey actionKey;
    private final HubMenuSessionRegistry bedrockSessions = new HubMenuSessionRegistry();
    private final Map<UUID, String> queuedGames = new ConcurrentHashMap<>();
    private final Set<String> rulesShown = ConcurrentHashMap.newKeySet();
    private final Set<UUID> queueHelpShown = ConcurrentHashMap.newKeySet();
    private final Set<UUID> onboardingPlayers = ConcurrentHashMap.newKeySet();
    private final PostMatchReplayRegistry replays = new PostMatchReplayRegistry();
    private final Map<UUID, QueueWaitState> queueWaits = new ConcurrentHashMap<>();
    private final NamespacedKey autoReplayKey;

    /** Per-queue-session prompt memory so each escalation is shown at most once. */
    private static final class QueueWaitState {
        private volatile boolean optionsShown;
        private volatile boolean switchShown;
    }

    static final int REPLAY_ITEM_SLOT = 4;

    public PlayerHubMenu(CookieDough plugin, LobbyManager lobby, PlayerGoalTracker goals, FriendManager friends) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.goals = goals;
        this.friends = friends;
        this.actionKey = new NamespacedKey(plugin, "hub_menu_action");
        this.autoReplayKey = new NamespacedKey(plugin, "auto_requeue");
    }

    /** Identifies only our hub inventories; never exempts world containers. */
    public static boolean isMenuInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MenuHolder;
    }

    public void open(Player player) {
        if (queuedGames.containsKey(player.getUniqueId())) {
            openQueue(player);
            return;
        }
        if (onboardingPlayers.contains(player.getUniqueId())) {
            openOnboarding(player);
            return;
        }
        if (openBedrock(player, MenuPage.MAIN)) return;
        player.openInventory(mainInventory(player));
    }

    /** Cross-mode game selection without forcing a passive activity to close. */
    public void openGames(Player player) {
        if (player == null || !player.isOnline()) return;
        openPage(player, MenuPage.GAMES);
    }

    /** Shown until its first successful display; it deliberately fits on one page. */
    public boolean openOnboarding(Player player) {
        onboardingPlayers.add(player.getUniqueId());
        boolean matchReady = ModePopulationService.hasReadyMatchForOneMorePlayer();
        if (openBedrock(player, MenuPage.ONBOARDING)) {
            FunnelTelemetry.record(player, FunnelTelemetry.Event.ONBOARDING_SHOWN,
                    "surface=bedrock_form match_ready=" + matchReady);
            return true;
        }
        player.openInventory(onboardingInventory(player));
        FunnelTelemetry.record(player, FunnelTelemetry.Event.ONBOARDING_SHOWN,
                "surface=java_inventory match_ready=" + matchReady);
        return true;
    }

    /** Installs cross-edition queue controls after the game module prepares its waiting inventory. */
    public void enterQueue(Player player, String gameName) {
        if (player == null || gameName == null || gameName.isBlank()) return;
        queuedGames.put(player.getUniqueId(), gameName);
        bedrockSessions.invalidate(player.getUniqueId());
        queueHelpShown.remove(player.getUniqueId());
        queueWaits.put(player.getUniqueId(), new QueueWaitState());
        removeReplayItem(player);
        boolean onboardingPending = onboardingPlayers.remove(player.getUniqueId());
        if (onboardingPending) {
            PlayerWrapperListener.completeOnboarding(player, "admission:" + safeGameName(gameName));
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            if (player.isOnline() && cookiePlayer != null && cookiePlayer.getState() == PlayerState.QUEUED
                    && GameManager.getGameOfPlayer(cookiePlayer) != null) {
                String ruleKey = "game.rules." + gameName.toLowerCase(java.util.Locale.ROOT);
                if (rulesShown.add(player.getUniqueId() + ":"
                        + gameName.toLowerCase(java.util.Locale.ROOT))) {
                    player.sendMessage(Component.text(message(
                            player, "game.rules.header", readable(player, gameName)), NamedTextColor.GOLD)
                            .append(Component.text(" " + message(player, ruleKey), NamedTextColor.GRAY)));
                }
                player.sendMessage(Component.text(message(
                        player, "queue.joined", readable(player, gameName)), NamedTextColor.GREEN));
                ensureQueueControl(player);
            }
        });
    }

    /** Reinstalls the queue recovery item after a minigame preview clears inventory. */
    public void ensureQueueControl(Player player) {
        if (player == null || !player.isOnline() || !queuedGames.containsKey(player.getUniqueId())) return;
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || cookiePlayer.getState() != PlayerState.QUEUED
                || GameManager.getGameOfPlayer(cookiePlayer) == null) return;
        player.getInventory().setItem(8, item(Material.RECOVERY_COMPASS,
                message(player, "queue.controls.item"), "queue",
                message(player, "queue.controls.item_lore")));
    }

    /**
     * Called every second for each player waiting in a queue that cannot count
     * down yet. Escalates once per queue session: options at 20 s when alone,
     * then a one-tap consented switch at 45 s if another queue has players.
     */
    public void updateQueueWait(Player player, Game game, long waitingSeconds) {
        if (player == null || game == null || !player.isOnline()) return;
        if (waitingSeconds >= QueueWaitPolicy.APP_PROMOTION_AFTER_SECONDS) {
            PlayerWrapperListener.offerDeferredAppPromotion(player, "queue_wait");
        }
        QueueWaitState state = queueWaits.computeIfAbsent(player.getUniqueId(), ignored -> new QueueWaitState());
        boolean alone = game.getPlayerCount() == 1;
        Game other = QueueWaitPolicy.needsSwitchCandidate(waitingSeconds, alone, state.optionsShown,
                state.switchShown) ? GameManager.findBusiestOtherQueue(game) : null;
        switch (QueueWaitPolicy.next(waitingSeconds, alone, state.optionsShown, state.switchShown, other != null)) {
            case HINT -> {
                state.optionsShown = true;
                sendQueueHint(player, game.getGameName());
            }
            case OPTIONS -> {
                state.optionsShown = true;
                FunnelTelemetry.record(player, FunnelTelemetry.Event.QUEUE_HELP_OPENED,
                        "game=" + safeGameName(game.getGameName()) + " trigger=wait_20s");
                openPage(player, MenuPage.WAITING, game.getGameName());
            }
            case SWITCH_OFFER -> {
                state.switchShown = true;
                FunnelTelemetry.record(player, FunnelTelemetry.Event.QUEUE_HELP_OPENED,
                        "game=" + safeGameName(game.getGameName()) + " trigger=switch_offer_45s"
                                + " target=" + safeGameName(other.getGameName()));
                openPage(player, MenuPage.SWITCH, game.getGameName() + "|" + other.getGameName());
            }
            case NONE -> { }
        }
    }

    /** Secondary "play while you wait" choice right after Quick Play joined a queue that still needs players. */
    public void offerWhileWaiting(Player player, String gameName) {
        if (player == null || gameName == null) return;
        QueueWaitState state = queueWaits.computeIfAbsent(player.getUniqueId(), ignored -> new QueueWaitState());
        state.optionsShown = true;
        // Let the queue's own rules/confirmation messages land first.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (queuedInGame(player, gameName)) openPage(player, MenuPage.WAITING, gameName);
        }, 10L);
    }

    private void sendQueueHint(Player player, String gameName) {
        if (!queueHelpShown.add(player.getUniqueId())) return;
        Component hint = Component.text(PlatformText.message(player, "queue.help", readable(player, gameName)),
                NamedTextColor.YELLOW);
        if (!BedrockFormSupport.isBedrock(player)) {
            hint = hint.append(Component.text(" [" + message(player, "queue.help.action") + "]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/menu")));
        }
        player.sendMessage(hint);
    }

    private boolean queuedInGame(Player player, String gameName) {
        if (player == null || !player.isOnline() || gameName == null) return false;
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
        return game != null && cookiePlayer.getState() == PlayerState.QUEUED
                && game.getGameName().equalsIgnoreCase(gameName);
    }

    public void leaveQueue(Player player) {
        if (player == null) return;
        queuedGames.remove(player.getUniqueId());
        bedrockSessions.invalidate(player.getUniqueId());
        queueHelpShown.remove(player.getUniqueId());
        queueWaits.remove(player.getUniqueId());
        ItemStack item = player.getInventory().getItem(8);
        if (hasAction(item, "queue")) player.getInventory().setItem(8, null);
    }

    public void enterSpectator(Player player) {
        if (player == null || !player.isOnline()) return;
        player.getInventory().setItem(7, item(Material.COMPASS,
                message(player, "spectator.controls.item"), "spectator_games",
                message(player, "spectator.controls.item_lore")));
    }

    public void leaveSpectator(Player player) {
        if (player == null) return;
        ItemStack item = player.getInventory().getItem(7);
        if (hasAction(item, "spectator_games")) player.getInventory().setItem(7, null);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        queuedGames.remove(playerId);
        bedrockSessions.invalidate(playerId);
        queueHelpShown.remove(playerId);
        rulesShown.removeIf(value -> value.startsWith(playerId + ":"));
        onboardingPlayers.remove(playerId);
        queueWaits.remove(playerId);
        replays.clear(playerId);
    }

    public void openQueue(Player player) {
        String gameName = queuedGames.get(player.getUniqueId());
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
        if (game == null || cookiePlayer.getState() != PlayerState.QUEUED) {
            leaveQueue(player);
            open(player);
            return;
        }
        gameName = game.getGameName();
        FunnelTelemetry.record(player, FunnelTelemetry.Event.QUEUE_HELP_OPENED, "game=" + gameName);
        if (openBedrock(player, MenuPage.QUEUE, gameName)) return;
        player.openInventory(queueInventory(player, gameName));
    }

    public void openReplay(Player player, String gameName) {
        if (player == null || gameName == null || gameName.isBlank() || !player.isOnline()) return;
        openPage(player, MenuPage.REPLAY, gameName);
    }

    /** Remembers a completed match; the choice itself waits for the lobby arrival. */
    public void recordMatchCompleted(Player player, String gameName) {
        if (player == null) return;
        replays.recordCompletion(player.getUniqueId(), gameName, System.currentTimeMillis());
    }

    /**
     * Lobby-arrival hook. Presents "What next?" 1.5 s after the player is really
     * back in the lobby, and only if they are still idle there by then.
     */
    public void onLobbyArrival(Player player, String finishedGameName) {
        if (player == null) return;
        String gameName = replays.consumeArrival(player.getUniqueId(), finishedGameName,
                System.currentTimeMillis()).orElse(null);
        if (gameName == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> presentPostMatch(player, gameName),
                PostMatchReplayRegistry.OFFER_DELAY_TICKS);
    }

    private void presentPostMatch(Player player, String gameName) {
        CookiePlayer cookiePlayer = player == null ? null : PlayerManager.getPlayer(player);
        if (cookiePlayer == null) return;
        org.bukkit.World lobbyWorld = Bukkit.getWorld("lobby");
        boolean eligible = PostMatchReplayRegistry.shouldOffer(new PostMatchReplayRegistry.OfferContext(
                player.isOnline(), PlayerWrapperListener.isPlayerDataReady(player.getUniqueId()),
                lobbyWorld != null && lobbyWorld.equals(player.getWorld()), cookiePlayer.getState(),
                GameManager.getGameOfPlayer(cookiePlayer) != null,
                GameManager.getQueueIntent(player.getUniqueId()) != null,
                com.cookiebuild.cookiedough.activity.ActivityRegistry.owner(player.getUniqueId()) != null));
        if (!eligible) return;
        PlayerWrapperListener.offerDeferredAppPromotion(player, "match_completed");
        if (isAutoReplayEnabled(player)) {
            FunnelTelemetry.record(player, FunnelTelemetry.Event.REMATCH_CLICKED,
                    "choice=auto game=" + safeGameName(gameName));
            player.sendMessage(Component.text(message(player, "replay.auto.requeued", readable(player, gameName)),
                    NamedTextColor.GREEN));
            lobby.requestSelectedActivity(player, gameName);
            return;
        }
        giveReplayItem(player, gameName);
        openReplay(player, gameName);
    }

    /** {@code /play replay}: the last completed mode of this session, else Quick Play. */
    public void replayLast(Player player) {
        if (player == null) return;
        String gameName = replays.lastCompleted(player.getUniqueId()).orElse(null);
        removeReplayItem(player);
        if (gameName == null || GamePresentation.find(gameName).isEmpty()) {
            lobby.requestSelectedQuickPlay(player);
        } else {
            lobby.requestSelectedActivity(player, gameName);
        }
    }

    /** Opt-in automatic re-queue after a match, persisted in the player's Paper data. */
    public boolean toggleAutoReplay(Player player) {
        boolean enabled = !isAutoReplayEnabled(player);
        if (enabled) {
            player.getPersistentDataContainer().set(autoReplayKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(autoReplayKey);
        }
        player.sendMessage(Component.text(message(player, enabled ? "replay.auto.enabled" : "replay.auto.disabled"),
                NamedTextColor.YELLOW));
        return enabled;
    }

    public boolean isAutoReplayEnabled(Player player) {
        return player != null && player.getPersistentDataContainer().has(autoReplayKey, PersistentDataType.BYTE);
    }

    private void giveReplayItem(Player player, String gameName) {
        if (BedrockFormSupport.isBedrock(player)) return;
        player.getInventory().setItem(REPLAY_ITEM_SLOT, item(Material.LIME_DYE,
                message(player, "replay.menu.same", readable(player, gameName)), "replay_item",
                message(player, "replay.item.lore")));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) removeReplayItem(player);
        }, PostMatchReplayRegistry.REPLAY_ITEM_TICKS);
    }

    private void removeReplayItem(Player player) {
        if (player == null) return;
        ItemStack item = player.getInventory().getItem(REPLAY_ITEM_SLOT);
        if (hasAction(item, "replay_item")) player.getInventory().setItem(REPLAY_ITEM_SLOT, null);
    }

    private Inventory mainInventory(Player player) {
        MenuHolder holder = new MenuHolder(MenuPage.MAIN, 27,
                Component.text(message(player, "hub.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(10, item(Material.NETHER_STAR, message(player, "hub.quick.name"), "quick",
                message(player, "hub.quick.lore")));
        inventory.setItem(12, item(Material.GRASS_BLOCK, message(player, "hub.games.name"), "games",
                message(player, "hub.games.lore")));
        inventory.setItem(14, item(Material.EXPERIENCE_BOTTLE, message(player, "hub.goals.name"), "goals",
                message(player, "hub.goals.lore")));
        inventory.setItem(16, item(Material.PLAYER_HEAD, message(player, "hub.friends.name"), "friends",
                message(player, "hub.friends.lore")));
        inventory.setItem(19, item(Material.COOKIE, message(player, "hub.party.name"), "party",
                message(player, "hub.party.lore")));
        inventory.setItem(21, item(Material.CLOCK, message(player, "hub.events.name"), "events",
                message(player, "hub.events.lore")));
        inventory.setItem(23, item(Material.ENDER_EYE, message(player, "hub.app.name"), "app",
                message(player, "hub.app.lore")));
        inventory.setItem(25, item(Material.BOOK, message(player, "hub.help.name"), "help",
                message(player, "hub.help.lore")));
        return inventory;
    }

    private Inventory onboardingInventory(Player player) {
        MenuHolder holder = new MenuHolder(MenuPage.ONBOARDING, 27,
                Component.text(message(player, "hub.onboarding.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        OnboardingPrimaryButton primary = onboardingPrimaryButton(onboardingMatchReady());
        inventory.setItem(11, item(primary.material(), message(player, primary.nameKey(), featuredName(player)),
                primary.action(), message(player, primary.loreKey())));
        inventory.setItem(13, item(Material.GRASS_BLOCK, message(player, "hub.games.name"), "games",
                message(player, "hub.onboarding.games_lore")));
        inventory.setItem(15, item(Material.ENDER_EYE, message(player, "hub.onboarding.community_name"), "community",
                message(player, "hub.onboarding.community_lore_discord"),
                message(player, "hub.onboarding.community_lore_app")));
        inventory.setItem(22, item(Material.COMPASS, message(player, "hub.onboarding.full_name"), "back",
                message(player, "hub.onboarding.full_lore")));
        return inventory;
    }

    private Inventory gamesInventory(Player player) {
        HubGameMenuModel model = HubGameMenuModel.index(player.locale());
        MenuHolder holder = new MenuHolder(MenuPage.GAMES, GAMES_INVENTORY_SIZE,
                Component.text(model.title(), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        List<Integer> slots = gameMenuSlots();
        for (int index = 0; index < model.entries().size(); index++) {
            HubGameMenuModel.Entry entry = model.entries().get(index);
            inventory.setItem(slots.get(index), item(entry.icon(), entry.label(), entry.action(), entry.detail()));
        }
        inventory.setItem(GAMES_BACK_SLOT, item(Material.ARROW, message(player, "hub.back"), "back",
                message(player, "hub.back_lore")));
        return inventory;
    }

    /** Renders a funnel model: summary on top, choices centred on the middle row. */
    private Inventory modelInventory(MenuPage page, String context, HubGameMenuModel model) {
        MenuHolder holder = new MenuHolder(page, context, 27, Component.text(model.title(), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(4, item(Material.COOKIE, model.title(), "noop", model.content().split("\\n")));
        List<Integer> slots = centredSlots(model.entries().size());
        for (int index = 0; index < slots.size(); index++) {
            HubGameMenuModel.Entry entry = model.entries().get(index);
            inventory.setItem(slots.get(index), item(entry.icon(), entry.label(), entry.action(), entry.detail()));
        }
        return inventory;
    }

    static List<Integer> centredSlots(int count) {
        return switch (Math.max(0, Math.min(count, 7))) {
            case 0 -> List.of();
            case 1 -> List.of(13);
            case 2 -> List.of(11, 15);
            case 3 -> List.of(11, 13, 15);
            case 4 -> List.of(10, 12, 14, 16);
            case 5 -> List.of(9, 11, 13, 15, 17);
            case 6 -> List.of(10, 11, 12, 14, 15, 16);
            default -> List.of(10, 11, 12, 13, 14, 15, 16);
        };
    }

    private HubGameMenuModel funnelModel(Player player, MenuPage page, String context) {
        java.util.Locale locale = player.locale();
        return switch (page) {
            case REPLAY -> FunnelMenuModels.replay(context, locale, busiestQueueChoice(null, context),
                    isAutoReplayEnabled(player));
            case WAITING -> {
                CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
                Game current = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
                yield FunnelMenuModels.waiting(context, current == null ? 2 : current.getMinimumPlayers(), locale,
                        busiestQueueChoice(current, context),
                        ModePopulationService.isPersistentActivityAvailable("Skyblock"));
            }
            case SWITCH -> {
                String[] parts = context.split("\\|", 2);
                Game target = GameManager.getOpenGameByName(parts.length > 1 ? parts[1] : "");
                int players = target == null ? 0 : target.getPlayerCount()
                        + GameManager.getAdmittableQueueIntentCount(target);
                yield FunnelMenuModels.switchOffer(parts[0], locale,
                        new FunnelMenuModels.QueueChoice(parts.length > 1 ? parts[1] : parts[0], players));
            }
            case COMMUNITY -> FunnelMenuModels.community(locale);
            default -> throw new IllegalArgumentException("Not a funnel page: " + page);
        };
    }

    private FunnelMenuModels.QueueChoice busiestQueueChoice(Game current, String excludedGameName) {
        Game other = current != null ? GameManager.findBusiestOtherQueue(current)
                : com.cookiebuild.cookiedough.game.GameSelectionPolicy.busiestOtherQueue(GameManager.getGames(),
                        excludedGameName, GameManager::getAdmittableQueueIntentCount);
        return other == null ? null : new FunnelMenuModels.QueueChoice(other.getGameName(),
                other.getPlayerCount() + GameManager.getAdmittableQueueIntentCount(other));
    }

    private Inventory gameDetailInventory(Player player, String gameName) {
        HubGameMenuModel model = HubGameMenuModel.detail(gameName, player.locale());
        MenuHolder holder = new MenuHolder(MenuPage.GAME_DETAIL, gameName, 27,
                Component.text(model.title(), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        GamePresentation game = GamePresentation.find(gameName).orElseThrow();
        inventory.setItem(4, item(game.icon(), model.title(), "noop", model.content().split("\\n")));
        int[] slots = model.entries().size() == 4 ? new int[] { 10, 12, 14, 16 } : new int[] { 11, 15, 22 };
        for (int index = 0; index < model.entries().size(); index++) {
            HubGameMenuModel.Entry entry = model.entries().get(index);
            inventory.setItem(slots[index], item(entry.icon(), entry.label(), entry.action(), entry.detail()));
        }
        return inventory;
    }

    private Inventory goalsInventory(Player player) {
        PlayerGoalTracker.GoalView view = goals.view(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuPage.GOALS, 27,
                Component.text(message(player, "hub.goals.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(11, item(view.dailyComplete() ? Material.LIME_DYE : Material.SUNFLOWER,
                message(player, "hub.goals.daily"), "noop",
                progress(message(player, "hub.goals.play_match"), view.dailyMatches(), 1),
                progress(message(player, "hub.goals.win_match"), view.dailyWins(), 1),
                message(player, "hub.goals.daily_reset")));
        inventory.setItem(13, item(view.weeklyComplete() ? Material.LIME_DYE : Material.DIAMOND,
                message(player, "hub.goals.weekly"), "noop",
                progress(message(player, "hub.goals.play_matches"), view.weeklyMatches(), 3),
                progress(message(player, "hub.goals.win_matches"), view.weeklyWins(), 1),
                progress(message(player, "hub.goals.finish_matches"), view.weeklyFinished(), 5),
                message(player, "hub.goals.weekly_reset")));
        inventory.setItem(15, item(Material.ENCHANTED_BOOK, message(player, "hub.goals.achievements"), "noop",
                message(player, "hub.goals.unlocked", view.achievements()),
                message(player, "hub.goals.achievements_lore")));
        inventory.setItem(22, item(Material.ARROW, message(player, "hub.back"), "back",
                message(player, "hub.back_lore")));
        return inventory;
    }

    private Inventory queueInventory(Player player, String gameName) {
        MenuHolder holder = new MenuHolder(MenuPage.QUEUE, gameName, 27,
                Component.text(message(player, "queue.menu.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(4, item(Material.CLOCK, message(player, "queue.menu.status", readable(player, gameName)),
                "noop", message(player, "queue.menu.status_hint")));
        if (ModePopulationService.isPersistentActivityAvailable("Skyblock")) {
            inventory.setItem(22, item(Material.GRASS_BLOCK, message(player, "queue.wait.skyblock"),
                    "queue:activity:Skyblock", message(player, "queue.wait.skyblock_hint",
                            readable(player, gameName))));
        }
        inventory.setItem(10, item(Material.BARRIER, message(player, "queue.menu.leave"), "queue:leave",
                message(player, "queue.menu.leave_hint")));
        inventory.setItem(12, item(Material.COMPASS, message(player, "queue.menu.switch"), "queue:switch",
                message(player, "queue.menu.switch_hint")));
        inventory.setItem(14, item(Material.BLAZE_ROD, message(player, "queue.menu.practice"), "queue:practice",
                message(player, "queue.menu.practice_hint")));
        inventory.setItem(16, item(Material.FIREWORK_ROCKET, message(player, "queue.menu.rally"), "queue:rally",
                message(player, "queue.menu.rally_hint")));
        return inventory;
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action != null) dispatch(player, action, holder.page(), holder.context());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || (!hasAction(event.getItem(), "queue")
                        && !hasAction(event.getItem(), "spectator_games")
                        && !hasAction(event.getItem(), "replay_item"))) {
            return;
        }
        event.setCancelled(true);
        if (hasAction(event.getItem(), "queue")) openQueue(event.getPlayer());
        else if (hasAction(event.getItem(), "replay_item")) {
            String gameName = replays.lastCompleted(event.getPlayer().getUniqueId()).orElse(null);
            if (gameName == null) removeReplayItem(event.getPlayer());
            else dispatchReplay(event.getPlayer(), "same", gameName);
        }
        else openGames(event.getPlayer());
    }

    private void dispatch(Player player, String action, MenuPage source, String context) {
        if (source == MenuPage.ONBOARDING && OnboardingCompletionPolicy.completes(action)
                && onboardingPlayers.remove(player.getUniqueId())) {
            PlayerWrapperListener.completeOnboarding(player, action);
        }
        if (action.startsWith("game:details:")) {
            String gameName = action.substring("game:details:".length());
            if (GamePresentation.find(gameName).isPresent()) openPage(player, MenuPage.GAME_DETAIL, gameName);
            return;
        }
        if (action.startsWith("game:join:")) {
            String gameName = action.substring("game:join:".length());
            if (GamePresentation.find(gameName).isEmpty()) return;
            player.closeInventory();
            lobby.requestSelectedActivity(player, gameName);
            return;
        }
        if (action.startsWith("game:spectate:")) {
            String gameName = action.substring("game:spectate:".length());
            if (GamePresentation.find(gameName).isEmpty()) return;
            player.closeInventory();
            lobby.requestSpectate(player, gameName);
            return;
        }
        if (action.startsWith("queue:")) {
            dispatchQueue(player, action.substring("queue:".length()), context);
            return;
        }
        if (action.startsWith("replay:")) {
            dispatchReplay(player, action.substring("replay:".length()), context);
            return;
        }
        if (action.startsWith("wait:")) {
            dispatchWait(player, action.substring("wait:".length()), context);
            return;
        }
        switch (action) {
            case "quick" -> {
                player.closeInventory();
                lobby.requestSelectedQuickPlay(player);
            }
            case "games" -> openPage(player, MenuPage.GAMES);
            case "goals" -> openPage(player, MenuPage.GOALS);
            case "friends" -> {
                player.closeInventory();
                friends.describe(player, message -> player.sendMessage(ChatColor.YELLOW + message));
            }
            case "party" -> run(player, "party list");
            case "events" -> run(player, "events");
            case "app" -> run(player, "app status");
            case "community" -> showCommunityLinks(player);
            case "onboarding" -> openOnboarding(player);
            case "help" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + message(player, "hub.help.commands") + " " + ChatColor.YELLOW
                        + "/menu, /quickplay, /practice, /friend, /party, /mute, /block, /report, /rules, /lobby");
            }
            case "back" -> openPage(player, MenuPage.MAIN);
            case "close" -> player.closeInventory();
            default -> {
                // Decorative goal entries deliberately have no action.
            }
        }
    }

    private void dispatchQueue(Player player, String action, String gameName) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) return;
        switch (action) {
            case "leave" -> {
                player.closeInventory();
                LobbyManager.teleportPlayerToLobby(cookiePlayer);
            }
            case "switch" -> {
                player.closeInventory();
                LobbyManager.teleportPlayerToLobby(cookiePlayer);
                Bukkit.getScheduler().runTask(plugin, () -> openPage(player, MenuPage.GAMES));
            }
            case "practice" -> {
                player.closeInventory();
                plugin.getPracticeManager().toggle(player);
            }
            case "rally" -> {
                player.closeInventory();
                player.performCommand("rally " + safeGameName(gameName));
            }
            case "activity:Skyblock" -> {
                player.closeInventory();
                lobby.playActivityWhileQueued(player, "Skyblock", gameName);
            }
            default -> { }
        }
    }

    private void dispatchWait(Player player, String action, String context) {
        String gameName = context == null ? null : context.split("\\|", 2)[0];
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || gameName == null) return;
        player.closeInventory();
        if (action.startsWith("switch:")) {
            String target = action.substring("switch:".length());
            if (GamePresentation.find(target).isEmpty() || !queuedInGame(player, gameName)) return;
            FunnelTelemetry.record(player, FunnelTelemetry.Event.SELECTOR_OPENED, "selector=queue_switch from="
                    + safeGameName(gameName) + " to=" + safeGameName(target));
            if (!LobbyManager.teleportPlayerToLobby(cookiePlayer)) return;
            Bukkit.getScheduler().runTask(plugin, () -> lobby.requestSelectedActivity(player, target));
            return;
        }
        if (action.startsWith("activity:")) {
            String activity = action.substring("activity:".length());
            if (GamePresentation.find(activity).filter(GamePresentation::persistent).isEmpty()) return;
            lobby.playActivityWhileQueued(player, activity, gameName);
            return;
        }
        switch (action) {
            case "stay" -> player.sendMessage(Component.text(message(player, "queue.wait.kept",
                    readable(player, gameName)), NamedTextColor.GREEN));
            case "practice" -> plugin.getPracticeManager().toggle(player);
            case "rally" -> player.performCommand("rally " + safeGameName(gameName));
            default -> { }
        }
    }

    private void dispatchReplay(Player player, String action, String gameName) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) return;
        FunnelTelemetry.record(player, FunnelTelemetry.Event.REMATCH_CLICKED,
                "choice=" + action + " game=" + safeGameName(gameName));
        player.closeInventory();
        if (cookiePlayer.getState() != PlayerState.LOBBY || GameManager.getGameOfPlayer(cookiePlayer) != null) {
            if (!LobbyManager.teleportPlayerToLobby(cookiePlayer)) return;
        }
        if ("auto".equals(action)) {
            toggleAutoReplay(player);
            Bukkit.getScheduler().runTask(plugin, () -> openReplay(player, gameName));
            return;
        }
        removeReplayItem(player);
        if (action.startsWith("join:")) {
            String target = action.substring("join:".length());
            if (GamePresentation.find(target).isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () -> lobby.requestSelectedActivity(player, target));
            }
            return;
        }
        switch (action) {
            case "same" -> Bukkit.getScheduler().runTask(plugin,
                    () -> lobby.requestSelectedActivity(player, gameName));
            case "quick" -> Bukkit.getScheduler().runTask(plugin,
                    () -> lobby.requestSelectedQuickPlay(player));
            case "feedback" -> player.sendMessage(Component.text(message(player, "feedback.command.usage"),
                    NamedTextColor.AQUA));
            case "lobby" -> { }
            default -> { }
        }
    }

    private void run(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    /**
     * Shows the invite as readable text for everyone (Bedrock cannot click chat
     * links) and, on Bedrock, in a form that leads back to onboarding. This
     * choice deliberately does not complete onboarding.
     */
    private void showCommunityLinks(Player player) {
        player.closeInventory();
        player.sendMessage(Component.text(message(player, "hub.community.prefix") + " ", NamedTextColor.GOLD)
                .append(Component.text(message(player, "hub.community.discord") + " ", NamedTextColor.AQUA))
                .append(Component.text(FunnelMenuModels.DISCORD_INVITE, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl("https://" + FunnelMenuModels.DISCORD_INVITE)))
                .append(Component.text(" " + message(player, "hub.community.or") + " ", NamedTextColor.GRAY))
                .append(Component.text(message(player, "hub.community.app") + " ", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(FunnelMenuModels.APP_URL, NamedTextColor.LIGHT_PURPLE)
                        .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com/#mobile-app")))
                .append(Component.text(" " + message(player, "hub.community.suffix"), NamedTextColor.GRAY)));
        if (BedrockFormSupport.isBedrock(player)) {
            openBedrock(player, MenuPage.COMMUNITY, null);
        }
    }

    private void openPage(Player player, MenuPage page) {
        openPage(player, page, null);
    }

    private void openPage(Player player, MenuPage page, String context) {
        if (openBedrock(player, page, context)) return;
        player.openInventory(switch (page) {
            case MAIN -> mainInventory(player);
            case ONBOARDING -> onboardingInventory(player);
            case GAMES -> gamesInventory(player);
            case GAME_DETAIL -> gameDetailInventory(player, context);
            case GOALS -> goalsInventory(player);
            case REPLAY, WAITING, SWITCH, COMMUNITY -> modelInventory(page, context, funnelModel(player, page, context));
            case QUEUE -> queueInventory(player, context);
        });
    }

    private boolean openBedrock(Player player, MenuPage page) {
        return openBedrock(player, page, null);
    }

    private boolean openBedrock(Player player, MenuPage page, String context) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            FloodgatePlayer floodgate = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgate == null) return false;
            String scope = page.name() + ":" + (context == null ? "" : context);
            UUID nonce = bedrockSessions.issue(player.getUniqueId(), scope);
            PlayerGoalTracker.GoalView view = goals.view(player.getUniqueId());
            SimpleForm.Builder builder = SimpleForm.builder();
            List<String> actions = new ArrayList<>();
            switch (page) {
                case ONBOARDING -> {
                    builder.title("§l§6" + message(player, "hub.onboarding.title"))
                            .content(message(player, "hub.onboarding.content"));
                    OnboardingPrimaryButton primary = onboardingPrimaryButton(onboardingMatchReady());
                    String primaryLabel = BedrockButtonText.format(
                            message(player, primary.nameKey(), featuredName(player)),
                            message(player, primary.loreKey()));
                    if (primary.texture() != null) {
                        button(builder, actions, primaryLabel, primary.action(), primary.texture());
                    } else {
                        hubButton(builder, actions, primaryLabel, primary.action());
                    }
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.games.name"),
                            message(player, "hub.onboarding.games_lore")), "games");
                    hubButton(builder, actions, BedrockButtonText.format(
                            message(player, "hub.onboarding.community_name"),
                            message(player, "hub.onboarding.community_lore_app")), "community");
                    hubButton(builder, actions,
                            BedrockButtonText.format(message(player, "hub.onboarding.full_name")), "back");
                }
                case MAIN -> {
                    builder.title("§l§6" + message(player, "hub.title"))
                            .content(message(player, "hub.content"));
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.quick.name"),
                            message(player, "hub.quick.lore")), "quick");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.games.name"),
                            message(player, "hub.games.lore")), "games");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.goals.name"),
                            message(player, "hub.goals.lore")), "goals");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.friends.name"),
                            message(player, "hub.friends.lore")), "friends");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.party.name"),
                            message(player, "hub.party.lore")), "party");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.events.name"),
                            message(player, "hub.events.lore")), "events");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.app.name"),
                            message(player, "hub.app.lore")), "app");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.help.name"),
                            message(player, "hub.help.lore")), "help");
                }
                case GAMES -> {
                    HubGameMenuModel model = HubGameMenuModel.index(player.locale());
                    builder.title("§l§6" + model.title()).content(model.content());
                    for (HubGameMenuModel.Entry entry : model.entries()) {
                        button(builder, actions, BedrockButtonText.format(entry.label(), entry.detail()),
                                entry.action(), entry.bedrockTexture());
                    }
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.back")), "back");
                }
                case GAME_DETAIL -> {
                    HubGameMenuModel model = HubGameMenuModel.detail(context, player.locale());
                    builder.title("§l§6" + model.title()).content(model.content());
                    for (HubGameMenuModel.Entry entry : model.entries()) {
                        button(builder, actions, BedrockButtonText.format(entry.label(), entry.detail()),
                                entry.action(), entry.bedrockTexture());
                    }
                }
                case GOALS -> {
                    builder.title("§l§6" + message(player, "hub.goals.title"))
                            .content("§e" + message(player, "hub.goals.daily") + "\n§f"
                                    + message(player, "hub.goals.bedrock_daily", view.dailyMatches(), view.dailyWins())
                                    + "\n\n§b" + message(player, "hub.goals.weekly") + "\n§f"
                                    + message(player, "hub.goals.bedrock_weekly", view.weeklyMatches(),
                                            view.weeklyWins(), view.weeklyFinished())
                                    + "\n\n§d" + message(player, "hub.goals.bedrock_achievements", view.achievements()));
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "hub.back")), "back");
                }
                case QUEUE -> {
                    builder.title("§l§6" + message(player, "queue.menu.title"))
                            .content(message(player, "queue.menu.status", readable(player, context)) + "\n"
                                    + message(player, "queue.menu.status_hint"));
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "queue.menu.leave"),
                            message(player, "queue.menu.leave_hint")), "queue:leave");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "queue.menu.switch"),
                            message(player, "queue.menu.switch_hint")), "queue:switch");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "queue.menu.practice"),
                            message(player, "queue.menu.practice_hint")), "queue:practice");
                    hubButton(builder, actions, BedrockButtonText.format(message(player, "queue.menu.rally"),
                            message(player, "queue.menu.rally_hint")), "queue:rally");
                    if (ModePopulationService.isPersistentActivityAvailable("Skyblock")) {
                        button(builder, actions, BedrockButtonText.format(message(player, "queue.wait.skyblock"),
                                message(player, "queue.wait.skyblock_hint", readable(player, context))),
                                "queue:activity:Skyblock", "modes/skyblock");
                    }
                }
                case REPLAY, WAITING, SWITCH, COMMUNITY -> {
                    HubGameMenuModel model = funnelModel(player, page, context);
                    builder.title("§l§6" + model.title()).content(model.content());
                    for (HubGameMenuModel.Entry entry : model.entries()) {
                        button(builder, actions, BedrockButtonText.format(entry.label(), entry.detail()),
                                entry.action(), entry.bedrockTexture());
                    }
                }
            }
            builder.validResultHandler(response -> {
                int index = response.clickedButtonId();
                if (index >= 0 && index < actions.size()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && bedrockSessions.consume(player.getUniqueId(), nonce, scope)
                                && bedrockResponseStillValid(player, page, context)) {
                            dispatch(player, actions.get(index), page, context);
                        }
                    });
                }
            });
            builder.closedOrInvalidResultHandler(() ->
                    bedrockSessions.invalidate(player.getUniqueId(), nonce, scope));
            floodgate.sendForm(builder.build());
            return true;
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Could not open Bedrock lobby menu for " + player.getName() + ": "
                    + error.getMessage());
            return false;
        }
    }

    private boolean bedrockResponseStillValid(Player player, MenuPage page, String context) {
        if (page == MenuPage.QUEUE) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            Game game = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
            return HubBedrockResponsePolicy.queueValid(context, queuedGames.get(player.getUniqueId()),
                    cookiePlayer != null && cookiePlayer.getState() == PlayerState.QUEUED,
                    game == null ? null : game.getGameName());
        }
        if (page == MenuPage.WAITING || page == MenuPage.SWITCH) {
            String gameName = context == null ? null : context.split("\\|", 2)[0];
            return queuedInGame(player, gameName);
        }
        if (page == MenuPage.REPLAY) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            return HubBedrockResponsePolicy.replayValid(
                    cookiePlayer != null && cookiePlayer.getState() == PlayerState.LOBBY,
                    cookiePlayer != null && GameManager.getGameOfPlayer(cookiePlayer) != null);
        }
        return true;
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action) {
        BedrockFormImages.button(builder, label, null);
        actions.add(action);
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action,
            String texturePath) {
        BedrockFormImages.button(builder, label, texturePath);
        actions.add(action);
    }

    private static void hubButton(SimpleForm.Builder builder, List<String> actions, String label, String action) {
        HubActionImages.texture(action).ifPresentOrElse(
                texture -> button(builder, actions, label, action, texture),
                () -> button(builder, actions, label, action));
    }

    private ItemStack item(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(java.util.Arrays.stream(lore).map(MenuLore::detail).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasAction(ItemStack item, String expected) {
        if (item == null || !item.hasItemMeta()) return false;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        return expected.equals(action);
    }

    private static String message(Player player, String key, Object... arguments) {
        return LocaleManager.getMessage(key, player.locale(), arguments);
    }

    private static String safeGameName(String gameName) {
        return gameName == null ? "" : gameName.replaceAll("[^A-Za-z0-9_-]", "");
    }

    static boolean onboardingMatchReady() {
        return ModePopulationService.hasReadyMatchForOneMorePlayer();
    }

    /**
     * The primary onboarding action is always Quick Play: it joins a ready
     * match, else the featured Build Battle queue so the next arrival starts
     * the match. Skyblock (beta, solo) is never the default for a new player.
     */
    static OnboardingPrimaryButton onboardingPrimaryButton(boolean matchReady) {
        return matchReady
                ? new OnboardingPrimaryButton("quick", Material.NETHER_STAR,
                        "hub.quick.name", "hub.quick.lore", null)
                : new OnboardingPrimaryButton("quick", Material.CRAFTING_TABLE,
                        "hub.onboarding.featured_name", "hub.onboarding.featured_lore",
                        GamePresentation.forGame(com.cookiebuild.cookiedough.game.GameSelectionPolicy.FEATURED_GAME)
                                .bedrockTexture());
    }

    private static String featuredName(Player player) {
        return GamePresentation.readableName(com.cookiebuild.cookiedough.game.GameSelectionPolicy.FEATURED_GAME,
                player.locale());
    }

    private static String readable(Player player, String gameName) {
        return GamePresentation.readableName(gameName, player.locale());
    }

    record OnboardingPrimaryButton(String action, Material material, String nameKey,
            String loreKey, String texture) { }

    private static String progress(String label, int current, int target) {
        return (current >= target ? "✓ " : "• ") + label + ": " + current + "/" + target;
    }

    private enum MenuPage {
        ONBOARDING,
        MAIN,
        GAMES,
        GAME_DETAIL,
        GOALS,
        QUEUE,
        REPLAY,
        WAITING,
        SWITCH,
        COMMUNITY
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuPage page;
        private final String context;
        private final Inventory inventory;

        private MenuHolder(MenuPage page, int size, Component title) {
            this(page, null, size, title);
        }

        private MenuHolder(MenuPage page, String context, int size, Component title) {
            this.page = page;
            this.context = context;
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private Inventory inventory() {
            return inventory;
        }

        private MenuPage page() {
            return page;
        }

        private String context() {
            return context;
        }
    }
}
