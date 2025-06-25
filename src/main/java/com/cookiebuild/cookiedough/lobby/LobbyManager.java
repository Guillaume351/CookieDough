package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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

public class LobbyManager implements Listener {
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

        Bukkit.getPluginManager().registerEvents(this, plugin);

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
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshSigns();
            }
        }.runTaskTimer(plugin, 0, 20); // Refresh every second (20 ticks)
    }

    private void refreshSigns() {
        ArrayList<Game> games = GameManager.getGames();
        for (int i = 0; i < games.size() && i < gameSigns.size(); i++) {
            Game game = games.get(i);
            Sign sign = gameSigns.get(i);

            updateSignContent(sign, game);
        }
    }

    private void updateSignContent(Sign sign, Game game) {
        sign.setLine(0, ChatColor.BLUE + "Game");
        sign.setLine(1, ChatColor.GOLD + game.getGameName());
        sign.setLine(2,
                game.getState() == GameState.OPEN ? ChatColor.GREEN + game.getState().toString()
                        : ChatColor.RED + game.getState().toString());
        sign.setLine(3, ChatColor.YELLOW + String.valueOf(game.getPlayerCount()) + " players");

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

    public void addGameSign(Sign sign) {
        gameSigns.add(sign);
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
            // Set gamemode to adventure
            player.setGameMode(GameMode.ADVENTURE);

            // Empty inventory
            player.getInventory().clear();

            // Remove arrows in the player's body
            player.setArrowsInBody(0);

            // Reset player display name to original name (remove kit display)
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());

            // if player is in a game, remove them from the game
            if (cookiePlayer.getState() == PlayerState.IN_GAME) {
                Game game = GameManager.getGameOfPlayer(cookiePlayer);
                if (game != null) {
                    game.removePlayer(cookiePlayer);
                } else {
                    CookieDough.getInstance().getLogger().severe("Player " + cookiePlayer.getPlayer().getName()
                            + " is in a game but no game was found.");
                }
            }
            cookiePlayer.setState(PlayerState.LOBBY);

            Location lobbySpawnLocation = lobbyWorld.getSpawnLocation();
            player.teleport(lobbySpawnLocation);
            CookieDough.getInstance().getLogger().info(player.getName() + " has been teleported to the lobby.");

            // Initialize LobbyScoreboard
            LobbyScoreboard scoreboard = new LobbyScoreboard(player);
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
        if (clickedBlock != null && clickedBlock.getState() instanceof Sign sign) {
            for (GameStatus game : GameManager.getGames()) {
                if (sign.getLine(1).contains(game.getGameName())) {
                    if (game.getState() == GameState.OPEN) {
                        Player player = event.getPlayer();
                        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
                        if (!cookiePlayer.getState().equals(PlayerState.IN_GAME)) {
                            joinAvailableGame(cookiePlayer);
                        }
                    } else {
                        event.getPlayer().sendMessage(ChatColor.RED + "This game is not available.");
                    }
                    break;
                }
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
}