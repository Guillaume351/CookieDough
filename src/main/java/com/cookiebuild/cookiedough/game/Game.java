package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Sound;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public abstract class Game implements GameStatus {

    private final String gameName;

    protected int START_DELAY_SECONDS = 30;
    protected int QUICK_START_DELAY_SECONDS = 10;

    private final List<CookiePlayer> players;

    private final UUID gameId;
    private int time;
    private int startTimer;
    private GameState state;
    private boolean isFilling;
    private boolean inQuickStart = false;

    private int capacity = 8;

    public Game(String gameName) {
        this.gameName = gameName;
        this.gameId = UUID.randomUUID();
        this.players = new ArrayList<>();
        this.resetGame();
    }

    public boolean addPlayer(CookiePlayer player) {
        // if game is running, do not allow players to join
        if (state == GameState.RUNNING) {
            player.getPlayer().sendMessage(
                    ChatColor.RED + LocaleManager.getMessage("game.already_started", player.getPlayer().locale()));
            return false;
        }

        if (!players.contains(player)) {
            players.add(player);
            player.setState(PlayerState.IN_GAME); // Set player as in game

            // send localized message to in game players
            getPlayers().forEach(p -> p.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager
                    .getMessage("player.joined.game", p.getPlayer().locale(), player.getPlayer().getName())));

        } else {
            // Log error if player is already in the game
            CookieDough.getInstance().getLogger().log(Level.SEVERE,
                    player.getPlayer().getName() + " was added multiple times to the game!");
        }
        return true;
    }

    public void removePlayer(CookiePlayer player) {
        if (players.remove(player)) {
            if (startTimer > 0 && players.size() < 2) {
                startTimer = 0;
                inQuickStart = false;
            }
        }
        getPlayers().forEach(p -> p.getPlayer().sendMessage(ChatColor.RED
                + LocaleManager.getMessage("player.left.game", p.getPlayer().locale(), player.getPlayer().getName())));
    }

    public List<CookiePlayer> getPlayers() {
        return new ArrayList<>(players); // Return a copy to avoid external modification
    }

    public void tick() {
        if (state == GameState.OPEN) {
            int availablePlayers = GameManager.getAvailablePlayerCount();
            if (players.size() >= 2) {
                boolean quickStartCondition = availablePlayers == 0 || players.size() == capacity;

                if (quickStartCondition && !inQuickStart) {
                    // Transitioning to quick start
                    inQuickStart = true;
                    startTimer = 0; // Reset timer to start quick start countdown
                } else if (!quickStartCondition) {
                    // Not in quick start (or transitioning out)
                    inQuickStart = false;
                }

                startTimer++;
                int delay = inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS;

                if (startTimer >= delay) {
                    startGame();
                    startTimer = 0;
                    inQuickStart = false;
                }
            } else {
                startTimer = 0; // Reset timer if players are less than 2
                inQuickStart = false;
            }

            // Notify players of the countdown
            if (startTimer > 0) {
                notifyCountdown();
            }
        }
    }

    private void notifyCountdown() {
        int delay = inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS;
        int remainingTime = delay - startTimer;

        if (remainingTime <= 5 && remainingTime > 0) {
            for (CookiePlayer player : players) {
                player.getPlayer().sendMessage(ChatColor.YELLOW +
                        LocaleManager.getMessage("game.countdown", player.getPlayer().locale(),
                                String.valueOf(remainingTime)));
                player.getPlayer().playSound(player.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            }
        }
    }

    public void startGame() {
        state = GameState.RUNNING;
        isFilling = false;
        for (CookiePlayer player : players) {
            teleportToGame(player);
            // send localized message
            player.getPlayer().sendMessage(
                    ChatColor.GREEN + LocaleManager.getMessage("game.started", player.getPlayer().locale()));
            player.getPlayer().playSound(player.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
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
        inQuickStart = false;
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

    public int getPlayerCount() {
        return players.size();
    }

    public boolean addPlayerToAvailableTeam(CookiePlayer player) {
        return addPlayer(player);
    }
}
