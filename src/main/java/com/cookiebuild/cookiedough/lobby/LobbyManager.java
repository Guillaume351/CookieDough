package com.cookiebuild.cookiedough.lobby;

import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class LobbyManager implements Listener {
    private final JavaPlugin plugin;
    private final List<GameStatus> activeGames = new ArrayList<>(); // Initialize the list
    private final List<Sign> gameSigns;
    //private final List<Entity> gameNpcs; // TODO: add NPCs

    public LobbyManager(JavaPlugin plugin, List<Sign> gameSigns) {
        this.plugin = plugin;
        this.gameSigns = gameSigns;


        Bukkit.getPluginManager().registerEvents(this, plugin);
        startSignRefreshTask();
    }

    public void registerGame(GameStatus game) {
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

            sign.setLine(0, "Game");
            sign.setLine(1, "Game " + game.getGameNumber());
            sign.setLine(2, game.isGameEnded() ? "Finished" : "In Progress");
            sign.setLine(3, game.getPlayerCount() + " players");
            sign.update();
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
//        if (gameNpcs.contains(event.getRightClicked())) {
//            Player player = event.getPlayer();
//            CookiePlayer cookiePlayer = new CookiePlayer(player);
//            joinAvailableGame(cookiePlayer);
//        }
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
}