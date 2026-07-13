package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;

public class LobbyManager implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final List<GameNPC> gameNpcs = new ArrayList<>();
    private final List<Sign> gameSigns = new ArrayList<>();
    private final StatueManager statueManager;

    // Singleton
    private static LobbyManager instance;

    public LobbyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;

        // Initialize StatueManager
        this.statueManager = new StatueManager(plugin);

        // Get lobby world, remove all entities
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            for (Entity entity : lobbyWorld.getEntities()) {
                // We should only remove non-player entities that are not part of the game.
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
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
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshSigns();
            }
        }.runTaskTimer(plugin, 0, 100);
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
        GameNPC npc = new GameNPC(gameName, location, CookieDough.getInstance());
        gameNpcs.add(npc);
        // keep chunk loaded
        npc.getNPC().getLocation().getChunk().load(true);

        // Create statue next to NPC (offset by 2 blocks in x direction)
        Location statueLocation = location.clone().add(2, 0, 0);
        statueManager.createStatue(gameName, statueLocation);
    }

    public static void teleportPlayerToLobby(CookiePlayer cookiePlayer) {
        if (cookiePlayer == null || cookiePlayer.getPlayer() == null) {
            return;
        }
        Player player = cookiePlayer.getPlayer();
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            CookieDough.getInstance().getPracticeManager().stop(player, false);
            // Remove active players and eliminated spectators from their roster.
            Game currentGame = GameManager.getGameOfPlayer(cookiePlayer);
            if (currentGame != null) {
                currentGame.removePlayer(cookiePlayer, "returned_lobby");
            } else if (cookiePlayer.getState() == PlayerState.IN_GAME
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
            PlayerWrapperListener.showLobbyScoreboard(player);
            FunnelTelemetry.record(player, FunnelTelemetry.Event.LOBBY_READY, "world=lobby");
        } else {
            CookieDough.getInstance().getLogger().severe("Lobby world 'lobby' is not loaded!");
        }
    }

    public void joinAvailableGame(CookiePlayer player) {
        Game game = GameManager.getBestOpenGame();
        if (game != null && game.addPlayerToAvailableTeam(player)) {
            player.getPlayer().sendMessage(ChatColor.GREEN + "Quick Play: joined " + game.getGameName()
                    + " (" + game.getPlayerCount() + "/" + game.getCapacity() + ").");
            return;
        }
        player.getPlayer().sendMessage(ChatColor.RED + "No available games. Please try again shortly.");
    }

    public void requestQuickPlay(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        FunnelTelemetry.record(player, FunnelTelemetry.Event.SELECTOR_OPENED, "selector=quick_play");
        if (cookiePlayer == null) {
            player.sendMessage(ChatColor.RED + "Your player profile is still loading. Please try again.");
            return;
        }
        if (!PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            PlayerWrapperListener.queueQuickPlayWhenReady(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Quick Play is queued while your profile loads…");
            return;
        }
        joinAvailableGame(cookiePlayer);
    }

    private static void giveQuickPlayItem(Player player) {
        ItemStack quickPlay = new ItemStack(Material.COMPASS);
        ItemMeta meta = quickPlay.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Quick Play",
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        meta.lore(List.of(net.kyori.adventure.text.Component.text("Join the game closest to starting",
                net.kyori.adventure.text.format.NamedTextColor.GRAY)));
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
            event.setCancelled(true);
            requestQuickPlay(event.getPlayer());
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
            event.getPlayer().sendMessage(ChatColor.RED + "This game is not available.");
            return;
        }

        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) {
            player.sendMessage(ChatColor.RED + "Could not find your player data. Please try again.");
            return;
        }

        if (cookiePlayer.getState() != PlayerState.IN_GAME) {
            if (!game.addPlayerToAvailableTeam(cookiePlayer)) {
                player.sendMessage(ChatColor.RED + "Failed to join " + game.getGameName()
                        + ". The game might be full.");
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
