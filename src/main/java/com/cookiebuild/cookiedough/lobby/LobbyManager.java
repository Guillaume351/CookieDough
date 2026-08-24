package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.activity.ActivityAdmissionResult;
import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.activity.PersistentActivity;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class LobbyManager implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final List<GameNPC> gameNpcs = new ArrayList<>();
    private final List<Sign> gameSigns = new ArrayList<>();
    private final StatueManager statueManager;
    private LobbyPlayerCountDisplay playerCountDisplay;
    private SkyblockBillboard skyblockBillboard;
    private BukkitTask signRefreshTask;

    // Singleton
    private static LobbyManager instance;

    private record PassiveSource(PersistentActivity activity, Game spectatorGame) { }

    public LobbyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;

        // Champion heads and dense leaderboard panels are independently
        // configurable. The compact default renders one vanilla ArmorStand head
        // per selector and schedules no top-10 panel work.
        boolean championHeadsEnabled = plugin.getConfig().getBoolean(
                "lobby.champion-heads-enabled", true);
        boolean leaderboardPanelsEnabled = plugin.getConfig().getBoolean(
                "lobby.leaderboard-panels-enabled", false);
        this.statueManager = championHeadsEnabled || leaderboardPanelsEnabled
                ? new StatueManager(plugin, championHeadsEnabled, leaderboardPanelsEnabled)
                : null;

        // Remove only entities that Cookie Build explicitly owns. Map-authored
        // item frames, paintings, decorative mobs and other plugins' NPCs must
        // survive a CookieDough restart.
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            for (Entity entity : lobbyWorld.getEntities()) {
                if (LobbyEntityOwnership.isOwned(plugin, entity)) {
                    entity.remove();
                }
            }
            Location playerCountLocation = lobbyWorld.getSpawnLocation().clone().add(0.5, 3.0, 0.5);
            playerCountDisplay = new LobbyPlayerCountDisplay(plugin, playerCountLocation);
        }

        // enable NPC listeners
        Bukkit.getPluginManager().registerEvents(new NPCListener(), plugin);

        // Start sign refresh task
        startSignRefreshTask();
    }

    public static LobbyManager getInstance() {
        return instance;
    }

    private void startSignRefreshTask() {
        CookieDough.getInstance().getLogger().info("Starting sign refresh task (5 second interval)");
        signRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshSigns();
            }
        }.runTaskTimer(plugin, 0, 100);
    }

    public void shutdown() {
        if (signRefreshTask != null) {
            signRefreshTask.cancel();
            signRefreshTask = null;
        }
        gameNpcs.forEach(GameNPC::shutdown);
        gameNpcs.clear();
        if (playerCountDisplay != null) {
            playerCountDisplay.shutdown();
            playerCountDisplay = null;
        }
        if (statueManager != null) {
            statueManager.shutdown();
        }
        if (skyblockBillboard != null) {
            skyblockBillboard.shutdown();
            skyblockBillboard = null;
        }
        if (instance == this) {
            instance = null;
        }
    }

    private void refreshSigns() {
        ArrayList<Game> games = new ArrayList<>(GameManager.getGames().stream()
                .map(Game::getGameName).distinct().map(GameManager::getGameByName).toList());

        // Only log detailed refresh info if we expect changes or every 30 cycles (30
        // seconds)
        boolean detailedLogging = false;

        if (detailedLogging) {
            CookieDough.getInstance().getLogger().info("=== SIGN REFRESH ===");
            CookieDough.getInstance().getLogger().info("Games: " + games.size() + " | Signs: " + gameSigns.size());
        }

        // Remove any invalid signs (destroyed blocks)
        int initialSignCount = gameSigns.size();
        gameSigns.removeIf(sign -> {
            if (sign == null) {
                CookieDough.getInstance().getLogger().warning("Found null sign, removing from list");
                return true;
            }
            if (!sign.getBlock().getType().name().contains("SIGN")) {
                CookieDough.getInstance().getLogger().warning("Found invalid sign block (type: " +
                        sign.getBlock().getType().name() + "), removing from list");
                return true;
            }
            return false;
        });

        if (initialSignCount != gameSigns.size()) {
            CookieDough.getInstance().getLogger().info("Removed " + (initialSignCount - gameSigns.size()) +
                    " invalid signs. New count: " + gameSigns.size());
        }

        // Update existing signs
        int signsActuallyUpdated = 0;
        for (int i = 0; i < gameSigns.size(); i++) {
            Sign sign = gameSigns.get(i);

            if (sign != null && sign.getBlock().getType().name().contains("SIGN")) {
                boolean wasUpdated = false;
                if (i < games.size()) {
                    Game game = games.get(i);
                    wasUpdated = updateSignContentIfChanged(sign, game, detailedLogging);
                } else {
                    wasUpdated = clearSignContentIfChanged(sign, detailedLogging);
                }

                if (wasUpdated) {
                    signsActuallyUpdated++;
                }
            } else {
                CookieDough.getInstance().getLogger().warning("Sign " + i + " is null or invalid during processing");
            }
        }

        // Only log if signs were updated, or every 30 seconds
        if (signsActuallyUpdated > 0) {
            CookieDough.getInstance().getLogger().info("Updated " + signsActuallyUpdated + " signs");
        } else if (detailedLogging) {
            CookieDough.getInstance().getLogger().info("No sign updates needed");
        }
    }

    private static int refreshCycleCount = 0;

    private boolean shouldLogDetailed() {
        refreshCycleCount++;
        // Log detailed info every 30 cycles (30 seconds) or if it's the first cycle
        return refreshCycleCount == 1 || refreshCycleCount % 30 == 0;
    }

    private boolean updateSignContentIfChanged(Sign sign, Game game, boolean detailedLogging) {
        try {
            // Ensure chunk is loaded
            if (!sign.getBlock().getChunk().isLoaded()) {
                CookieDough.getInstance().getLogger()
                        .info("Loading chunk for sign at: " + sign.getBlock().getLocation());
                sign.getBlock().getChunk().load(true);
            }

            // Generate new content
            String newLine0 = ChatColor.BLUE + "Game";
            String newLine1 = ChatColor.GOLD + game.getGameName();

            String stateColor;
            String stateText = game.getState().toString();
            switch (game.getState()) {
                case OPEN:
                    stateColor = ChatColor.GREEN.toString();
                    break;
                case RUNNING:
                    stateColor = ChatColor.YELLOW.toString();
                    break;
                case FINISHED:
                    stateColor = ChatColor.RED.toString();
                    break;
                default:
                    stateColor = ChatColor.GRAY.toString();
            }

            String newLine2 = stateColor + stateText;
            String newLine3 = ChatColor.YELLOW + "" + game.getPlayerCount() + "/" + game.getCapacity() + " players";

            // Check if content actually changed
            boolean contentChanged = !sign.getLine(0).equals(newLine0) ||
                    !sign.getLine(1).equals(newLine1) ||
                    !sign.getLine(2).equals(newLine2) ||
                    !sign.getLine(3).equals(newLine3);

            if (!contentChanged) {
                return false;
            }

            // Always log when content actually changes
            CookieDough.getInstance().getLogger().info("Updating sign for " + game.getGameName() +
                    ": " + sign.getLine(3) + " → " + newLine3);

            // Update content
            sign.setLine(0, newLine0);
            sign.setLine(1, newLine1);
            sign.setLine(2, newLine2);
            sign.setLine(3, newLine3);

            sign.setWaxed(false);
            sign.setGlowingText(true);

            boolean updateResult = sign.update(true);
            if (!updateResult) {
                CookieDough.getInstance().getLogger().warning("Sign update failed for " + game.getGameName());
            }

            // Send update to all nearby players
            sendSignUpdateToNearbyPlayers(sign);

            return true;

        } catch (Exception e) {
            CookieDough.getInstance().getLogger().severe("Failed to update sign at " +
                    sign.getBlock().getLocation() + ": " + e.getMessage());
            gameSigns.remove(sign);
            return false;
        }
    }

    private boolean clearSignContentIfChanged(Sign sign, boolean detailedLogging) {
        try {
            // Check if sign is already empty
            boolean isEmpty = sign.getLine(0).isEmpty() &&
                    sign.getLine(1).isEmpty() &&
                    sign.getLine(2).isEmpty() &&
                    sign.getLine(3).isEmpty();

            if (isEmpty) {
                return false;
            }

            CookieDough.getInstance().getLogger().info("Clearing empty sign at: " + sign.getBlock().getLocation());

            // Ensure chunk is loaded
            if (!sign.getBlock().getChunk().isLoaded()) {
                sign.getBlock().getChunk().load(true);
            }

            for (int i = 0; i < 4; i++) {
                sign.setLine(i, "");
            }

            boolean updateResult = sign.update(true);
            if (!updateResult) {
                CookieDough.getInstance().getLogger().warning("Sign clear failed");
            }

            // Send update to all nearby players
            sendSignUpdateToNearbyPlayers(sign);

            return true;

        } catch (Exception e) {
            CookieDough.getInstance().getLogger().severe("Failed to clear sign at " +
                    sign.getBlock().getLocation() + ": " + e.getMessage());
            gameSigns.remove(sign);
            return false;
        }
    }

    private void sendSignUpdateToNearbyPlayers(Sign sign) {
        try {
            Location signLocation = sign.getBlock().getLocation();
            World world = signLocation.getWorld();

            if (world != null) {
                int playersNotified = 0;
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distance(signLocation) <= 64) {
                        // Use both methods for maximum compatibility
                        player.sendBlockChange(signLocation, sign.getBlock().getBlockData());
                        player.sendSignChange(signLocation, sign.getLines());
                        playersNotified++;
                    }
                }

                if (playersNotified > 0) {
                    CookieDough.getInstance().getLogger().info("Synced sign to " + playersNotified + " players");
                }

                // Delayed backup update for any missed clients
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            for (Player player : world.getPlayers()) {
                                if (player.getLocation().distance(signLocation) <= 64) {
                                    player.sendSignChange(signLocation, sign.getLines());
                                }
                            }
                        } catch (Exception e) {
                            // Silent failure for delayed updates
                        }
                    }
                }.runTaskLater(plugin, 5);

            }
        } catch (Exception e) {
            CookieDough.getInstance().getLogger().warning("Failed to send sign update to players: " + e.getMessage());
        }
    }

    public void addGameSign(Sign sign) {
        CookieDough.getInstance().getLogger().info("=== ADDING GAME SIGN ===");
        CookieDough.getInstance().getLogger().info("Sign location: " + sign.getBlock().getLocation());
        CookieDough.getInstance().getLogger().info("Sign block type: " + sign.getBlock().getType().name());
        CookieDough.getInstance().getLogger().info("Current sign content:");
        for (int i = 0; i < 4; i++) {
            CookieDough.getInstance().getLogger().info("  Line " + i + ": '" + sign.getLine(i) + "'");
        }

        gameSigns.add(sign);
        CookieDough.getInstance().getLogger().info("Sign added successfully! Total signs now: " + gameSigns.size());

        // Log all current signs
        CookieDough.getInstance().getLogger().info("All registered signs:");
        for (int i = 0; i < gameSigns.size(); i++) {
            Sign currentSign = gameSigns.get(i);
            CookieDough.getInstance().getLogger().info("  Sign " + i + ": " + currentSign.getBlock().getLocation() +
                    " (type: " + currentSign.getBlock().getType().name() + ")");
        }
        CookieDough.getInstance().getLogger().info("=== END ADDING GAME SIGN ===");
    }

    public void addGameNpc(String gameName, Location location) {
        addGameNpc(gameName, location, new Vector(2, 0, 0));
    }

    public void addGameNpc(String gameName, Location location, Vector statueOffset) {
        GameNPC npc = new GameNPC(gameName, location, CookieDough.getInstance());
        gameNpcs.add(npc);
        // keep chunk loaded
        npc.getNPC().getLocation().getChunk().load(true);

        // The default showcase is one compact champion head. Dense leaderboard
        // panels remain independently disabled for the lobby spawn.
        if (statueManager != null) {
            Location statueLocation = location.clone().add(statueOffset);
            statueManager.createStatue(gameName, statueLocation);
        }
    }

    public void setupSkyblockBillboard() {
        if (!plugin.getConfig().getBoolean("lobby.skyblock-billboard.enabled", true)) {
            return;
        }
        String path = "lobby.skyblock-billboard";
        World world = Bukkit.getWorld(plugin.getConfig().getString(path + ".world", "lobby"));
        if (world == null) {
            plugin.getLogger().warning("Skipping the Skyblock billboard: configured world is not loaded");
            return;
        }
        Location location = new Location(world,
                plugin.getConfig().getDouble(path + ".x"),
                plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"));
        try {
            skyblockBillboard = SkyblockBillboard.create(plugin, location,
                    plugin.getConfig().getString(path + ".facing", "SOUTH"),
                    plugin.getConfig().getInt(path + ".map-id", -1));
        } catch (RuntimeException error) {
            // A production lobby may have different backing blocks than the
            // versioned test world. Never fail CookieDough startup for an
            // optional visual; the Bee NPC remains fully usable.
            plugin.getLogger().warning("Skipping the Skyblock billboard because its frame could not spawn: "
                    + error.getMessage());
            return;
        }
        if (skyblockBillboard != null && skyblockBillboard.mapId()
                != plugin.getConfig().getInt(path + ".map-id", -1)) {
            plugin.getConfig().set(path + ".map-id", skyblockBillboard.mapId());
            plugin.saveConfig();
        }
        if (skyblockBillboard != null) {
            plugin.getLogger().info("Skyblock lobby billboard ready with persistent map id "
                    + skyblockBillboard.mapId());
        }
    }

    public static void teleportPlayerToLobby(CookiePlayer cookiePlayer) {
        if (cookiePlayer == null || cookiePlayer.getPlayer() == null) {
            return;
        }
        Player player = cookiePlayer.getPlayer();
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            CookieDough.getInstance().getPracticeManager().stop(player, false);
            if (!ActivityRegistry.leave(cookiePlayer, "returned_lobby")) {
                return;
            }
            // Remove active players and eliminated spectators from their roster.
            Game currentGame = GameManager.getGameOfPlayer(cookiePlayer);
            if (currentGame != null) {
                currentGame.removePlayer(cookiePlayer, "returned_lobby");
            } else if (cookiePlayer.getState() == PlayerState.QUEUED
                    || cookiePlayer.getState() == PlayerState.IN_GAME
                    || cookiePlayer.getState() == PlayerState.SPECTATING) {
                    CookieDough.getInstance().getLogger().severe("Player " + cookiePlayer.getPlayer().getName()
                            + " is in a game but no game was found.");
            }
            cookiePlayer.resetPlayer();
            cookiePlayer.setState(PlayerState.LOBBY);

            Location lobbySpawnLocation = lobbyWorld.getSpawnLocation();
            player.teleport(lobbySpawnLocation);
            CookieDough.getInstance().getLogger().info(player.getName() + " has been teleported to the lobby.");

            giveQuickPlayItem(player);
            player.saveData();
            PlayerWrapperListener.showLobbyScoreboard(player);
            FunnelTelemetry.record(player, FunnelTelemetry.Event.LOBBY_READY, "world=lobby");
        } else {
            CookieDough.getInstance().getLogger().severe("Lobby world 'lobby' is not loaded!");
        }
    }

    public void joinAvailableGame(CookiePlayer player) {
        Game game = GameManager.getBestOpenGame();
        if (game != null && (player.getState() == PlayerState.PERSISTENT_MODE
                || player.getState() == PlayerState.SPECTATING)) {
            boolean replacing = GameManager.getQueueIntent(player.getPlayer().getUniqueId()) != null;
            if (GameManager.registerQueueIntent(player, game)) {
                player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                        replacing ? "lobby.queue.intent_replaced" : "lobby.queue.intent_registered",
                        player.getPlayer().locale(), game.getGameName()));
                return;
            }
        }
        boolean partyMember = CookieDough.getInstance().getPartyManager().getPartyId(
                player.getPlayer().getUniqueId()) != null;
        if (shouldSuggestSolo(player.getState(),
                ModePopulationService.isPersistentActivityAvailable("Skyblock"), partyMember,
                ModePopulationService.hasReadyMatchForOneMorePlayer())) {
            CookieDough.getInstance().getPlayerHubMenu().openSoloSuggestion(player.getPlayer());
            return;
        }
        if (game != null && player.getState() == PlayerState.LOBBY && game.addPlayerToAvailableTeam(player)) {
            player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                    "lobby.join.quick_success", player.getPlayer().locale(),
                    GamePresentation.forGame(game.getGameName()).displayName(player.getPlayer().locale()),
                    game.getPlayerCount(), game.getCapacity()));
            return;
        }
        player.getPlayer().sendMessage(ChatColor.RED + LocaleManager.getMessage(
                "lobby.join.no_available", player.getPlayer().locale()));
    }

    static boolean shouldSuggestSolo(PlayerState state, boolean skyblockAvailable,
            boolean partyMember, boolean readyMatch) {
        return state == PlayerState.LOBBY && skyblockAvailable && !partyMember && !readyMatch;
    }

    public void requestQuickPlay(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        FunnelTelemetry.record(player, FunnelTelemetry.Event.SELECTOR_OPENED, "selector=quick_play");
        if (cookiePlayer == null) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage("player.data_loading", player.locale()));
            return;
        }
        if (!PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            PlayerWrapperListener.queueQuickPlayWhenReady(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                    "lobby.quick_queued", player.locale()));
            return;
        }
        joinAvailableGame(cookiePlayer);
    }

    public void requestGame(Player player, String gameName) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        FunnelTelemetry.record(player, FunnelTelemetry.Event.SELECTOR_OPENED,
                "selector=direct game=" + gameName.replaceAll("[^A-Za-z0-9_-]", ""));
        if (cookiePlayer == null || !PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                    "player.data_loading", player.locale()));
            return;
        }

        Game game = GameManager.getOpenGameByName(gameName);
        if (game == null) {
            String available = GameManager.getGames().stream().map(Game::getGameName).distinct().sorted().toList()
                    .toString();
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.game.no_open", player.locale(), gameName, available));
            return;
        }
        if (cookiePlayer.getState() == PlayerState.PERSISTENT_MODE
                || cookiePlayer.getState() == PlayerState.SPECTATING) {
            boolean replacing = GameManager.getQueueIntent(player.getUniqueId()) != null;
            if (GameManager.registerQueueIntent(cookiePlayer, game)) {
                player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                        replacing ? "lobby.queue.intent_replaced" : "lobby.queue.intent_registered",
                        player.locale(), game.getGameName()));
            } else {
                player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                        "lobby.game.unavailable", player.locale(), gameName));
            }
            return;
        }
        GameManager.cancelQueueIntent(player.getUniqueId());
        if (game.addPlayerToAvailableTeam(cookiePlayer)) {
            player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("lobby.game.joined", player.locale(),
                    GamePresentation.forGame(game.getGameName()).displayName(player.locale()),
                    game.getPlayerCount(), game.getCapacity()));
        } else {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage("lobby.game.unavailable", player.locale(),
                    GamePresentation.forGame(game.getGameName()).displayName(player.locale())));
        }
    }

    /** Opens a running arena as a viewer from either lobby, Skyblock or another spectator session. */
    public void requestSpectate(Player player, String gameName) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || !PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage("player.data_loading", player.locale()));
            return;
        }
        Game game = GameManager.getSpectatableGameByName(gameName);
        if (game == null) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.spectate.unavailable", player.locale(), gameName));
            return;
        }
        if (game.isExternalSpectator(player.getUniqueId())) return;
        PassiveSource source = passiveSource(cookiePlayer);
        if (!PassiveActivityTransition.execute(
                () -> game.preflightSpectatorAdmission(cookiePlayer),
                () -> transitionFromPassiveActivity(cookiePlayer),
                () -> game.addSpectator(cookiePlayer),
                () -> restorePassiveSource(cookiePlayer, source))) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.spectate.unavailable", player.locale(), gameName));
        }
    }

    /** Routes persistent destinations without adding them to match Quick Play. */
    public void requestActivity(Player player, String activityName) {
        if (ActivityRegistry.find(activityName) == null) {
            requestGame(player, activityName);
            return;
        }
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        FunnelTelemetry.record(player, FunnelTelemetry.Event.SELECTOR_OPENED,
                "selector=persistent activity=" + activityName.replaceAll("[^A-Za-z0-9_-]", ""));
        if (cookiePlayer == null) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage("player.data_loading", player.locale()));
            return;
        }
        if (!PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            PlayerWrapperListener.queueActivityWhenReady(player.getUniqueId(), activityName);
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage("player.data_loading", player.locale()));
            return;
        }
        if (cookiePlayer.getState() == PlayerState.SPECTATING
                && !transitionFromPassiveActivity(cookiePlayer)) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.queue.leave_failed", player.locale()));
            return;
        }
        if (cookiePlayer.getState() != PlayerState.LOBBY
                && cookiePlayer.getState() != PlayerState.PERSISTENT_MODE) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.game.unavailable", player.locale(), activityName));
            return;
        }
        ActivityAdmissionResult result = ActivityRegistry.enter(activityName, cookiePlayer);
        if (result.admitted()) {
            PlayerWrapperListener.completeOnboarding(player, "activity:" + activityName);
        }
        player.sendMessage((result.admitted() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    /**
     * Leaves passive activities through their authoritative cleanup before a
     * queue admission. A failed Skyblock inventory transfer never strands the
     * player in two activities.
     */
    private boolean transitionFromPassiveActivity(CookiePlayer cookiePlayer) {
        if (cookiePlayer == null) return false;
        if (cookiePlayer.getState() == PlayerState.PERSISTENT_MODE
                || cookiePlayer.getState() == PlayerState.SPECTATING) {
            teleportPlayerToLobby(cookiePlayer);
        }
        return cookiePlayer.getState() == PlayerState.LOBBY
                && GameManager.getGameOfPlayer(cookiePlayer) == null;
    }

    /** Called by GameManager only after intents can satisfy the arena minimum. */
    public boolean admitQueuedIntent(CookiePlayer cookiePlayer, Game game) {
        return admitQueuedParty(List.of(cookiePlayer), game);
    }

    /**
     * Two-phase party admission. No member leaves a passive activity before all
     * preflights succeed; any late transition/game failure restores every source
     * and removes every partial roster mutation.
     */
    public boolean admitQueuedParty(List<CookiePlayer> members, Game game) {
        if (members == null || members.isEmpty() || members.stream().anyMatch(java.util.Objects::isNull)
                || game == null || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()
                || game.getPartyAdmissionProblem(members.size()) != null
                || members.stream().anyMatch(member -> !canAdmitQueuedIntent(member, game))) {
            return false;
        }
        Map<UUID, PassiveSource> sources = new java.util.LinkedHashMap<>();
        for (CookiePlayer member : members) {
            sources.put(member.getPlayer().getUniqueId(), passiveSource(member));
        }
        List<CookiePlayer> transitioned = new ArrayList<>();
        for (CookiePlayer member : members) {
            if (!transitionFromPassiveActivity(member)) {
                transitioned.forEach(previous -> restorePassiveSource(previous,
                        sources.get(previous.getPlayer().getUniqueId())));
                restorePassiveSource(member, sources.get(member.getPlayer().getUniqueId()));
                return false;
            }
            transitioned.add(member);
        }
        List<CookiePlayer> admitted = new ArrayList<>();
        for (CookiePlayer member : members) {
            if (!game.addPlayerToAvailableTeam(member)) {
                admitted.forEach(previous -> game.removePlayer(previous, "party_admission_rollback"));
                transitioned.forEach(previous -> {
                    if (previous.getState() != PlayerState.LOBBY
                            || GameManager.getGameOfPlayer(previous) != null) {
                        teleportPlayerToLobby(previous);
                    }
                    restorePassiveSource(previous, sources.get(previous.getPlayer().getUniqueId()));
                });
                return false;
            }
            admitted.add(member);
        }
        return true;
    }

    public boolean canAdmitQueuedIntent(CookiePlayer cookiePlayer, Game game) {
        if (cookiePlayer == null || game == null || game.getState() != GameState.OPEN
                || !game.isAdmissionsOpen()) return false;
        if (cookiePlayer.getState() == PlayerState.LOBBY) {
            return GameManager.getGameOfPlayer(cookiePlayer) == null;
        }
        if (cookiePlayer.getState() == PlayerState.PERSISTENT_MODE) {
            return ActivityRegistry.canLeave(cookiePlayer, "returned_lobby");
        }
        Game current = GameManager.getGameOfPlayer(cookiePlayer);
        return cookiePlayer.getState() == PlayerState.SPECTATING && current != null
                && current.isExternalSpectator(cookiePlayer.getPlayer().getUniqueId());
    }

    private static PassiveSource passiveSource(CookiePlayer player) {
        if (player == null || player.getPlayer() == null) return new PassiveSource(null, null);
        PersistentActivity activity = ActivityRegistry.owner(player.getPlayer().getUniqueId());
        Game game = GameManager.getGameOfPlayer(player);
        Game spectatorGame = game != null && game.isExternalSpectator(player.getPlayer().getUniqueId())
                ? game : null;
        return new PassiveSource(activity, spectatorGame);
    }

    private static boolean restorePassiveSource(CookiePlayer player, PassiveSource source) {
        if (player == null || source == null || player.getPlayer() == null || !player.getPlayer().isOnline()) {
            return false;
        }
        if (source.activity() != null
                && ActivityRegistry.owner(player.getPlayer().getUniqueId()) == source.activity()) return true;
        if (source.spectatorGame() != null
                && source.spectatorGame().isExternalSpectator(player.getPlayer().getUniqueId())) return true;
        if (player.getState() != PlayerState.LOBBY || GameManager.getGameOfPlayer(player) != null) {
            teleportPlayerToLobby(player);
        }
        if (source.activity() != null) {
            return ActivityRegistry.enter(source.activity().name(), player).admitted();
        }
        return source.spectatorGame() == null || source.spectatorGame().addSpectator(player);
    }

    private static void giveQuickPlayItem(Player player) {
        ItemStack quickPlay = new ItemStack(Material.COMPASS);
        ItemMeta meta = quickPlay.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(LocaleManager.getMessage(
                        "lobby.menu.item_name", player.locale()),
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text(LocaleManager.getMessage(
                                "lobby.menu.item_lore", player.locale()),
                        net.kyori.adventure.text.format.NamedTextColor.GRAY),
                net.kyori.adventure.text.Component.text(LocaleManager.getMessage(
                                "lobby.menu.item_action", player.locale()),
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
        meta.getPersistentDataContainer().set(new NamespacedKey(CookieDough.getInstance(), "quick_play"),
                PersistentDataType.BYTE, (byte) 1);
        quickPlay.setItemMeta(meta);
        player.getInventory().setItem(0, quickPlay);
    }

    public void addGameNpc(Entity npc) {
        // TODO: add NPCs
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(CookieDough.getInstance(), "quick_play"), PersistentDataType.BYTE)) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            event.setCancelled(true);
            CookieDough.getInstance().getPlayerHubMenu().open(event.getPlayer());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (!(clickedBlock != null && clickedBlock.getState() instanceof Sign sign)) {
            return;
        }

        Game game = findGameForSign(sign);
        if (game == null) {
            return;
        }

        if (game.getState() != GameState.OPEN) {
            event.getPlayer().sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.sign.unavailable", event.getPlayer().locale()));
            return;
        }

        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.sign.profile_missing", player.locale()));
            return;
        }

        if (cookiePlayer.getState() == PlayerState.LOBBY) {
            if (!game.addPlayerToAvailableTeam(cookiePlayer)) {
                player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                        "lobby.sign.join_failed", player.locale(),
                        GamePresentation.forGame(game.getGameName()).displayName(player.locale())));
            }
        }
    }

    private Game findGameForSign(Sign clickedSign) {
        String frontLineOne = getPlainLine(clickedSign, Side.FRONT, 1);
        String backLineOne = getPlainLine(clickedSign, Side.BACK, 1);

        for (Game game : GameManager.getGames()) {
            String gameName = game.getGameName();
            if (containsIgnoreCase(frontLineOne, gameName) || containsIgnoreCase(backLineOne, gameName)) {
                return GameManager.getGameByName(gameName);
            }
        }
        return null;
    }

    private String getPlainLine(Sign sign, Side side, int line) {
        return PLAIN_TEXT_SERIALIZER.serialize(sign.getSide(side).line(line));
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && expected != null &&
                value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    public List<GameNPC> getGameNpcs() {
        return gameNpcs;
    }

    public GameNPC getGameNpcByName(String gameName) {
        for (GameNPC npc : gameNpcs) {
            if (npc.getGameName().equalsIgnoreCase(gameName)) {
                return npc;
            }
        }
        return null;
    }

    public StatueManager getStatueManager() {
        return statueManager;
    }

    public List<Sign> getGameSigns() {
        return gameSigns;
    }

    /**
     * Debug method to manually refresh all signs and force client updates
     * Useful for testing and troubleshooting sign sync issues
     */
    public void debugRefreshAllSigns() {
        CookieDough.getInstance().getLogger().info("=== MANUAL SIGN DEBUG REFRESH ===");

        for (int i = 0; i < gameSigns.size(); i++) {
            Sign sign = gameSigns.get(i);
            CookieDough.getInstance().getLogger()
                    .info("Debug refreshing sign " + i + " at: " + sign.getBlock().getLocation());

            // Log current server-side content
            CookieDough.getInstance().getLogger().info("Current server-side sign content:");
            for (int lineNum = 0; lineNum < 4; lineNum++) {
                CookieDough.getInstance().getLogger().info("  Line " + lineNum + ": '" + sign.getLine(lineNum) + "'");
            }

            // Force client refresh for all nearby players
            sendSignUpdateToNearbyPlayers(sign);

            // Additional verification - try to re-read the sign content
            try {
                Sign reloadedSign = (Sign) sign.getBlock().getState();
                CookieDough.getInstance().getLogger().info("Re-read sign content after refresh:");
                for (int lineNum = 0; lineNum < 4; lineNum++) {
                    CookieDough.getInstance().getLogger()
                            .info("  Line " + lineNum + ": '" + reloadedSign.getLine(lineNum) + "'");
                }

                // Check if there's any difference
                boolean contentMatches = true;
                for (int lineNum = 0; lineNum < 4; lineNum++) {
                    if (!sign.getLine(lineNum).equals(reloadedSign.getLine(lineNum))) {
                        contentMatches = false;
                        CookieDough.getInstance().getLogger().warning("MISMATCH on line " + lineNum +
                                ": Original='" + sign.getLine(lineNum) + "' vs Reloaded='"
                                + reloadedSign.getLine(lineNum) + "'");
                    }
                }

                if (contentMatches) {
                    CookieDough.getInstance().getLogger().info("Sign content verification: PASSED");
                } else {
                    CookieDough.getInstance().getLogger()
                            .warning("Sign content verification: FAILED - Content mismatch detected!");
                }

            } catch (Exception e) {
                CookieDough.getInstance().getLogger().warning("Failed to re-read sign content: " + e.getMessage());
            }
        }

        CookieDough.getInstance().getLogger().info("=== END MANUAL SIGN DEBUG REFRESH ===");
    }

    /**
     * Force all nearby players to refresh their view of all signs
     */
    public void forceSignRefreshForAllPlayers() {
        CookieDough.getInstance().getLogger().info("Forcing sign refresh for all players...");

        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            for (Player player : lobbyWorld.getPlayers()) {
                for (Sign sign : gameSigns) {
                    if (sign != null && sign.getBlock().getLocation().getWorld().equals(lobbyWorld)) {
                        // Force refresh this sign for this player
                        player.sendSignChange(sign.getBlock().getLocation(), sign.getLines());
                        player.sendBlockChange(sign.getBlock().getLocation(), sign.getBlock().getBlockData());
                    }
                }
                CookieDough.getInstance().getLogger().info("Refreshed all signs for player: " + player.getName());
            }
        }
    }
}
