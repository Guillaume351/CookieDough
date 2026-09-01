package com.cookiebuild.cookiedough.player;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerManager {
    private static final List<CookiePlayer> players = new CopyOnWriteArrayList<>();

    public static void addPlayer(CookiePlayer player) {
        players.removeIf(existing -> existing.getPlayer().getUniqueId().equals(player.getPlayer().getUniqueId()));
        players.add(player);
    }

    public static void removePlayer(CookiePlayer player) {
        players.remove(player);
    }

    public static CookiePlayer getPlayer(Player player) {
        for (CookiePlayer cookiePlayer : players) {
            if (cookiePlayer.getPlayer().equals(player)) {
                return cookiePlayer;
            }
        }
        return null;
    }

    public static ArrayList<CookiePlayer> getPlayers() {
        return new ArrayList<>(players);
    }
}
