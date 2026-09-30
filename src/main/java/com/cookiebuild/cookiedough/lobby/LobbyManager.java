package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
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

    private final JavaPlugin plugin;
    private final List<GameNPC> gameNpcs = new ArrayList<>();
    private final LobbySignManager signManager;
    private final StatueManager statueManager;
    private LobbyPlayerCountDisplay playerCountDisplay;
    private SkyblockBillboard skyblockBillboard;
    private SkyblockLobbyGuide skyblockGuide;

    // Singleton
    private static LobbyManager instance;

    private record PassiveSource(PersistentActivity activity, Game spectatorGame) { }

    public LobbyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.signManager = new LobbySignManager(plugin);
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
        signManager.startSignRefreshTask();
    }

    public static LobbyManager getInstance() {
        return instance;
    }

    /**
     * Locale of shared lobby nameplates. Entity names are identical for every
     * viewer, so they follow the community's main language (config
     * {@code lobby.display-locale}, French by default).
     */
    static java.util.Locale displayLocale() {
        CookieDough plugin = CookieDough.getInstance();
        String tag = plugin == null ? "fr" : plugin.getConfig().getString("lobby.display-locale", "fr");
        return java.util.Locale.forLanguageTag((tag == null || tag.isBlank() ? "fr" : tag).replace('_', '-'));
    }

    public void shutdown() {
        if (skyblockGuide != null) {
            skyblockGuide.shutdown();
            skyblockGuide = null;
        }
        signManager.shutdown();
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

    public void addGameSign(Sign sign) {
        signManager.addGameSign(sign);
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

    public void startSkyblockGuide() {
        if (skyblockGuide != null) return;
        if (!(plugin instanceof CookieDough cookieDough)) {
            plugin.getLogger().warning("Skipping the Skyblock lobby guide: CookieDough runtime is unavailable");
            return;
        }
        skyblockGuide = new SkyblockLobbyGuide(cookieDough, this);
        skyblockGuide.start();
    }

    public static boolean teleportPlayerToLobby(CookiePlayer cookiePlayer) {
        if (cookiePlayer == null || cookiePlayer.getPlayer() == null) {
            return false;
        }
        Player player = cookiePlayer.getPlayer();
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld == null) {
            CookieDough.getInstance().getLogger().severe("Lobby world 'lobby' is not loaded!");
            return false;
        }

        Location sourceLocation = player.getLocation().clone();
        Location lobbySpawnLocation = lobbyWorld.getSpawnLocation();
        if (!lobbySpawnLocation.getChunk().load()) {
            CookieDough.getInstance().getLogger().severe(
                    "Could not load the lobby destination for " + player.getName());
            return false;
        }
        if (!ActivityRegistry.canLeave(cookiePlayer, "returned_lobby")) {
            ActivityRegistry.notifyLeaveBlocked(cookiePlayer, "returned_lobby");
            return false;
        }
        if (!CookieDough.getInstance().getPlayerTransitionFlightGuard()
                .teleport(player, lobbySpawnLocation)) {
            CookieDough.getInstance().getLogger().severe(
                    "Could not safely teleport " + player.getName() + " to the lobby");
            return false;
        }
        if (!player.getWorld().equals(lobbyWorld)) {
            CookieDough.getInstance().getLogger().severe(
                    "Lobby teleport for " + player.getName() + " was redirected to "
                            + player.getWorld().getName());
            return false;
        }

        // No source is destroyed until the destination teleport is acknowledged.
        // canLeave and leave execute synchronously on the main thread, so this
        // second check closes the contract without exposing an interaction tick.
        if (!ActivityRegistry.leave(cookiePlayer, "returned_lobby")) {
            CookieDough.getInstance().getLogger().severe(
                    "Could not leave the current activity after teleporting " + player.getName());
            if (!sourceLocation.getChunk().load()
                    || !CookieDough.getInstance().getPlayerTransitionFlightGuard()
                            .teleport(player, sourceLocation)) {
                CookieDough.getInstance().getLogger().severe(
                        "Could not restore " + player.getName() + " to the source after a rejected lobby leave");
            }
            return false;
        }
        CookieDough.getInstance().getPracticeManager().stop(player, false);
        // Remove active players and eliminated spectators from their roster.
        Game currentGame = GameManager.getGameOfPlayer(cookiePlayer);
        // A participant leaving a finished arena is a completed match even for
        // modules that record completion themselves instead of offerReplay().
        String finishedGameName = currentGame != null && currentGame.getState() == GameState.FINISHED
                && !currentGame.isExternalSpectator(player.getUniqueId()) ? currentGame.getGameName() : null;
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
        CookieDough.getInstance().getLogger().info(player.getName() + " has been teleported to the lobby.");

        giveQuickPlayItem(player);
        player.saveData();
        PlayerWrapperListener.showLobbyScoreboard(player);
        FunnelTelemetry.record(player, FunnelTelemetry.Event.LOBBY_READY, "world=lobby");
        PlayerHubMenu hub = CookieDough.getInstance().getPlayerHubMenu();
        if (hub != null) {
            // The "What next?" choice is scheduled from here, i.e. only once the
            // player is really back in the lobby, never during the transfer.
            hub.onLobbyArrival(player, finishedGameName);
        }
        return true;
    }

    public void joinAvailableGame(CookiePlayer player) {
        Game game = GameManager.getBestOpenGame();
        if (game != null && (player.getState() == PlayerState.PERSISTENT_MODE
                || player.getState() == PlayerState.SPECTATING)) {
            boolean replacing = GameManager.getQueueIntent(player.getPlayer().getUniqueId()) != null;
            if (GameManager.registerQueueIntent(player, game)) {
                player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                        replacing ? "lobby.queue.intent_replaced" : "lobby.queue.intent_registered",
                        player.getPlayer().locale(),
                        GamePresentation.readableName(game.getGameName(), player.getPlayer().locale())));
                return;
            }
        }
        // Quick Play never sends a lone player away to a solo mode: it joins the
        // ready match, else the busiest queue, else the featured mode, so the
        // next arrival starts the match. Solo activities are offered afterwards
        // as a way to wait, keeping the queue intent.
        if (game != null && player.getState() == PlayerState.LOBBY && game.addPlayerToAvailableTeam(player)) {
            GameManager.cancelQueueIntent(player.getPlayer().getUniqueId());
            // A mode may admit a late joiner straight into a running match:
            // report the arena that actually owns the player.
            Game joined = admittedGame(player, game);
            player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                    "lobby.join.quick_success", player.getPlayer().locale(),
                    GamePresentation.forGame(joined.getGameName()).displayName(player.getPlayer().locale()),
                    joined.getPlayerCount(), joined.getCapacity()));
            boolean partyMember = CookieDough.getInstance().getPartyManager().hasOnlinePartyCompanions(
                    player.getPlayer().getUniqueId());
            if (joined.getState() == GameState.OPEN && player.getState() == PlayerState.QUEUED
                    && shouldOfferWhileWaiting(joined.getPlayerCount(), joined.getMinimumPlayers(), partyMember,
                            joined.supportsSoloStart())) {
                CookieDough.getInstance().getPlayerHubMenu().offerWhileWaiting(player.getPlayer(),
                        joined.getGameName());
            }
            return;
        }
        player.getPlayer().sendMessage(ChatColor.RED + LocaleManager.getMessage(
                "lobby.join.no_available", player.getPlayer().locale()));
    }

    private static Game admittedGame(CookiePlayer player, Game requested) {
        Game owner = GameManager.getGameOfPlayer(player);
        return owner == null ? requested : owner;
    }

    /**
     * Secondary "play while you wait" choice after Quick Play, only when the
     * queue still needs players. A mode that starts a lone player's match by
     * itself (solo start) is not interrupted with an offer to leave.
     */
    static boolean shouldOfferWhileWaiting(int queuedPlayers, int minimumPlayers, boolean partyMember,
            boolean soloStart) {
        return !partyMember && !soloStart && queuedPlayers >= 1 && queuedPlayers < minimumPlayers;
    }

    /**
     * Leaves a waiting queue for a persistent activity (Skyblock) while keeping
     * a passive queue intent for the same mode: the next arrival still starts
     * the match and pulls this player back through the normal admission path.
     */
    public void playActivityWhileQueued(Player player, String activityName, String gameName) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || !PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage("player.data_loading", player.locale()));
            return;
        }
        Game queued = GameManager.getGameOfPlayer(cookiePlayer);
        if (queued == null || cookiePlayer.getState() != PlayerState.QUEUED
                || !queued.getGameName().equalsIgnoreCase(gameName)
                || ActivityRegistry.find(activityName) == null) {
            requestActivity(player, activityName);
            return;
        }
        if (!teleportPlayerToLobby(cookiePlayer)) return;
        requestActivity(player, activityName);
        if (cookiePlayer.getState() != PlayerState.PERSISTENT_MODE) return;
        Game target = GameManager.getOpenGameByName(gameName);
        if (target != null && GameManager.registerQueueIntent(cookiePlayer, target)) {
            FunnelTelemetry.record(player, FunnelTelemetry.Event.SELECTOR_OPENED,
                    "selector=play_while_waiting activity=" + activityName.replaceAll("[^A-Za-z0-9_-]", "")
                            + " game=" + gameName.replaceAll("[^A-Za-z0-9_-]", ""));
            player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("lobby.queue.intent_registered",
                    player.locale(), GamePresentation.readableName(gameName, player.locale())));
        }
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

    /** Applies one party/persistent-mode decision for commands, menus and NPCs. */
    public void requestSelectedActivity(Player player, String activityName) {
        GamePresentation presentation = GamePresentation.find(activityName).orElse(null);
        if (presentation != null && presentation.persistent()) {
            requestActivity(player, activityName);
            return;
        }
        com.cookiebuild.cookiedough.retention.PartyManager parties =
                CookieDough.getInstance().getPartyManager();
        if (!parties.isAvailable()) {
            player.sendMessage(ChatColor.YELLOW
                    + LocaleManager.getMessage("party.service.unavailable", player.locale()));
            return;
        }
        if (!parties.hasOnlinePartyCompanions(player.getUniqueId())) {
            requestActivity(player, activityName);
            return;
        }
        Game target = GameManager.getOpenGameByName(activityName);
        if (target == null) {
            sendNoOpenGame(player, activityName);
            return;
        }
        String result = parties.queueParty(player, target);
        if (result == null) {
            requestActivity(player, activityName);
        }
        else if (!result.isBlank()) {
            player.sendMessage(ChatColor.YELLOW + result);
        }
    }

    /** Applies the same durable-versus-online party rule to generic Quick Play. */
    public void requestSelectedQuickPlay(Player player) {
        String partyResult = CookieDough.getInstance().getPartyManager().queueParty(player);
        if (partyResult == null) {
            requestQuickPlay(player);
        }
        else if (!partyResult.isBlank()) {
            player.sendMessage(ChatColor.YELLOW + partyResult);
        }
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
            sendNoOpenGame(player, gameName);
            return;
        }
        if (!game.canAdmitPlayer(player)) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.game.access_denied", player.locale(),
                    GamePresentation.readableName(game.getGameName(), player.locale())));
            return;
        }
        if (cookiePlayer.getState() == PlayerState.PERSISTENT_MODE
                || cookiePlayer.getState() == PlayerState.SPECTATING) {
            boolean replacing = GameManager.getQueueIntent(player.getUniqueId()) != null;
            if (GameManager.registerQueueIntent(cookiePlayer, game)) {
                player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                        replacing ? "lobby.queue.intent_replaced" : "lobby.queue.intent_registered",
                        player.locale(), GamePresentation.readableName(game.getGameName(), player.locale())));
            } else {
                player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                        "lobby.game.unavailable", player.locale(),
                        GamePresentation.readableName(gameName, player.locale())));
            }
            return;
        }
        if (game.addPlayerToAvailableTeam(cookiePlayer)) {
            GameManager.cancelQueueIntent(player.getUniqueId());
            Game joined = admittedGame(cookiePlayer, game);
            player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("lobby.game.joined", player.locale(),
                    GamePresentation.forGame(joined.getGameName()).displayName(player.locale()),
                    joined.getPlayerCount(), joined.getCapacity()));
        } else {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage("lobby.game.unavailable", player.locale(),
                    GamePresentation.forGame(game.getGameName()).displayName(player.locale())));
        }
    }

    private void sendNoOpenGame(Player player, String gameName) {
        String available = String.join(", ", GameManager.getGames().stream().map(Game::getGameName).distinct()
                .map(name -> GamePresentation.readableName(name, player.locale()))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                "lobby.game.no_open", player.locale(), GamePresentation.readableName(gameName, player.locale()),
                available));
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
        PassiveSource source = passiveSource(cookiePlayer);
        if (cookiePlayer.getState() == PlayerState.SPECTATING
                && !transitionFromPassiveActivity(cookiePlayer)) {
            restorePassiveSource(cookiePlayer, source);
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
        } else {
            restorePassiveSource(cookiePlayer, source);
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
            return teleportPlayerToLobby(cookiePlayer);
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
        if (cookiePlayer == null || cookiePlayer.getPlayer() == null || game == null
                || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()
                || !game.canAdmitPlayer(cookiePlayer.getPlayer())) return false;
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
                net.kyori.adventure.text.Component.text(com.cookiebuild.cookiedough.ui.PlatformText.message(
                                player, "lobby.menu.item_action"),
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
        event.setCancelled(true);

        if (game.getState() != GameState.OPEN) {
            event.getPlayer().sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.sign.unavailable", event.getPlayer().locale()));
            return;
        }

        Player player = event.getPlayer();
        requestSelectedActivity(player, game.getGameName());
    }

    private Game findGameForSign(Sign sign) {
        return signManager.findGameForSign(sign);
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
        return signManager.getGameSigns();
    }

    /**
     * Debug method to manually refresh all signs and force client updates
     * Useful for testing and troubleshooting sign sync issues
     */

    public void debugRefreshAllSigns() {
        signManager.debugRefreshAllSigns();
    }

    /**
     * Force all nearby players to refresh their view of all signs
     */

    public void forceSignRefreshForAllPlayers() {
        signManager.forceSignRefreshForAllPlayers();
    }
}
