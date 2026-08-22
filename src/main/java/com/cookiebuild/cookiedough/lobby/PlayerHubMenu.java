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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** One discoverable lobby menu, rendered natively for Java and Bedrock players. */
public final class PlayerHubMenu implements Listener {
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

    public PlayerHubMenu(CookieDough plugin, LobbyManager lobby, PlayerGoalTracker goals, FriendManager friends) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.goals = goals;
        this.friends = friends;
        this.actionKey = new NamespacedKey(plugin, "hub_menu_action");
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

    /** Shown until its first successful display; it deliberately fits on one page. */
    public boolean openOnboarding(Player player) {
        onboardingPlayers.add(player.getUniqueId());
        if (openBedrock(player, MenuPage.ONBOARDING)) return true;
        player.openInventory(onboardingInventory(player));
        return true;
    }

    /** Installs cross-edition queue controls after the game module prepares its waiting inventory. */
    public void enterQueue(Player player, String gameName) {
        if (player == null || gameName == null || gameName.isBlank()) return;
        queuedGames.put(player.getUniqueId(), gameName);
        bedrockSessions.invalidate(player.getUniqueId());
        queueHelpShown.remove(player.getUniqueId());
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
                            player, "game.rules.header", gameName), NamedTextColor.GOLD)
                            .append(Component.text(" " + message(player, ruleKey), NamedTextColor.GRAY)));
                }
                player.sendMessage(Component.text(message(
                        player, "queue.joined", gameName), NamedTextColor.GREEN));
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

    public void updateQueueWait(Player player, String gameName, long waitingSeconds) {
        if (player == null || waitingSeconds < 15 || !queueHelpShown.add(player.getUniqueId())) return;
        player.sendMessage(Component.text(message(player, "queue.help", gameName), NamedTextColor.YELLOW)
                .append(Component.text(" [" + message(player, "queue.help.action") + "]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/menu"))));
    }

    public void leaveQueue(Player player) {
        if (player == null) return;
        queuedGames.remove(player.getUniqueId());
        bedrockSessions.invalidate(player.getUniqueId());
        queueHelpShown.remove(player.getUniqueId());
        ItemStack item = player.getInventory().getItem(8);
        if (hasAction(item, "queue")) player.getInventory().setItem(8, null);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        queuedGames.remove(playerId);
        bedrockSessions.invalidate(playerId);
        queueHelpShown.remove(playerId);
        rulesShown.removeIf(value -> value.startsWith(playerId + ":"));
        onboardingPlayers.remove(playerId);
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
        if (openBedrock(player, MenuPage.REPLAY, gameName)) return;
        player.openInventory(replayInventory(player, gameName));
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
        inventory.setItem(11, item(Material.NETHER_STAR, message(player, "hub.quick.name"), "quick",
                message(player, "hub.quick.lore")));
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
        MenuHolder holder = new MenuHolder(MenuPage.GAMES, 36,
                Component.text(model.title(), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        int[] slots = { 9, 11, 13, 15, 17, 21, 23 };
        for (int index = 0; index < model.entries().size(); index++) {
            HubGameMenuModel.Entry entry = model.entries().get(index);
            inventory.setItem(slots[index], item(entry.icon(), entry.label(), entry.action(), entry.detail()));
        }
        inventory.setItem(31, item(Material.ARROW, message(player, "hub.back"), "back",
                message(player, "hub.back_lore")));
        return inventory;
    }

    private Inventory gameDetailInventory(Player player, String gameName) {
        HubGameMenuModel model = HubGameMenuModel.detail(gameName, player.locale());
        MenuHolder holder = new MenuHolder(MenuPage.GAME_DETAIL, gameName, 27,
                Component.text(model.title(), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        GamePresentation game = GamePresentation.find(gameName).orElseThrow();
        inventory.setItem(4, item(game.icon(), model.title(), "noop", model.content().split("\\n")));
        int[] slots = { 11, 15, 22 };
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
                progress(message(player, "hub.goals.eliminations"), view.weeklyEliminations(), 10),
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
        inventory.setItem(4, item(Material.CLOCK, message(player, "queue.menu.status", gameName), "noop",
                message(player, "queue.menu.status_hint")));
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

    private Inventory replayInventory(Player player, String gameName) {
        MenuHolder holder = new MenuHolder(MenuPage.REPLAY, gameName, 27,
                Component.text(message(player, "replay.menu.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(4, item(Material.COOKIE, message(player, "replay.menu.status", gameName), "noop",
                message(player, "replay.menu.status_hint")));
        inventory.setItem(11, item(Material.LIME_DYE, message(player, "replay.menu.same", gameName), "replay:same",
                message(player, "replay.menu.same_hint")));
        inventory.setItem(13, item(Material.NETHER_STAR, message(player, "replay.menu.quick"), "replay:quick",
                message(player, "replay.menu.quick_hint")));
        inventory.setItem(15, item(Material.OAK_DOOR, message(player, "replay.menu.lobby"), "replay:lobby",
                message(player, "replay.menu.lobby_hint")));
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
                || !hasAction(event.getItem(), "queue")) {
            return;
        }
        event.setCancelled(true);
        openQueue(event.getPlayer());
    }

    private void dispatch(Player player, String action, MenuPage source, String context) {
        if (source == MenuPage.ONBOARDING && OnboardingCompletionPolicy.completes(action)) {
            onboardingPlayers.remove(player.getUniqueId());
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
            lobby.requestActivity(player, gameName);
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
        switch (action) {
            case "quick" -> {
                player.closeInventory();
                lobby.requestQuickPlay(player);
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
            LobbyManager.teleportPlayerToLobby(cookiePlayer);
        }
        switch (action) {
            case "same" -> Bukkit.getScheduler().runTask(plugin,
                    () -> lobby.requestGame(player, gameName));
            case "quick" -> Bukkit.getScheduler().runTask(plugin,
                    () -> player.performCommand("quickplay"));
            case "lobby" -> { }
            default -> { }
        }
    }

    private void run(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private void showCommunityLinks(Player player) {
        player.closeInventory();
        player.sendMessage(Component.text(message(player, "hub.community.prefix") + " ", NamedTextColor.GOLD)
                .append(Component.text(message(player, "hub.community.discord"), NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl("https://discord.gg/ajmPnwh9g8")))
                .append(Component.text(" " + message(player, "hub.community.or") + " ", NamedTextColor.GRAY))
                .append(Component.text(message(player, "hub.community.app"), NamedTextColor.LIGHT_PURPLE)
                        .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com/#mobile-app")))
                .append(Component.text(" " + message(player, "hub.community.suffix"), NamedTextColor.GRAY)));
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
            case QUEUE, REPLAY -> throw new IllegalArgumentException("Context is required for " + page);
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
                            .content("§7" + message(player, "hub.onboarding.content"));
                    button(builder, actions, "§a§l" + message(player, "hub.quick.name")
                            + "\n§7" + message(player, "hub.quick.lore"), "quick");
                    button(builder, actions, "§6§l" + message(player, "hub.games.name")
                            + "\n§7" + message(player, "hub.onboarding.games_lore"), "games");
                    button(builder, actions, "§b§l" + message(player, "hub.onboarding.community_name")
                            + "\n§7" + message(player, "hub.onboarding.community_lore_app"), "community");
                    button(builder, actions, "§f§l" + message(player, "hub.onboarding.full_name"), "back");
                }
                case MAIN -> {
                    builder.title("§l§6" + message(player, "hub.title"))
                            .content("§7" + message(player, "hub.content"));
                    button(builder, actions, "§a§l" + message(player, "hub.quick.name")
                            + "\n§7" + message(player, "hub.quick.lore"), "quick");
                    button(builder, actions, "§6§l" + message(player, "hub.games.name"), "games");
                    button(builder, actions, "§b§l" + message(player, "hub.goals.name"), "goals");
                    button(builder, actions, "§d§l" + message(player, "hub.friends.name"), "friends");
                    button(builder, actions, "§e§l" + message(player, "hub.party.name"), "party");
                    button(builder, actions, "§9§l" + message(player, "hub.events.name"), "events");
                    button(builder, actions, "§5§l" + message(player, "hub.app.name")
                            + "\n§7" + message(player, "hub.app.lore"), "app");
                    button(builder, actions, "§f§l" + message(player, "hub.help.name"), "help");
                }
                case GAMES -> {
                    HubGameMenuModel model = HubGameMenuModel.index(player.locale());
                    builder.title("§l§6" + model.title()).content("§7" + model.content());
                    for (HubGameMenuModel.Entry entry : model.entries()) {
                        button(builder, actions, "§f§l" + entry.label() + "\n§7" + entry.detail(),
                                entry.action(), entry.bedrockTexture());
                    }
                    button(builder, actions, "§7" + message(player, "hub.back"), "back");
                }
                case GAME_DETAIL -> {
                    HubGameMenuModel model = HubGameMenuModel.detail(context, player.locale());
                    builder.title("§l§6" + model.title()).content("§7" + model.content());
                    for (HubGameMenuModel.Entry entry : model.entries()) {
                        button(builder, actions, "§f§l" + entry.label() + "\n§7" + entry.detail(),
                                entry.action(), entry.bedrockTexture());
                    }
                }
                case GOALS -> {
                    builder.title("§l§6" + message(player, "hub.goals.title"))
                            .content("§e" + message(player, "hub.goals.daily") + "\n§f"
                                    + message(player, "hub.goals.bedrock_daily", view.dailyMatches(), view.dailyWins())
                                    + "\n\n§b" + message(player, "hub.goals.weekly") + "\n§f"
                                    + message(player, "hub.goals.bedrock_weekly", view.weeklyMatches(),
                                            view.weeklyWins(), view.weeklyEliminations())
                                    + "\n\n§d" + message(player, "hub.goals.bedrock_achievements", view.achievements()));
                    button(builder, actions, "§7" + message(player, "hub.back"), "back");
                }
                case QUEUE -> {
                    builder.title("§l§6" + message(player, "queue.menu.title"))
                            .content("§7" + message(player, "queue.menu.status", context) + "\n"
                                    + message(player, "queue.menu.status_hint"));
                    button(builder, actions, "§c§l" + message(player, "queue.menu.leave")
                            + "\n§7" + message(player, "queue.menu.leave_hint"), "queue:leave");
                    button(builder, actions, "§6§l" + message(player, "queue.menu.switch")
                            + "\n§7" + message(player, "queue.menu.switch_hint"), "queue:switch");
                    button(builder, actions, "§b§l" + message(player, "queue.menu.practice")
                            + "\n§7" + message(player, "queue.menu.practice_hint"), "queue:practice");
                    button(builder, actions, "§d§l" + message(player, "queue.menu.rally")
                            + "\n§7" + message(player, "queue.menu.rally_hint"), "queue:rally");
                }
                case REPLAY -> {
                    builder.title("§l§6" + message(player, "replay.menu.title"))
                            .content("§7" + message(player, "replay.menu.status", context));
                    button(builder, actions, "§a§l" + message(player, "replay.menu.same", context)
                            + "\n§7" + message(player, "replay.menu.same_hint"), "replay:same");
                    button(builder, actions, "§6§l" + message(player, "replay.menu.quick")
                            + "\n§7" + message(player, "replay.menu.quick_hint"), "replay:quick");
                    button(builder, actions, "§f§l" + message(player, "replay.menu.lobby")
                            + "\n§7" + message(player, "replay.menu.lobby_hint"), "replay:lobby");
                }
            }
            builder.validResultHandler(response -> {
                int index = response.getClickedButtonId();
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
        if (page == MenuPage.REPLAY) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            return HubBedrockResponsePolicy.replayValid(
                    cookiePlayer != null && cookiePlayer.getState() == PlayerState.LOBBY,
                    cookiePlayer != null && GameManager.getGameOfPlayer(cookiePlayer) != null);
        }
        return true;
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action) {
        builder.button(label);
        actions.add(action);
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action,
            String texturePath) {
        BedrockFormImages.button(builder, label, texturePath);
        actions.add(action);
    }

    private ItemStack item(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(java.util.Arrays.stream(lore).map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
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
        REPLAY
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
