package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;

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

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

public class LobbyManager implements Listener {
    private final JavaPlugin plugin;
    private final List<Location> gameSigns;
    private final List<GameNPC> gameNpcs = new ArrayList<>();
    private final StatueManager statueManager;

    // Singleton
    private static LobbyManager instance;

    public LobbyManager(JavaPlugin plugin, List<Location> gameSigns) {
        this.plugin = plugin;
        this.gameSigns = gameSigns;
        instance = this;

        // Initialize StatueManager
        PlayerStatsService playerStatsService = CookieDough.getPlayerStatsService();
        this.statueManager = new StatueManager(playerStatsService, plugin);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        startSignRefreshTask();

        // Get lobby world, remove all entities
        World lobbyWorld = Bukkit.getWorld("lobby");
        for (Entity entity : lobbyWorld.getEntities()) {
            entity.remove();
        }

        // enable NPC listeners
        Bukkit.getPluginManager().registerEvents(new NPCListener(playerStatsService), plugin);
    }

    public static LobbyManager getInstance() {
        return instance;
    }

    private void startSignRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshSigns();
            }
        }.runTaskTimer(plugin, 0, 10); // Refresh every 0.5 seconds (10 ticks)
    }

    private void refreshSigns() {
        ArrayList<Game> games = GameManager.getGames(); // Get all games
        for (int i = 0; i < gameSigns.size(); i++) {
            Location signLocation = gameSigns.get(i);
            Sign sign = (Sign) signLocation.getBlock().getState();
            if (i < games.size()) {
                GameStatus game = games.get(i);
                updateSignContent(sign, game);
            } else {
                clearSignContent(sign);
            }
        }
    }

    private void updateSignContent(Sign sign, GameStatus game) {
        sign.setLine(0, ChatColor.AQUA + "" + ChatColor.BOLD + "Game");
        sign.setLine(1, ChatColor.GOLD + "" + ChatColor.BOLD + game.getGameName());
        sign.setLine(2,
                game.getState() == GameState.OPEN ? ChatColor.GREEN + "" + ChatColor.BOLD + game.getState().toString()
                        : ChatColor.RED + "" + ChatColor.BOLD + game.getState().toString());
        sign.setLine(3, ChatColor.YELLOW + "" + ChatColor.BOLD + game.getPlayerCount() + " players");

        sign.setWaxed(true);
        sign.setGlowingText(true);

        sign.update(true); // Force update
    }

    private void clearSignContent(Sign sign) {
        for (int i = 0; i < 4; i++) {
            sign.setLine(i, "");
        }
        sign.update(true); // Force update
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
        Player player = cookiePlayer.getPlayer();
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld != null) {
            // Remove arrows in the player's body
            player.setArrowsInBody(0);

            // if player is in a game, remove them from the game
            if (cookiePlayer.getState() == PlayerState.IN_GAME) {
                GameManager.getGameOfPlayer(cookiePlayer).removePlayer(cookiePlayer);
            }
            cookiePlayer.setState(PlayerState.LOBBY);

            Location lobbySpawnLocation = lobbyWorld.getSpawnLocation();
            player.teleport(lobbySpawnLocation);
            CookieDough.getInstance().getLogger().info(player.getName() + " has been teleported to the lobby.");

            // Initialize LobbyScoreboard
            LobbyScoreboard scoreboard = new LobbyScoreboard(player, CookieDough.getPlayerStatsService());
            scoreboard.show();
        } else {
            CookieDough.getInstance().getLogger().severe("Lobby world 'lobby' is not loaded!");
        }
    }

    public void joinAvailableGame(CookiePlayer player) {
        for (GameStatus game : GameManager.getGames()) {
            if (game.addPlayerToAvailableTeam(player)) {
                return;
            }
        }
        player.getPlayer().sendMessage("No available games. Please wait.");
    }

    public void addGameNpc(Entity npc) {
        // TODO: add NPCs
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getState() instanceof Sign clickedSign) {
            GameStatus clickedGame = findGameForSign(clickedSign);
            if (clickedGame != null) {
                handleGameSignClick(event.getPlayer(), clickedGame);
            }
        }
    }

    private GameStatus findGameForSign(Sign clickedSign) {
        for (GameStatus game : GameManager.getGames()) {
            if (clickedSign.getLine(1).equals(ChatColor.GOLD + "" + ChatColor.BOLD + game.getGameName())) {
                return game;
            }
        }
        return null;
    }

    private void handleGameSignClick(Player player, GameStatus game) {
        if (game.getState() == GameState.OPEN) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            if (!cookiePlayer.getState().equals(PlayerState.IN_GAME)) {
                game.addPlayerToAvailableTeam(cookiePlayer);
            }
        } else {
            player.sendMessage(ChatColor.RED + "This game is not available.");
        }
    }

    public List<GameNPC> getGameNpcs() {
        return gameNpcs;
    }

    public StatueManager getStatueManager() {
        return statueManager;
    }
}