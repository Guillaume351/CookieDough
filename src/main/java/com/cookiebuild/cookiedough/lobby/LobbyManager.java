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
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

public class LobbyManager implements Listener {
    private final JavaPlugin plugin;
    private final List<GameNPC> gameNpcs = new ArrayList<>();
    private final StatueManager statueManager;

    // Singleton
    private static LobbyManager instance;

    public LobbyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;

        // Initialize StatueManager
        PlayerStatsService playerStatsService = CookieDough.getPlayerStatsService();
        this.statueManager = new StatueManager(playerStatsService, plugin);

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
        // This functionality is currently disabled as it depends on a fixed list of
        // signs.
        // A more dynamic system should be implemented in the future.
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
            // Set gamemode to adventyre
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

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getState() instanceof Sign) {
            // Sign-based game joining is temporarily disabled.
            // Players should join via NPCs.
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
}