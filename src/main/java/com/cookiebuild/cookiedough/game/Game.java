package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public abstract class Game implements GameStatus {

    private static final int QUEUE_TELEMETRY_INTERVAL_SECONDS = 15;

    private final String gameName;

    protected int START_DELAY_SECONDS = 30;
    protected int QUICK_START_DELAY_SECONDS = 10;

    private final List<CookiePlayer> players;
    private final Map<UUID, CookiePlayer> spectators = new HashMap<>();
    private final Map<UUID, Long> queueEnteredAt = new HashMap<>();

    private final UUID gameId;
    private int time;
    private int startTimer;
    private GameState state;
    private boolean isFilling;
    private boolean admissionsOpen;
    private boolean replacementRegistrationRequested;
    private int replacementRegistrationAttemptTick = Integer.MIN_VALUE;
    protected boolean inQuickStart = false;

    private int capacity = 8;
    private int minimumPlayers = 2;

    public Game(String gameName) {
        this(gameName, UUID.randomUUID());
    }

    protected Game(String gameName, UUID gameId) {
        this.gameName = gameName;
        this.gameId = java.util.Objects.requireNonNull(gameId, "gameId");
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
        if (state != GameState.OPEN || !admissionsOpen) {
            player.getPlayer().sendMessage(
                    ChatColor.RED + LocaleManager.getMessage("game.already_started", player.getPlayer().locale()));
            return false;
        }
        if (players.contains(player) || player.getState() != PlayerState.LOBBY) {
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
        player.setState(PlayerState.QUEUED);
        getPlayers().forEach(p -> p.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager
                .getMessage("player.joined.game", p.getPlayer().locale(), player.getPlayer().getName())));
        PlayerWrapperListener.hideLobbyScoreboard(player.getPlayer());
        FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.QUEUE_JOINED,
                "game=" + gameName + " players=" + players.size());
        emitQueueState();
        if (CookieDough.getInstance() != null && CookieDough.getInstance().getPlayerHubMenu() != null) {
            CookieDough.getInstance().getPlayerHubMenu().enterQueue(player.getPlayer(), gameName);
        }
        return true;
    }

    /**
     * Restores a module-owned participant after a short transport disconnect.
     * The module must validate the player's reserved slot and reconnect window
     * before calling this method; normal admission remains restricted to OPEN.
     */
    protected synchronized boolean restorePlayerAfterReconnect(CookiePlayer player) {
        if (!canRestorePlayerAfterReconnect(player)) return false;
        UUID playerId = player.getPlayer().getUniqueId();
        players.removeIf(existing -> existing.getPlayer().getUniqueId().equals(playerId));
        players.add(player);
        player.setState(PlayerState.IN_GAME);
        PlayerWrapperListener.hideLobbyScoreboard(player.getPlayer());
        return true;
    }

    /** Read-only roster preflight used before a custom reconnect moves the player. */
    protected synchronized boolean canRestorePlayerAfterReconnect(CookiePlayer player) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline()
                || state != GameState.RUNNING) return false;
        UUID playerId = player.getPlayer().getUniqueId();
        long otherPlayers = players.stream().filter(existing ->
                !existing.getPlayer().getUniqueId().equals(playerId)).count();
        return otherPlayers < capacity;
    }

    /** Admits a read-only viewer without consuming a participant slot. */
    public synchronized boolean addSpectator(CookiePlayer player) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline()
                || state != GameState.RUNNING || !supportsSpectating()
                || player.getState() != PlayerState.LOBBY
                || GameManager.getGameOfPlayer(player) != null) {
            return false;
        }
        UUID playerId = player.getPlayer().getUniqueId();
        if (spectators.containsKey(playerId)) return true;
        if (!teleportToSpectator(player)) return false;
        CookieDough.getInstance().getPracticeManager().stop(player.getPlayer(), false);
        spectators.put(playerId, player);
        player.setState(PlayerState.SPECTATING);
        PlayerWrapperListener.hideLobbyScoreboard(player.getPlayer());
        if (CookieDough.getInstance().getPlayerHubMenu() != null) {
            CookieDough.getInstance().getPlayerHubMenu().enterSpectator(player.getPlayer());
        }
        player.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                "game.spectate.joined", player.getPlayer().locale(), gameName));
        FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.SPECTATOR_JOINED,
                "game=" + gameName);
        return true;
    }

    public void removePlayer(CookiePlayer player) {
        removePlayer(player, "left_queue");
    }

    public synchronized void removePlayer(CookiePlayer player, String reason) {
        if (player != null && player.getPlayer() != null
                && spectators.remove(player.getPlayer().getUniqueId()) != null) {
            onSpectatorRemoved(player);
            if (CookieDough.getInstance() != null && CookieDough.getInstance().getPlayerHubMenu() != null) {
                CookieDough.getInstance().getPlayerHubMenu().leaveSpectator(player.getPlayer());
            }
            if (player.getPlayer().isOnline() && player.getState() == PlayerState.SPECTATING) {
                player.setState(PlayerState.LOBBY);
            }
            return;
        }
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
            if (CookieDough.getInstance() != null && CookieDough.getInstance().getPlayerHubMenu() != null) {
                CookieDough.getInstance().getPlayerHubMenu().leaveQueue(player.getPlayer());
            }
            // A player that is no longer owned by any game must not keep a game-only
            // state. In particular, eliminated spectators are removed before the lobby
            // teleport during cleanup; leaving SPECTATING here made the lobby correctly
            // report them as apparent orphans even though the removal was intentional.
            if (player.getPlayer().isOnline()
                    && (player.getState() == PlayerState.QUEUED
                            || player.getState() == PlayerState.IN_GAME
                            || player.getState() == PlayerState.SPECTATING)) {
                player.setState(PlayerState.LOBBY);
            }
            if (startTimer > 0 && players.size() < minimumPlayers) {
                startTimer = 0;
                inQuickStart = false;
            }
            emitQueueState();

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

    /** Game modules can clear viewer-only state when a spectator leaves. */
    protected void onSpectatorRemoved(CookiePlayer player) {
        // Optional hook.
    }

    public List<CookiePlayer> getPlayers() {
        return new ArrayList<>(players); // Return a copy to avoid external modification
    }

    public synchronized List<CookiePlayer> getSpectators() {
        return new ArrayList<>(spectators.values());
    }

    public synchronized List<CookiePlayer> getOwnedPlayers() {
        List<CookiePlayer> owned = new ArrayList<>(players);
        owned.addAll(spectators.values());
        return owned;
    }

    public synchronized boolean ownsPlayer(CookiePlayer player) {
        if (player == null || player.getPlayer() == null) return false;
        UUID playerId = player.getPlayer().getUniqueId();
        return players.stream().anyMatch(existing -> existing.getPlayer().getUniqueId().equals(playerId))
                || spectators.containsKey(playerId);
    }

    public synchronized boolean isExternalSpectator(UUID playerId) {
        return playerId != null && spectators.containsKey(playerId);
    }

    public void tick() {
        time++;
        if (state == GameState.OPEN && !players.isEmpty()
                && time % QUEUE_TELEMETRY_INTERVAL_SECONDS == 0) {
            emitQueueState();
        }
        if (state == GameState.OPEN) {
            GameManager.activateReadyQueueIntents(this);
            int availablePlayers = GameManager.getAvailablePlayerCount();
            if (canStartCountdown()) {
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
                startTimer = 0; // Reset whenever headcount or mode-specific composition is not ready.
                inQuickStart = false;
                if (!players.isEmpty()) {
                    long now = System.currentTimeMillis();
                    for (CookiePlayer waiting : players) {
                        long queuedAt = queueEnteredAt.getOrDefault(waiting.getPlayer().getUniqueId(), now);
                        long waitingSeconds = Math.max(0L, (now - queuedAt) / 1000);
                        waiting.getPlayer().sendActionBar(createWaitingActionBar(waiting, waitingSeconds));
                        if (CookieDough.getInstance() != null && CookieDough.getInstance().getPlayerHubMenu() != null) {
                            CookieDough.getInstance().getPlayerHubMenu().updateQueueWait(
                                    waiting.getPlayer(), gameName, waitingSeconds);
                        }
                    }
                }
            }

            // Notify players of the countdown
            if (startTimer > 0) {
                notifyCountdown();
            }
        }
    }

    /**
     * Allows team-based modes to require a playable team composition before the
     * shared countdown begins. Headcount remains the default for free-for-all
     * games.
     */
    protected boolean canStartCountdown() {
        return players.size() >= minimumPlayers;
    }

    /** Creates the continuously refreshed status shown while a lobby cannot count down. */
    protected net.kyori.adventure.text.Component createWaitingActionBar(
            CookiePlayer waiting, long waitingSeconds) {
        int missingPlayers = Math.max(0, minimumPlayers - players.size());
        return net.kyori.adventure.text.Component.text(
                LocaleManager.getMessage("game.waiting.players", waiting.getPlayer().locale(),
                        waitingSeconds, missingPlayers),
                net.kyori.adventure.text.format.NamedTextColor.YELLOW);
    }

    private void notifyCountdown() {
        int delay = inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS;
        int remainingTime = delay - startTimer;

        for (CookiePlayer player : players) {
            player.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                    LocaleManager.getMessage("game.waiting.starting", player.getPlayer().locale(),
                            Math.max(0, remainingTime), players.size(), capacity),
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
        admissionsOpen = false;
        isFilling = false;
        GameManager.reassignQueueIntents(this);
        emitQueueState();
        GameManager.notifyGameChanged(this, "started");
        for (CookiePlayer player : players) {
            Long queuedAt = queueEnteredAt.remove(player.getPlayer().getUniqueId());
            long waitSeconds = queuedAt == null ? 0L
                    : Math.max(0L, (System.currentTimeMillis() - queuedAt) / 1000);
            FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.QUEUE_LEFT,
                    "game=" + gameName + " wait_seconds=" + waitSeconds + " reason=match_started");
            if (CookieDough.getInstance() != null) {
                if (CookieDough.getInstance().getCosmeticEffects() != null) {
                    CookieDough.getInstance().getCosmeticEffects()
                            .disableLobbyFlightBeforeArena(player.getPlayer());
                }
                if (CookieDough.getInstance().getPracticeManager() != null) {
                    CookieDough.getInstance().getPracticeManager().stop(player.getPlayer(), false);
                }
                if (CookieDough.getInstance().getPlayerHubMenu() != null) {
                    CookieDough.getInstance().getPlayerHubMenu().leaveQueue(player.getPlayer());
                }
            }
            player.setState(PlayerState.IN_GAME);
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

    /** Cross-world transport with chunk preflight and a bounded anti-floating window. */
    protected final void teleportPlayerSafely(Player player, Location destination) {
        if (!tryTeleportPlayerSafely(player, destination)) {
            throw new IllegalStateException("Could not safely teleport player into " + gameName);
        }
    }

    /** Fallible variant for reconnects, which must preserve their reservation on failure. */
    protected final boolean tryTeleportPlayerSafely(Player player, Location destination) {
        return player != null && destination != null && destination.getWorld() != null
                && destination.getChunk().load()
                && CookieDough.getInstance().getPlayerTransitionFlightGuard().teleport(player, destination);
    }

    /** Whether this mode exposes its live arena to lobby spectators. */
    public boolean supportsSpectating() {
        return false;
    }

    /**
     * Resolves the mode-owned destination without mutating the player. Modules
     * must reject unavailable maps/worlds here so a passive activity is never
     * left before the target arena is ready.
     */
    protected Location spectatorDestination(CookiePlayer player) {
        return null;
    }

    /** Loads the exact destination before the source activity is released. */
    public synchronized boolean preflightSpectatorAdmission(CookiePlayer player) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline()
                || state != GameState.RUNNING || !supportsSpectating()) return false;
        Location destination = spectatorDestination(player);
        return destination != null && destination.getWorld() != null && destination.getChunk().load();
    }

    /** Places a viewer at the already-preflighted safe mode-owned point. */
    protected boolean teleportToSpectator(CookiePlayer player) {
        Location destination = spectatorDestination(player);
        if (destination == null || destination.getWorld() == null
                || !destination.getChunk().load()
                || !CookieDough.getInstance().getPlayerTransitionFlightGuard()
                        .teleport(player.getPlayer(), destination)) return false;
        player.resetPlayer();
        player.getPlayer().setGameMode(GameMode.SPECTATOR);
        return true;
    }

    public boolean hasStarted() {
        return state == GameState.RUNNING;
    }

    public void resetGame() {
        if (!getOwnedPlayers().isEmpty() && !ejectOwnedPlayersToLobby()) return;
        state = GameState.OPEN;
        admissionsOpen = GameManager.areGlobalAdmissionsOpen();
        time = 0;
        startTimer = 0;
        players.clear();
        queueEnteredAt.clear();
        inQuickStart = false;
        replacementRegistrationRequested = false;
        replacementRegistrationAttemptTick = Integer.MIN_VALUE;
        GameManager.notifyGameChanged(this, "reset");
    }

    public abstract boolean isGameEnded();

    /**
     * Administrative/disable lifecycle hook. Game modules already overriding
     * this method can persist interrupted outcomes and unload their map safely.
     */
    public void shutdown() {
        closeAdmissions();
        setState(GameState.FINISHED);
        for (CookiePlayer player : getOwnedPlayers()) {
            if (player.getPlayer().isOnline()) {
                com.cookiebuild.cookiedough.lobby.LobbyManager.teleportPlayerToLobby(player);
            } else {
                removePlayer(player, "administrative_cancel");
            }
        }
        GameManager.removeGame(this);
    }

    /** Moves viewers out before a mode unloads its arena world. */
    public boolean ejectSpectatorsToLobby() {
        for (CookiePlayer spectator : getSpectators()) {
            try {
                if (spectator.getPlayer().isOnline()) {
                    com.cookiebuild.cookiedough.lobby.LobbyManager.teleportPlayerToLobby(spectator);
                } else {
                    removePlayer(spectator, "game_removed");
                }
            } catch (RuntimeException error) {
                logEjectionFailure(spectator, error);
            }
        }
        return getSpectators().isEmpty();
    }

    /**
     * Moves every player owned by this arena before reset or world unload. A
     * failed transport deliberately preserves ownership so cleanup can retry.
     */
    public boolean ejectOwnedPlayersToLobby() {
        for (CookiePlayer player : getOwnedPlayers()) {
            try {
                if (player.getPlayer().isOnline()) {
                    com.cookiebuild.cookiedough.lobby.LobbyManager.teleportPlayerToLobby(player);
                } else {
                    removePlayer(player, "game_removed");
                }
            } catch (RuntimeException error) {
                logEjectionFailure(player, error);
            }
        }
        return getOwnedPlayers().isEmpty();
    }

    private void logEjectionFailure(CookiePlayer player, RuntimeException error) {
        CookieDough plugin = CookieDough.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("Could not move player " + player.getPlayer().getUniqueId()
                    + " out of " + gameName + " arena " + gameId + ": " + error.getMessage());
        }
    }

    /** Call once when the result is known to expose a consistent replay action. */
    public void offerReplay() {
        for (CookiePlayer cookiePlayer : getPlayers()) {
            if (!cookiePlayer.getPlayer().isOnline()) {
                continue;
            }
            FunnelTelemetry.record(cookiePlayer.getPlayer(), FunnelTelemetry.Event.MATCH_COMPLETED,
                    "game=" + gameName);
            boolean bedrockQueueOffer = CookieDough.getInstance().getRallyManager()
                    .notifyAvailableAfterMatch(cookiePlayer.getPlayer(), gameName);
            cookiePlayer.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                            LocaleManager.getMessage("feedback.action", cookiePlayer.getPlayer().locale()),
                            net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/feedback "))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.Component.text(LocaleManager.getMessage(
                                    "feedback.hover", cookiePlayer.getPlayer().locale())))));
            if (!bedrockQueueOffer && CookieDough.getInstance() != null
                    && CookieDough.getInstance().getPlayerHubMenu() != null) {
                CookieDough.getInstance().getPlayerHubMenu().openReplay(cookiePlayer.getPlayer(), gameName);
            }
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

    @Override
    public int getCountdownSeconds() {
        if (startTimer <= 0) return 0;
        int delay = inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS;
        return Math.max(0, delay - startTimer);
    }

    public void setStartTimer(int startTimer) {
        this.startTimer = startTimer;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
        if (state != GameState.OPEN) admissionsOpen = false;
        GameManager.notifyGameChanged(this, "state_changed");
    }

    @Override
    public boolean isAdmissionsOpen() {
        return admissionsOpen && state == GameState.OPEN;
    }

    public void closeAdmissions() {
        admissionsOpen = false;
        emitQueueState();
        GameManager.notifyGameChanged(this, "admissions_closed");
    }

    public boolean reopenAdmissions() {
        if (state != GameState.OPEN || !GameManager.areGlobalAdmissionsOpen()) return false;
        admissionsOpen = true;
        emitQueueState();
        GameManager.notifyGameChanged(this, "admissions_reopened");
        return true;
    }

    private void emitQueueState() {
        CookieDough plugin = CookieDough.getInstance();
        if (plugin == null) return;
        boolean queueOpen = state == GameState.OPEN && admissionsOpen;
        int eligiblePlayers = queueOpen ? players.size() : 0;
        long oldestWaitSeconds = 0L;
        if (eligiblePlayers > 0 && !queueEnteredAt.isEmpty()) {
            long oldestEnteredAt = queueEnteredAt.values().stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(System.currentTimeMillis());
            oldestWaitSeconds = Math.max(0L, (System.currentTimeMillis() - oldestEnteredAt) / 1000L);
        }
        plugin.getLogger().info("[queue] event=state"
                + " game=" + gameName
                + " eligible_players=" + eligiblePlayers
                + " minimum_players=" + minimumPlayers
                + " ready_to_start=" + (queueOpen && canStartCountdown())
                + " oldest_wait_seconds=" + oldestWaitSeconds);
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

    @Override
    public int getQueuePlayerCount() {
        return isAdmissionsOpen() ? players.size() : 0;
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
