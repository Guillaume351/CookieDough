package com.cookiebuild.cookiedough.game;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public abstract class Game implements GameStatus {

    private final String gameName;


    protected static final int START_DELAY_SECONDS = 10;

    private final List<CookiePlayer> players;

    private final UUID gameId;
    private int time;
    private int startTimer;
    private GameState state;
    private boolean isFilling;

    private int capacity = 8;

    public Game(String gameName) {
        this.gameName = gameName;
        this.gameId = UUID.randomUUID();
        this.players = new ArrayList<>();
        this.resetGame();
    }

    public void addPlayer(CookiePlayer player) {
        if (!players.contains(player)) {
            players.add(player);
            player.setState(PlayerState.IN_GAME); // Set player as in game
        } else {
            // Log error if player is already in the game
            CookieDough.getInstance().getLogger().log(Level.SEVERE, player.getPlayer().getName() + " was added multiple times to the game!");
        }
    }

    public void removePlayer(CookiePlayer player) {
        if (players.remove(player)) {
            if (startTimer > 0 && players.size() < 2) {
                startTimer = 0;
            }
        }
    }

    public List<CookiePlayer> getPlayers() {
        return new ArrayList<>(players); // Return a copy to avoid external modification
    }

    public void tick() {
        if (state == GameState.OPEN) {
            if (players.size() >= 2) {
                startTimer++;
                if (startTimer >= START_DELAY_SECONDS) { // TODO: make this configurable
                    startGame();
                    startTimer = 0;
                }
            } else {
                startTimer = 0; // Reset timer if players are less than 2
            }
        }
    }

    public void startGame() {
        state = GameState.RUNNING;
        isFilling = false;
        for (CookiePlayer player : players) {
            teleportToGame(player);
            // send localized message
            player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage("game.started", player.getPlayer().locale()));
        }

        registerANewGame();
    }

    public abstract void registerANewGame();

    protected abstract void teleportToGame(CookiePlayer player);

    public boolean hasStarted() {
        return state == GameState.RUNNING;
    }

    public void resetGame() {
        state = GameState.OPEN;
        time = 0;
        startTimer = 0;
        players.clear();
    }

    public abstract boolean isGameEnded();

    // Getters and Setters for encapsulation
    public UUID getGameId() {
        return gameId;
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

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
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

    public String getGameName() {
        return gameName;
    }
}