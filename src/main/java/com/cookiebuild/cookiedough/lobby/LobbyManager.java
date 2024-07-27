package com.cookiebuild.cookiedough.lobby;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class LobbyManager implements Listener {
    private final JavaPlugin plugin;
    private final List<GameStatus> activeGames = new ArrayList<>(); // Initialize the list
    private final List<Sign> gameSigns;
    //private final List<Entity> gameNpcs; // TODO: add NPCs

    // Singleton
    private static LobbyManager instance;

    public LobbyManager(JavaPlugin plugin, List<Sign> gameSigns) {
        this.plugin = plugin;
        this.gameSigns = gameSigns;
        instance = this;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        startSignRefreshTask();
    }

    public static LobbyManager getInstance() {
        return instance;
    }

    public void registerGame(GameStatus game) {
        CookieDough.getInstance().getLogger().info("Registering game " + game.getGameId() + " of " + game.getGameName());
        activeGames.add(game);
    }

    private void startSignRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshSigns();
            }
        }.runTaskTimer(plugin, 0, 20 * 10); // Refresh every 10 seconds
    }

    private void refreshSigns() {
        for (int i = 0; i < activeGames.size() && i < gameSigns.size(); i++) {
            GameStatus game = activeGames.get(i);
            Sign sign = gameSigns.get(i);

            sign.setLine(0, ChatColor.BLUE + "Game");
            sign.setLine(1, ChatColor.GOLD + game.getGameName());

            if (game.getState() == GameState.OPEN) {
                sign.setLine(2, ChatColor.GREEN + game.getState().toString());
            } else {
                sign.setLine(2, ChatColor.RED + game.getState().toString());
            }

            sign.setLine(3, ChatColor.YELLOW + String.valueOf(game.getPlayerCount()) + " players");
            sign.update();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getState() instanceof Sign sign) {

            for (GameStatus game : activeGames) {
                if (sign.getLine(1).contains(game.getGameName())) {
                    if (game.getState() == GameState.OPEN) {
                        Player player = event.getPlayer();
                        CookiePlayer cookiePlayer = new CookiePlayer(player);
                        joinAvailableGame(cookiePlayer);
                    } else {
                        event.getPlayer().sendMessage(ChatColor.RED + "This game is not available.");
                    }
                    break;
                }
            }
        }

    }

    public void joinAvailableGame(CookiePlayer player) {
        for (GameStatus game : activeGames) {
            if (game.addPlayerToAvailableTeam(player)) {
                player.setState(PlayerState.IN_GAME);
                return;
            }
        }
        player.getPlayer().sendMessage("No available games. Please wait.");
    }

    public void addGameSign(Sign sign) {
        gameSigns.add(sign);
    }

    public void addGameNpc(Entity npc) {
        // TODO: add NPCs
    }

    public static void teleportPlayerToLobby(CookiePlayer cookiePlayer) {
        Player player = cookiePlayer.getPlayer();
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            // if player is in a game, remove them from the game
            if (cookiePlayer.getState() == PlayerState.IN_GAME) {
                GameManager.getGameOfPlayer(cookiePlayer).removePlayer(cookiePlayer);
            }

            Location lobbySpawnLocation = lobbyWorld.getSpawnLocation();
            player.teleport(lobbySpawnLocation);
            CookieDough.getInstance().getLogger().info(player.getName() + " has been teleported to the lobby.");
        } else {
            CookieDough.getInstance().getLogger().severe("Lobby world 'lobby' is not loaded!");
        }
    }
}