package com.cookiebuild.cookiedough.player;

import org.bukkit.entity.Player;

import java.util.ArrayList;

public class PlayerManager {
    static ArrayList<CookiePlayer> players = new ArrayList<>();

    public static void addPlayer(CookiePlayer player) {
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
}
