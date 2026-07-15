package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Sound;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public abstract class Game implements GameStatus {

    private final String gameName;

    protected int START_DELAY_SECONDS = 30;
    protected int QUICK_START_DELAY_SECONDS = 10;

    private final List<CookiePlayer> players;
    private final Map<UUID, Long> queueEnteredAt = new HashMap<>();

    private final UUID gameId;
    private int time;
    private int startTimer;
    private GameState state;
    private boolean isFilling;
    private boolean replacementRegistrationRequested;
    private int replacementRegistrationAttemptTick = Integer.MIN_VALUE;
    protected boolean inQuickStart = false;

    private int capacity = 8;
    private int minimumPlayers = 2;

    public Game(String gameName) {
        this.gameName = gameName;
        this.gameId = UUID.randomUUID();
        this.players = new ArrayList<>();
        this.resetGame();
    }

    public synchronized boolean addPlayer(CookiePlayer player) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline()) {
            return false;
        }
        if (!PlayerWrapperListener.isPlayerDataReady(player.getPlayer().getUniqueId())) {
            player.getPlayer().sendMessage(ChatColor.YELLOW + LocaleManager
                    .getMessage("player.data_loading", player.getPlayer().locale()));
            return false;
        }
        if (state != GameState.OPEN) {
            player.getPlayer().sendMessage(
                    ChatColor.RED + LocaleManager.getMessage("game.already_started", player.getPlayer().locale()));
            return false;
        }
        if (players.contains(player) || player.getState() == PlayerState.IN_GAME) {
            player.getPlayer().sendMessage(ChatColor.YELLOW + LocaleManager
                    .getMessage("game.already_joined", player.getPlayer().locale()));
            return false;
        }
        if (players.size() >= capacity) {
            player.getPlayer().sendMessage(ChatColor.RED + LocaleManager
                    .getMessage("game.full", player.getPlayer().locale()));
            return false;
        }

        CookieDough.getInstance().getPracticeManager().stop(player.getPlayer(), false);
        players.add(player);
        queueEnteredAt.put(player.getPlayer().getUniqueId(), System.currentTimeMillis());
        player.setState(PlayerState.IN_GAME);
        getPlayers().forEach(p -> p.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager
                .getMessage("player.joined.game", p.getPlayer().locale(), player.getPlayer().getName())));
        PlayerWrapperListener.hideLobbyScoreboard(player.getPlayer());
        FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.QUEUE_JOINED,
                "game=" + gameName + " players=" + players.size());
        return true;
    }

    /**
     * Restores a module-owned participant after a short transport disconnect.
     * The module must validate the player's reserved slot and reconnect window
     * before calling this method; normal admission remains restricted to OPEN.
     */
    protected synchronized boolean restorePlayerAfterReconnect(CookiePlayer player) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline()
                || state != GameState.RUNNING) {
            return false;
        }
        UUID playerId = player.getPlayer().getUniqueId();
        players.removeIf(existing -> existing.getPlayer().getUniqueId().equals(playerId));
        if (players.size() >= capacity) {
            return false;
        }
        players.add(player);
        player.setState(PlayerState.IN_GAME);
        PlayerWrapperListener.hideLobbyScoreboard(player.getPlayer());
        return true;
    }

    public void removePlayer(CookiePlayer player) {
        removePlayer(player, "left_queue");
    }

    public synchronized void removePlayer(CookiePlayer player, String reason) {
        boolean playerWasRemoved = players.remove(player);
        if (playerWasRemoved) {
            Long queuedAt = queueEnteredAt.remove(player.getPlayer().getUniqueId());
            if (queuedAt != null) {
                FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.QUEUE_LEFT,
                        "game=" + gameName + " wait_seconds="
                                + Math.max(0L, (System.currentTimeMillis() - queuedAt) / 1000)
                                + " reason=" + reason);
            }
            onPlayerRemoved(player);
            // A player that is no longer owned by any game must not keep a game-only
            // state. In particular, eliminated spectators are removed before the lobby
            // teleport during cleanup; leaving SPECTATING here made the lobby correctly
            // report them as apparent orphans even though the removal was intentional.
            if (player.getPlayer().isOnline()
                    && (player.getState() == PlayerState.IN_GAME
                            || player.getState() == PlayerState.SPECTATING)) {
                player.setState(PlayerState.LOBBY);
            }
            if (startTimer > 0 && players.size() < minimumPlayers) {
                startTimer = 0;
                inQuickStart = false;
            }

            // Only send notification if player was actually removed and game is not
            // finished
            if (this.state != GameState.FINISHED) {
                getPlayers().forEach(p -> p.getPlayer().sendMessage(ChatColor.RED
                        + LocaleManager.getMessage("player.left.game", p.getPlayer().locale(),
                                player.getPlayer().getName())));
            }
        }
    }

    /** Game modules can clean up team/spectator state when a player leaves. */
    protected void onPlayerRemoved(CookiePlayer player) {
        // Optional hook.
    }

    public List<CookiePlayer> getPlayers() {
        return new ArrayList<>(players); // Return a copy to avoid external modification
    }

    public void tick() {
        time++;
        if (state == GameState.OPEN) {
            int availablePlayers = GameManager.getAvailablePlayerCount();
            if (players.size() >= minimumPlayers) {
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
                // Prepare the spare arena at the beginning of the countdown instead of
                // immediately after players are teleported into a live match. World
                // creation must remain on the server thread, but moving it out of the
                // first live-game ticks prevents replacement-map preparation from
                // freezing combat as the match begins.
                if (startTimer == 1) {
                    ensureReplacementGame();
                }
                int delay = inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS;

                if (startTimer >= delay) {
                    startGame();
                    startTimer = 0;
                    inQuickStart = false;
                }
            } else {
                startTimer = 0; // Reset timer if players are less than 2
                inQuickStart = false;
                if (!players.isEmpty() && time % 15 == 0) {
                    CookiePlayer waiting = players.getFirst();
                    long queuedAt = queueEnteredAt.getOrDefault(waiting.getPlayer().getUniqueId(),
                            System.currentTimeMillis());
                    long waitingSeconds = Math.max(0L, (System.currentTimeMillis() - queuedAt) / 1000);
                    waiting.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                            "Waiting " + waitingSeconds + "s · "
                                    + (minimumPlayers - players.size()) + " more player"
                                    + (minimumPlayers - players.size() == 1 ? "" : "s") + " needed",
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                }
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

        for (CookiePlayer player : players) {
            player.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                    "Starting in " + Math.max(0, remainingTime) + "s · " + players.size() + "/" + capacity,
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }

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
            Long queuedAt = queueEnteredAt.remove(player.getPlayer().getUniqueId());
            long waitSeconds = queuedAt == null ? 0L
                    : Math.max(0L, (System.currentTimeMillis() - queuedAt) / 1000);
            FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.QUEUE_LEFT,
                    "game=" + gameName + " wait_seconds=" + waitSeconds + " reason=match_started");
            teleportToGame(player);
            // send localized message
            player.getPlayer().sendMessage(
                    ChatColor.GREEN + LocaleManager.getMessage("game.started", player.getPlayer().locale()));
            player.getPlayer().playSound(player.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.MATCH_STARTED, "game=" + gameName);
        }

        ensureReplacementGame();
    }

    private void ensureReplacementGame() {
        if (GameManager.hasOtherOpenGame(this)) {
            replacementRegistrationRequested = true;
            return;
        }
        // Some modules schedule the actual map registration for the next server
        // tick. If that deferred preparation fails, retry after a short bounded
        // interval instead of permanently believing a spare exists.
        if (replacementRegistrationRequested && state == GameState.OPEN
                && time - replacementRegistrationAttemptTick < 5) {
            return;
        }
        replacementRegistrationRequested = true;
        replacementRegistrationAttemptTick = time;
        try {
            registerANewGame();
        } catch (RuntimeException error) {
            replacementRegistrationRequested = false;
            CookieDough.getInstance().getLogger().severe(
                    "Could not prepare replacement " + gameName + " game: " + error.getMessage());
        }
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
        queueEnteredAt.clear();
        inQuickStart = false;
        replacementRegistrationRequested = false;
        replacementRegistrationAttemptTick = Integer.MIN_VALUE;
    }

    public abstract boolean isGameEnded();

    /** Call once when the result is known to expose a consistent replay action. */
    public void offerReplay() {
        for (CookiePlayer cookiePlayer : getPlayers()) {
            if (!cookiePlayer.getPlayer().isOnline()) {
                continue;
            }
            FunnelTelemetry.record(cookiePlayer.getPlayer(), FunnelTelemetry.Event.MATCH_COMPLETED,
                    "game=" + gameName);
            cookiePlayer.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("Play again",
                            net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/quickplay replay"))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.Component.text("Join the next available match"))));
            cookiePlayer.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("Share feedback",
                            net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/feedback "))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.Component.text("Tell us what would make the next match better"))));
        }
    }

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
        if (capacity < 1 || capacity < minimumPlayers) {
            throw new IllegalArgumentException("Capacity must be at least the minimum player count");
        }
        this.capacity = capacity;
    }

    public int getMinimumPlayers() {
        return minimumPlayers;
    }

    public void setMinimumPlayers(int minimumPlayers) {
        if (minimumPlayers < 1 || minimumPlayers > capacity) {
            throw new IllegalArgumentException("Minimum players must be between 1 and capacity");
        }
        this.minimumPlayers = minimumPlayers;
    }

    public String getGameName() {
        return gameName;
    }

    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Maximum number of party members that this game can admit as one group.
     * Games with smaller teams than their total match capacity should override
     * this value.
     */
    public int getMaxAdmissiblePartySize() {
        return capacity;
    }

    /**
     * Returns a player-facing reason when a party cannot currently be admitted,
     * or {@code null} when it is compatible with this game.
     */
    public String getPartyAdmissionProblem(int partySize) {
        if (partySize <= 0) {
            return "Your party has no available players.";
        }
        int groupCapacity = getMaxAdmissiblePartySize();
        if (partySize > groupCapacity) {
            return gameName + " supports parties of up to " + groupCapacity
                    + " players; your party has " + partySize + ".";
        }
        if (capacity - getPlayerCount() < partySize) {
            return gameName + " does not currently have room for all " + partySize
                    + " party members.";
        }
        return null;
    }

    public boolean addPlayerToAvailableTeam(CookiePlayer player) {
        return addPlayer(player);
    }
}
