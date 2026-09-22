package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;

/** Owns lobby sign state, refresh scheduling and client synchronization. */
final class LobbySignManager {
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();
    private final JavaPlugin plugin;
    private final List<Sign> gameSigns = new ArrayList<>();
    private BukkitTask signRefreshTask;

    LobbySignManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        if (signRefreshTask != null) {
            signRefreshTask.cancel();
            signRefreshTask = null;
        }
    }

    void startSignRefreshTask() {
        plugin.getLogger().info("Starting sign refresh task (5 second interval)");
        signRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshSigns();
            }
        }.runTaskTimer(plugin, 0, 100);
    }

    private void refreshSigns() {
        ArrayList<Game> games = new ArrayList<>(GameManager.getGames().stream()
                .map(Game::getGameName).distinct().map(GameManager::getGameByName).toList());

        // Remove any invalid signs (destroyed blocks)
        int initialSignCount = gameSigns.size();
        gameSigns.removeIf(sign -> {
            if (sign == null) {
                plugin.getLogger().warning("Found null sign, removing from list");
                return true;
            }
            if (!sign.getBlock().getType().name().contains("SIGN")) {
                plugin.getLogger().warning("Found invalid sign block (type: " +
                        sign.getBlock().getType().name() + "), removing from list");
                return true;
            }
            return false;
        });

        if (initialSignCount != gameSigns.size()) {
            plugin.getLogger().info("Removed " + (initialSignCount - gameSigns.size()) +
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
                    wasUpdated = updateSignContentIfChanged(sign, game);
                } else {
                    wasUpdated = clearSignContentIfChanged(sign);
                }

                if (wasUpdated) {
                    signsActuallyUpdated++;
                }
            } else {
                plugin.getLogger().warning("Sign " + i + " is null or invalid during processing");
            }
        }

        // Log when the displayed game information changes.
        if (signsActuallyUpdated > 0) {
            plugin.getLogger().info("Updated " + signsActuallyUpdated + " signs");
        }
    }

    boolean updateSignContentIfChanged(Sign sign, Game game) {
        try {
            // Ensure chunk is loaded
            if (!sign.getBlock().getChunk().isLoaded()) {
                plugin.getLogger()
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
            plugin.getLogger().info("Updating sign for " + game.getGameName() +
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
                plugin.getLogger().warning("Sign update failed for " + game.getGameName());
            }

            // Send update to all nearby players
            sendSignUpdateToNearbyPlayers(sign);

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update sign at " +
                    sign.getBlock().getLocation() + ": " + e.getMessage());
            gameSigns.remove(sign);
            return false;
        }
    }

    boolean clearSignContentIfChanged(Sign sign) {
        try {
            // Check if sign is already empty
            boolean isEmpty = sign.getLine(0).isEmpty() &&
                    sign.getLine(1).isEmpty() &&
                    sign.getLine(2).isEmpty() &&
                    sign.getLine(3).isEmpty();

            if (isEmpty) {
                return false;
            }

            plugin.getLogger().info("Clearing empty sign at: " + sign.getBlock().getLocation());

            // Ensure chunk is loaded
            if (!sign.getBlock().getChunk().isLoaded()) {
                sign.getBlock().getChunk().load(true);
            }

            for (int i = 0; i < 4; i++) {
                sign.setLine(i, "");
            }

            boolean updateResult = sign.update(true);
            if (!updateResult) {
                plugin.getLogger().warning("Sign clear failed");
            }

            // Send update to all nearby players
            sendSignUpdateToNearbyPlayers(sign);

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to clear sign at " +
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
                    plugin.getLogger().info("Synced sign to " + playersNotified + " players");
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
            plugin.getLogger().warning("Failed to send sign update to players: " + e.getMessage());
        }
    }

    public void addGameSign(Sign sign) {
        plugin.getLogger().info("=== ADDING GAME SIGN ===");
        plugin.getLogger().info("Sign location: " + sign.getBlock().getLocation());
        plugin.getLogger().info("Sign block type: " + sign.getBlock().getType().name());
        plugin.getLogger().info("Current sign content:");
        for (int i = 0; i < 4; i++) {
            plugin.getLogger().info("  Line " + i + ": '" + sign.getLine(i) + "'");
        }

        gameSigns.add(sign);
        plugin.getLogger().info("Sign added successfully! Total signs now: " + gameSigns.size());

        // Log all current signs
        plugin.getLogger().info("All registered signs:");
        for (int i = 0; i < gameSigns.size(); i++) {
            Sign currentSign = gameSigns.get(i);
            plugin.getLogger().info("  Sign " + i + ": " + currentSign.getBlock().getLocation() +
                    " (type: " + currentSign.getBlock().getType().name() + ")");
        }
        plugin.getLogger().info("=== END ADDING GAME SIGN ===");
    }

    Game findGameForSign(Sign clickedSign) {
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

    public List<Sign> getGameSigns() {
        return gameSigns;
    }

    public void debugRefreshAllSigns() {
        plugin.getLogger().info("=== MANUAL SIGN DEBUG REFRESH ===");

        for (int i = 0; i < gameSigns.size(); i++) {
            Sign sign = gameSigns.get(i);
            plugin.getLogger()
                    .info("Debug refreshing sign " + i + " at: " + sign.getBlock().getLocation());

            // Log current server-side content
            plugin.getLogger().info("Current server-side sign content:");
            for (int lineNum = 0; lineNum < 4; lineNum++) {
                plugin.getLogger().info("  Line " + lineNum + ": '" + sign.getLine(lineNum) + "'");
            }

            // Force client refresh for all nearby players
            sendSignUpdateToNearbyPlayers(sign);

            // Additional verification - try to re-read the sign content
            try {
                Sign reloadedSign = (Sign) sign.getBlock().getState();
                plugin.getLogger().info("Re-read sign content after refresh:");
                for (int lineNum = 0; lineNum < 4; lineNum++) {
                    plugin.getLogger()
                            .info("  Line " + lineNum + ": '" + reloadedSign.getLine(lineNum) + "'");
                }

                // Check if there's any difference
                boolean contentMatches = true;
                for (int lineNum = 0; lineNum < 4; lineNum++) {
                    if (!sign.getLine(lineNum).equals(reloadedSign.getLine(lineNum))) {
                        contentMatches = false;
                        plugin.getLogger().warning("MISMATCH on line " + lineNum +
                                ": Original='" + sign.getLine(lineNum) + "' vs Reloaded='"
                                + reloadedSign.getLine(lineNum) + "'");
                    }
                }

                if (contentMatches) {
                    plugin.getLogger().info("Sign content verification: PASSED");
                } else {
                    plugin.getLogger()
                            .warning("Sign content verification: FAILED - Content mismatch detected!");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to re-read sign content: " + e.getMessage());
            }
        }

        plugin.getLogger().info("=== END MANUAL SIGN DEBUG REFRESH ===");
    }

    public void forceSignRefreshForAllPlayers() {
        plugin.getLogger().info("Forcing sign refresh for all players...");

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
                plugin.getLogger().info("Refreshed all signs for player: " + player.getName());
            }
        }
    }
}
