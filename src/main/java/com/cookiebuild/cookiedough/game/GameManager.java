package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.util.UUID;

import org.bukkit.Bukkit;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.lobby.StatueManager;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;

public class GameManager {
    private static final Duration QUEUE_INTENT_TTL = Duration.ofMinutes(10);
    private static final Duration QUEUE_INTENT_FAILURE_NOTICE_COOLDOWN = Duration.ofSeconds(30);
    public record QueueIntent(UUID playerId, UUID gameId, String gameName, long createdAtMillis) { }
    public record GameLifecycleEvent(String kind, UUID gameId, String gameName, GameState state) { }

    @FunctionalInterface
    public interface GameLifecycleListener {
        void onGameLifecycle(GameLifecycleEvent event);
    }

    private static final List<Game> games = new CopyOnWriteArrayList<>();
    private static final Map<UUID, QueueIntent> queueIntents = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> queueIntentFailureNoticeAt = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<GameLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private static volatile boolean globalAdmissionsOpen = true;
    private static final GameSelectionPolicy quickPlaySelection = new GameSelectionPolicy();
    private static long lastTickBatchAtNanos;

    public static void addGame(Game game) {
        if (!globalAdmissionsOpen) game.closeAdmissions();
        games.add(game);
        notifyGameChanged(game, "registered");
    }

    public static void removeGame(Game game) {
        if (game != null) game.ejectSpectatorsToLobby();
        if (game != null) {
            queueIntents.entrySet().removeIf(entry -> {
                if (!entry.getValue().gameId().equals(game.getGameId())) return false;
                queueIntentFailureNoticeAt.remove(entry.getKey());
                org.bukkit.entity.Player online = Bukkit.getPlayer(entry.getKey());
                if (online != null && online.isOnline()) {
                    online.sendMessage(org.bukkit.ChatColor.YELLOW + com.cookiebuild.cookiedough.utils.LocaleManager
                            .getMessage("lobby.queue.intent_game_closed", online.locale(), game.getGameName()));
                }
                return true;
            });
        }
        games.remove(game);
        notifyGameChanged(game, "removed");
        if (game != null && game.getState() == GameState.FINISHED) {
            StatueManager.refreshAfterMatch(game.getGameName());
        }
        // Each game module owns its namespaced world and performs cleanup itself.
    }

    public static ArrayList<Game> getGames() {
        return new ArrayList<>(games);
    }

    public static void tickGames() {
        long batchStartedAt = System.nanoTime();
        expireQueueIntents(System.currentTimeMillis());
        if (lastTickBatchAtNanos != 0L) {
            long schedulingDelayMillis = Math.max(0L,
                    (batchStartedAt - lastTickBatchAtNanos) / 1_000_000L - 1_000L);
            if (schedulingDelayMillis >= 100L) {
                CookieDough.getInstance().getLogger().warning(
                        "[performance] event=server_tick_delay delay_ms=" + schedulingDelayMillis);
            }
        }
        lastTickBatchAtNanos = batchStartedAt;
        // Create a copy of the games list to iterate over
        ArrayList<Game> gamesCopy = new ArrayList<>(games);
        for (Game game : gamesCopy) {
            long startedAt = System.nanoTime();
            game.tick();
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            if (elapsedMillis >= 50L) {
                CookieDough.getInstance().getLogger().warning(
                        "[performance] event=slow_game_tick game=" + game.getGameName()
                                + " state=" + game.getState()
                                + " elapsed_ms=" + elapsedMillis);
            }
        }
    }

    static boolean hasOtherOpenGame(Game current) {
        return games.stream()
                .anyMatch(game -> game != current
                        && game.getState() == GameState.OPEN
                        && game.isAdmissionsOpen()
                        && game.getGameName().equalsIgnoreCase(current.getGameName()));
    }

    public static Game getGameOfPlayer(CookiePlayer player) {
        for (Game game : GameManager.getGames()) {
            if (game.ownsPlayer(player)) {
                return game;
            }
        }
        return null;
    }

    public static Game getGameByName(String gameName) {
        Game open = getOpenGameByName(gameName);
        if (open != null) {
            return open;
        }
        return games.stream().filter(game -> game.getGameName().equalsIgnoreCase(gameName))
                .findFirst().orElse(null);
    }

    /** Chooses the fullest live arena so spectators see an active match. */
    public static Game getSpectatableGameByName(String gameName) {
        return games.stream()
                .filter(Game::supportsSpectating)
                .filter(game -> game.getState() == GameState.RUNNING)
                .filter(game -> game.getGameName().equalsIgnoreCase(gameName))
                .max(Comparator.comparingInt(Game::getPlayerCount))
                .orElse(null);
    }

    public static Game getGameById(UUID gameId) {
        return games.stream().filter(game -> game.getGameId().equals(gameId)).findFirst().orElse(null);
    }

    /** Registers a queue intent without placing a passive-activity player in a game roster. */
    public static boolean registerQueueIntent(CookiePlayer player, Game game) {
        Game current = player == null ? null : getGameOfPlayer(player);
        boolean externalSpectator = player != null && player.getPlayer() != null
                && player.getState() == PlayerState.SPECTATING && current != null
                && current.isExternalSpectator(player.getPlayer().getUniqueId());
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline() || game == null
                || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()
                || player.getState() != PlayerState.PERSISTENT_MODE && !externalSpectator) {
            return false;
        }
        UUID playerId = player.getPlayer().getUniqueId();
        queueIntentFailureNoticeAt.remove(playerId);
        queueIntents.put(playerId, new QueueIntent(
                playerId, game.getGameId(), game.getGameName(), System.currentTimeMillis()));
        return true;
    }

    /**
     * Records an invitation accepted from the replay transition without treating
     * an eliminated participant as an external spectator. Activation remains
     * deferred until normal match cleanup returns the player to the lobby.
     */
    public static boolean registerPostMatchQueueIntent(CookiePlayer player, Game game) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline() || game == null
                || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()) {
            return false;
        }
        UUID playerId = player.getPlayer().getUniqueId();
        Game current = getGameOfPlayer(player);
        boolean participant = current != null && !current.isExternalSpectator(playerId)
                && current.getPlayers().stream().anyMatch(existing ->
                        existing.getPlayer().getUniqueId().equals(playerId));
        if (!participant || player.getState() != PlayerState.IN_GAME
                && player.getState() != PlayerState.SPECTATING) {
            return false;
        }
        queueIntentFailureNoticeAt.remove(playerId);
        queueIntents.put(playerId, new QueueIntent(
                playerId, game.getGameId(), game.getGameName(), System.currentTimeMillis()));
        return true;
    }

    public static boolean cancelQueueIntent(UUID playerId) {
        if (playerId == null) return false;
        queueIntentFailureNoticeAt.remove(playerId);
        return queueIntents.remove(playerId) != null;
    }

    public static QueueIntent getQueueIntent(UUID playerId) {
        return playerId == null ? null : queueIntents.get(playerId);
    }

    /**
     * Converts passive queue intentions only once the arena can actually reach
     * its minimum. Each conversion reuses the authoritative lobby/activity exit.
     */
    static void activateReadyQueueIntents(Game game) {
        if (game == null || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()) return;
        long now = System.currentTimeMillis();
        expireQueueIntents(now);
        List<QueueIntent> valid = queueIntents.values().stream()
                .filter(intent -> intent.gameId().equals(game.getGameId()))
                .sorted(Comparator.comparingLong(QueueIntent::createdAtMillis))
                .filter(intent -> {
                    org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
                    CookiePlayer current = online == null ? null : PlayerManager.getPlayer(online);
                    Game owned = current == null ? null : getGameOfPlayer(current);
                    boolean externalViewer = current != null && current.getState() == PlayerState.SPECTATING
                            && owned != null && owned.isExternalSpectator(intent.playerId());
                    return online != null && online.isOnline() && current != null
                            && (current.getState() == PlayerState.LOBBY
                                    || current.getState() == PlayerState.PERSISTENT_MODE
                                    || externalViewer);
                }).toList();
        if (!QueueIntentReadinessPolicy.shouldActivate(game.getPlayerCount(), valid.size(),
                game.getMinimumPlayers(), game.getCapacity())) return;
        LobbyManager lobby = LobbyManager.getInstance();
        if (lobby == null) return;
        List<QueueIntent> ready = valid.stream().filter(intent -> {
            org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
            CookiePlayer current = online == null ? null : PlayerManager.getPlayer(online);
            return current != null && lobby.canAdmitQueuedIntent(current, game);
        }).toList();
        int needed = Math.max(0, game.getMinimumPlayers() - game.getPlayerCount());
        if (ready.size() < needed) {
            valid.stream().filter(intent -> !ready.contains(intent)).forEach(intent -> {
                org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
                if (online != null && online.isOnline()) {
                    long lastNotice = queueIntentFailureNoticeAt.getOrDefault(intent.playerId(), 0L);
                    if (now - lastNotice >= QUEUE_INTENT_FAILURE_NOTICE_COOLDOWN.toMillis()) {
                        queueIntentFailureNoticeAt.put(intent.playerId(), now);
                        online.sendMessage(org.bukkit.ChatColor.RED + com.cookiebuild.cookiedough.utils.LocaleManager
                                .getMessage("lobby.queue.leave_failed", online.locale()));
                    }
                }
            });
            return;
        }
        for (QueueIntent intent : ready) {
            if (game.getPlayerCount() >= game.getCapacity()) break;
            if (queueIntents.get(intent.playerId()) != intent) continue;
            org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
            CookiePlayer current = online == null ? null : PlayerManager.getPlayer(online);
            if (current != null && lobby.admitQueuedIntent(current, game)) {
                queueIntents.remove(intent.playerId(), intent);
                queueIntentFailureNoticeAt.remove(intent.playerId());
            } else if (online != null && online.isOnline()) {
                long lastNotice = queueIntentFailureNoticeAt.getOrDefault(intent.playerId(), 0L);
                if (now - lastNotice >= QUEUE_INTENT_FAILURE_NOTICE_COOLDOWN.toMillis()) {
                    queueIntentFailureNoticeAt.put(intent.playerId(), now);
                    online.sendMessage(org.bukkit.ChatColor.RED + com.cookiebuild.cookiedough.utils.LocaleManager
                            .getMessage("lobby.queue.leave_failed", online.locale()));
                }
            }
        }
    }

    static void reassignQueueIntents(Game closed) {
        if (closed == null) return;
        Game replacement = games.stream()
                .filter(candidate -> candidate != closed)
                .filter(candidate -> candidate.getGameName().equalsIgnoreCase(closed.getGameName()))
                .filter(candidate -> candidate.getState() == GameState.OPEN && candidate.isAdmissionsOpen())
                .filter(candidate -> candidate.getPlayerCount() < candidate.getCapacity())
                .max(Comparator.comparingInt(Game::getPlayerCount)).orElse(null);
        queueIntents.replaceAll((playerId, intent) -> {
            if (!intent.gameId().equals(closed.getGameId())) return intent;
            org.bukkit.entity.Player online = Bukkit.getPlayer(playerId);
            if (replacement == null) {
                if (online != null && online.isOnline()) {
                    online.sendMessage(org.bukkit.ChatColor.YELLOW + com.cookiebuild.cookiedough.utils.LocaleManager
                            .getMessage("lobby.queue.intent_game_closed", online.locale(), closed.getGameName()));
                }
                return intent;
            }
            if (online != null && online.isOnline()) {
                online.sendMessage(org.bukkit.ChatColor.YELLOW + com.cookiebuild.cookiedough.utils.LocaleManager
                        .getMessage("lobby.queue.intent_reassigned", online.locale(), replacement.getGameName()));
            }
            return new QueueIntent(playerId, replacement.getGameId(), replacement.getGameName(),
                    intent.createdAtMillis());
        });
        if (replacement == null) {
            queueIntents.entrySet().removeIf(entry -> entry.getValue().gameId().equals(closed.getGameId()));
        }
    }

    private static void expireQueueIntents(long nowMillis) {
        queueIntents.entrySet().removeIf(entry -> {
            QueueIntent intent = entry.getValue();
            if (nowMillis - intent.createdAtMillis() < QUEUE_INTENT_TTL.toMillis()) return false;
            queueIntentFailureNoticeAt.remove(entry.getKey());
            org.bukkit.entity.Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null && online.isOnline()) {
                online.sendMessage(org.bukkit.ChatColor.YELLOW + com.cookiebuild.cookiedough.utils.LocaleManager
                        .getMessage("lobby.queue.intent_expired", online.locale(), intent.gameName()));
            }
            return true;
        });
    }

    /**
     * Performs the module-owned reconnect hand-off only after CookieDough has
     * finished loading the fresh player profile on the primary server thread.
     */
    public static boolean tryReconnect(CookiePlayer player) {
        if (player == null || player.getPlayer() == null || !player.getPlayer().isOnline()
                || !Bukkit.isPrimaryThread()
                || !PlayerWrapperListener.isPlayerDataReady(player.getPlayer().getUniqueId())
                || player.getState() != PlayerState.LOBBY) {
            return false;
        }
        UUID playerId = player.getPlayer().getUniqueId();
        List<ReconnectableGame> candidates = games.stream()
                .filter(game -> game instanceof ReconnectableGame)
                .map(game -> (ReconnectableGame) game)
                .filter(game -> game.hasReconnectReservation(playerId))
                .toList();
        if (candidates.size() != 1) {
            if (candidates.size() > 1) {
                CookieDough.getInstance().getLogger().severe(
                        "Refusing ambiguous reconnect reservation for " + playerId);
            }
            return false;
        }
        boolean reconnected = candidates.getFirst().reconnect(player);
        if (reconnected) {
            FunnelTelemetry.record(player.getPlayer(), FunnelTelemetry.Event.MATCH_RECONNECTED, "");
        }
        return reconnected;
    }

    public static void setLifecycleListener(GameLifecycleListener listener) {
        if (listener != null) lifecycleListeners.addIfAbsent(listener);
    }

    public static void clearLifecycleListener(GameLifecycleListener listener) {
        if (listener != null) lifecycleListeners.remove(listener);
    }

    public static boolean areGlobalAdmissionsOpen() {
        return globalAdmissionsOpen;
    }

    public static void setGlobalAdmissionsOpen(boolean open) {
        globalAdmissionsOpen = open;
        for (Game game : games) {
            if (!open) game.closeAdmissions();
            else if (game.getState() == GameState.OPEN) game.reopenAdmissions();
        }
    }

    static void notifyGameChanged(Game game, String kind) {
        if (game == null) return;
        GameLifecycleEvent event = new GameLifecycleEvent(
                kind, game.getGameId(), game.getGameName(), game.getState());
        for (GameLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onGameLifecycle(event);
            } catch (RuntimeException error) {
                if (CookieDough.getInstance() != null) {
                    CookieDough.getInstance().getLogger().warning(
                            "Game lifecycle listener failed for " + kind + ": " + error.getMessage());
                }
            }
        }
    }

    public static Game getOpenGameByName(String gameName) {
        return games.stream()
                .filter(game -> game.getGameName().equalsIgnoreCase(gameName))
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(Game::isAdmissionsOpen)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .max(Comparator.comparingInt(Game::getPlayerCount))
                .orElse(null);
    }

    /** Concentrates low population in the game that is closest to starting. */
    public static Game getBestOpenGame() {
        return selectBestOpenGame(games);
    }

    /** Uses the shared fair-selection history for a pre-filtered set of games. */
    public static Game selectBestOpenGame(List<? extends Game> candidates) {
        return quickPlaySelection.select(candidates);
    }

    public static int getAvailablePlayerCount() {
        return (int) PlayerManager.getPlayers().stream().filter(p -> p.getState() == PlayerState.LOBBY).count();
    }

    /** Counts distinct online players owned by a game, including queues and live matches. */
    public static int getOnlineGamePlayerCount() {
        return countDistinctOnlinePlayers(games.stream());
    }

    /**
     * Counts distinct online players across every arena for one game mode.
     *
     * A player can briefly be present in two arena rosters during replacement or
     * reconnect hand-off, so lobby selectors must count UUIDs rather than roster
     * entries. Offline reservations are intentionally excluded.
     */
    public static int getOnlineGamePlayerCount(String gameName) {
        if (gameName == null || gameName.isBlank()) {
            return 0;
        }
        return countDistinctOnlinePlayers(games.stream()
                .filter(game -> game.getGameName().equalsIgnoreCase(gameName)));
    }

    private static int countDistinctOnlinePlayers(java.util.stream.Stream<Game> selectedGames) {
        return (int) selectedGames
                .flatMap(game -> game.getOwnedPlayers().stream())
                .filter(player -> player.getPlayer() != null && player.getPlayer().isOnline())
                .map(player -> player.getPlayer().getUniqueId())
                .distinct()
                .count();
    }
}
