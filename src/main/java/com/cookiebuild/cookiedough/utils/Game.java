package com.cookiebuild.cookiedough.utils;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public abstract class Game {

    public static final int GAME_OPEN = 0;
    public static final int GAME_STARTING = 1;
    public static final int GAME_RUNNING = 2;
    public static final int GAME_FINISHED = 3;

    private static final int START_DELAY_SECONDS = 60;

    private final List<CookiePlayer> players;

    private int gameNumber;
    private int time;
    private int startTimer;
    private int state;
    private boolean isFilling;

    private int capacity = 8;

    public Game(int gameNumber) {
        this.gameNumber = gameNumber;
        this.players = new ArrayList<>();
        this.resetGame();
    }

    public void addPlayer(CookiePlayer player) {
        if (!players.contains(player)) {
            players.add(player);
            player.setInGame(true); // Set player as in game
        } else {
            // Log error if player is already in the game
            CookieDough.getInstance().getLogger().log(Level.SEVERE, player.getPlayer().getName() + " was added multiple times to the game!");
        }
    }

    public void removePlayer(CookiePlayer player) {
        if (players.remove(player)) {
            player.setInGame(false);
            if (startTimer > 0 && players.size() < 2) {
                startTimer = 0;
            }
        }
    }

    public List<CookiePlayer> getPlayers() {
        return new ArrayList<>(players); // Return a copy to avoid external modification
    }

    public void tick() {
        if (state == GAME_OPEN) {
            if (players.size() >= 2) {
                startTimer++;
                if (startTimer >= START_DELAY_SECONDS) {
                    startGame();
                    startTimer = 0;
                }
            } else {
                startTimer = 0; // Reset timer if players are less than 2
            }
        }
    }

    public void startGame() {
        state = GAME_RUNNING;
        isFilling = false;
        for (CookiePlayer player : players) {
            teleportToGame(player);
            // send localized message
            player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage("game.start", player.getPlayer().locale()));
        }
    }

    protected abstract void teleportToGame(CookiePlayer player);

    public boolean hasStarted() {
        return state == GAME_RUNNING;
    }

    public void resetGame() {
        state = GAME_OPEN;
        time = 0;
        startTimer = 0;
        players.clear();
    }

    public abstract boolean isGameEnded();

    // Getters and Setters for encapsulation
    public int getGameNumber() {
        return gameNumber;
    }

    public void setGameNumber(int gameNumber) {
        this.gameNumber = gameNumber;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getStartTimer() {
        return startTimer;
    }

    public void setStartTimer(int startTimer) {
        this.startTimer = startTimer;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean isFilling() {
        return isFilling;
    }

    public void setFilling(boolean filling) {
        isFilling = filling;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}