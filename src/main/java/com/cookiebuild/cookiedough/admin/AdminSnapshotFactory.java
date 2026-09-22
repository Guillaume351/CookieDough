package com.cookiebuild.cookiedough.admin;

import org.bukkit.Bukkit;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Captured only on the Paper main thread. */
final class AdminSnapshotFactory {
    private final ObjectMapper mapper;
    private final AdminServerState serverState;
    private final AdminRabbitClient rabbit;

    AdminSnapshotFactory(ObjectMapper mapper, AdminServerState serverState, AdminRabbitClient rabbit) {
        this.mapper = mapper;
        this.serverState = serverState;
        this.rabbit = rabbit;
    }

    ObjectNode capture() {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("connected", rabbit.connected());
        snapshot.put("maintenance", serverState.maintenance());
        snapshot.put("draining", serverState.draining());
        snapshot.put("online_players", Bukkit.getOnlinePlayers().size());
        snapshot.put("max_players", Bukkit.getMaxPlayers());

        ArrayNode players = snapshot.putArray("players");
        for (CookiePlayer cookiePlayer : PlayerManager.getPlayers()) {
            var player = cookiePlayer.getPlayer();
            if (!player.isOnline()) continue;
            ObjectNode node = players.addObject();
            node.put("id", player.getUniqueId().toString());
            node.put("name", player.getName());
            node.put("state", cookiePlayer.getState().name().toLowerCase(java.util.Locale.ROOT));
            node.put("world", player.getWorld().getName());
            node.put("ping", player.getPing());
            Game game = GameManager.getGameOfPlayer(cookiePlayer);
            if (game == null) node.putNull("game_id");
            else node.put("game_id", game.getGameId().toString());
        }

        ArrayNode games = snapshot.putArray("games");
        for (Game game : GameManager.getGames()) {
            ObjectNode node = games.addObject();
            node.put("id", game.getGameId().toString());
            node.put("name", game.getGameName());
            node.put("state", game.getState().name());
            node.put("admissions_open", game.isAdmissionsOpen());
            node.put("players", game.getPlayerCount());
            node.put("capacity", game.getCapacity());
            node.put("minimum_players", game.getMinimumPlayers());
            node.put("elapsed_seconds", game.getTime());
            node.put("countdown_seconds", game.getCountdownSeconds());
        }
        return snapshot;
    }
}
