package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import com.cookiebuild.cookiedough.retention.PartyManager;

public class GameManager {
    private static final Duration QUEUE_INTENT_TTL = Duration.ofMinutes(10);
    private static final Duration QUEUE_INTENT_FAILURE_NOTICE_COOLDOWN = Duration.ofSeconds(30);
    public record QueueIntent(UUID playerId, UUID gameId, String gameName, long createdAtMillis,
            UUID cohortId, int cohortSize) {
        public QueueIntent {
            cohortId = cohortId == null ? playerId : cohortId;
            cohortSize = Math.max(1, cohortSize);
        }

        static QueueIntent solo(UUID playerId, Game game, long createdAtMillis) {
            return new QueueIntent(playerId, game.getGameId(), game.getGameName(), createdAtMillis,
                    playerId, 1);
        }
    }
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
        adoptWaitingQueueIntents(game);
        notifyGameChanged(game, "registered");
    }

    public static void removeGame(Game game) {
        if (game != null) game.ejectSpectatorsToLobby();
        if (game != null) reassignQueueIntents(game);
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
        cancelQueueIntent(playerId);
        queueIntentFailureNoticeAt.remove(playerId);
        queueIntents.put(playerId, QueueIntent.solo(playerId, game, System.currentTimeMillis()));
        return true;
    }

    /**
     * Registers one indivisible party cohort. Members remain outside the game
     * roster until the whole cohort passes the ready-check and can be admitted.
     */
    public static synchronized boolean registerPartyQueueIntent(List<CookiePlayer> members, Game game,
            UUID cohortId) {
        if (members == null || members.isEmpty() || game == null || cohortId == null
                || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()
                || game.getPartyAdmissionProblem(members.size()) != null) {
            return false;
        }
        List<CookiePlayer> distinct = members.stream().filter(java.util.Objects::nonNull)
                .filter(member -> member.getPlayer() != null)
                .filter(member -> member.getPlayer().isOnline())
                .filter(member -> PlayerWrapperListener.isPlayerDataReady(member.getPlayer().getUniqueId()))
                .filter(GameManager::isQueueIntentEligible)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                member -> member.getPlayer().getUniqueId(), member -> member,
                                (left, right) -> left, LinkedHashMap::new),
                        values -> new ArrayList<>(values.values())));
        if (distinct.size() != members.size()) return false;

        java.util.Set<UUID> memberIds = distinct.stream()
                .map(member -> member.getPlayer().getUniqueId()).collect(java.util.stream.Collectors.toSet());
        int alreadyQueued = validQueueIntentCohorts(game).stream()
                .flatMap(List::stream)
                .filter(intent -> !memberIds.contains(intent.playerId()))
                .mapToInt(intent -> 1).sum();
        if (game.getPlayerCount() + alreadyQueued + distinct.size() > game.getCapacity()) return false;

        for (UUID memberId : memberIds) cancelQueueIntent(memberId);
        long createdAt = System.currentTimeMillis();
        for (CookiePlayer member : distinct) {
            UUID playerId = member.getPlayer().getUniqueId();
            queueIntentFailureNoticeAt.remove(playerId);
            queueIntents.put(playerId, new QueueIntent(playerId, game.getGameId(), game.getGameName(),
                    createdAt, cohortId, distinct.size()));
        }
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
        cancelQueueIntent(playerId);
        queueIntentFailureNoticeAt.remove(playerId);
        queueIntents.put(playerId, QueueIntent.solo(playerId, game, System.currentTimeMillis()));
        return true;
    }

    public static synchronized boolean cancelQueueIntent(UUID playerId) {
        if (playerId == null) return false;
        QueueIntent intent = queueIntents.get(playerId);
        if (intent == null) return false;
        queueIntents.entrySet().removeIf(entry -> {
            boolean sameCohort = entry.getValue().cohortId().equals(intent.cohortId());
            if (sameCohort) queueIntentFailureNoticeAt.remove(entry.getKey());
            return sameCohort;
        });
        return true;
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
        LobbyManager lobby = LobbyManager.getInstance();
        if (lobby == null) return;
        QueueAdmissionPlan plan = queueAdmissionPlan(game, lobby);
        plan.blockedCohorts().forEach(cohort ->
                cohort.forEach(intent -> notifyQueueAdmissionFailure(intent, now)));
        List<List<QueueIntent>> readyCohorts = plan.readyCohorts();
        int readyCount = readyCohorts.stream().mapToInt(List::size).sum();
        if (!QueueIntentReadinessPolicy.shouldActivate(game.getPlayerCount(), readyCount,
                game.getMinimumPlayers(), game.getCapacity())) return;
        for (List<QueueIntent> cohort : readyCohorts) {
            if (game.getPlayerCount() + cohort.size() > game.getCapacity()) break;
            if (cohort.stream().anyMatch(intent -> queueIntents.get(intent.playerId()) != intent)) continue;
            List<CookiePlayer> members = cohort.stream().map(intent -> {
                org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
                return online == null ? null : PlayerManager.getPlayer(online);
            }).toList();
            if (members.stream().allMatch(java.util.Objects::nonNull)
                    && lobby.admitQueuedParty(members, game)) {
                cohort.forEach(intent -> {
                    queueIntents.remove(intent.playerId(), intent);
                    queueIntentFailureNoticeAt.remove(intent.playerId());
                });
            } else {
                cohort.forEach(intent -> notifyQueueAdmissionFailure(intent, now));
            }
        }
    }

    /** Valid passive intentions, grouped so a party is counted only when complete. */
    private static List<List<QueueIntent>> validQueueIntentCohorts(Game game) {
        if (game == null) return List.of();
        Map<UUID, List<QueueIntent>> grouped = queueIntents.values().stream()
                .filter(intent -> intent.gameId().equals(game.getGameId()))
                .sorted(Comparator.comparingLong(QueueIntent::createdAtMillis))
                .collect(java.util.stream.Collectors.groupingBy(
                        QueueIntent::cohortId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<List<QueueIntent>> valid = new ArrayList<>();
        for (List<QueueIntent> cohort : grouped.values()) {
            if (!isCompleteCohort(cohort)
                    || !cohort.stream().allMatch(GameManager::isQueueIntentEligible)) {
                continue;
            }
            if (!isCurrentPartyCohort(cohort)) {
                cancelStalePartyCohort(cohort);
                continue;
            }
            valid.add(List.copyOf(cohort));
        }
        return List.copyOf(valid);
    }

    /** Revalidates a queued party against the latest durable app/server snapshot. */
    private static boolean isCurrentPartyCohort(List<QueueIntent> cohort) {
        CookieDough plugin = CookieDough.getInstance();
        PartyManager parties = plugin == null ? null : plugin.getPartyManager();
        if (parties == null) return false;
        if (cohort.size() == 1 && cohort.getFirst().cohortId().equals(cohort.getFirst().playerId())) {
            return parties.isCurrentSoloQueueCohort(cohort.getFirst().playerId());
        }
        return parties.isCurrentQueueCohort(cohort.getFirst().cohortId(),
                cohort.stream().map(QueueIntent::playerId).toList());
    }

    private static void cancelStalePartyCohort(List<QueueIntent> cohort) {
        for (QueueIntent intent : cohort) {
            if (!queueIntents.remove(intent.playerId(), intent)) continue;
            queueIntentFailureNoticeAt.remove(intent.playerId());
            org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(org.bukkit.ChatColor.YELLOW
                        + com.cookiebuild.cookiedough.utils.LocaleManager.getMessage(
                                "lobby.queue.intent_party_changed", online.locale()));
            }
        }
    }

    static boolean isCompleteCohort(List<QueueIntent> cohort) {
        if (cohort == null || cohort.isEmpty()) return false;
        int expected = cohort.getFirst().cohortSize();
        UUID cohortId = cohort.getFirst().cohortId();
        return cohort.size() == expected
                && cohort.stream().allMatch(intent -> intent.cohortSize() == expected
                        && intent.cohortId().equals(cohortId))
                && cohort.stream().map(QueueIntent::playerId).distinct().count() == expected;
    }

    private static boolean isQueueIntentEligible(QueueIntent intent) {
        org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
        CookiePlayer current = online == null ? null : PlayerManager.getPlayer(online);
        return current != null && isQueueIntentEligible(current);
    }

    private static boolean isQueueIntentEligible(CookiePlayer current) {
        if (current == null || current.getPlayer() == null || !current.getPlayer().isOnline()) return false;
        if (!PlayerWrapperListener.isPlayerDataReady(current.getPlayer().getUniqueId())) return false;
        Game owned = getGameOfPlayer(current);
        boolean externalViewer = current.getState() == PlayerState.SPECTATING && owned != null
                && owned.isExternalSpectator(current.getPlayer().getUniqueId());
        return current.getState() == PlayerState.LOBBY
                || current.getState() == PlayerState.PERSISTENT_MODE || externalViewer;
    }

    public static int getValidQueueIntentCount(Game game) {
        return validQueueIntentCohorts(game).stream().mapToInt(List::size).sum();
    }

    /** Counts only complete cohorts that pass the same non-destructive leave preflight as activation. */
    public static int getAdmittableQueueIntentCount(Game game) {
        return admittableQueueIntentCohorts(game).stream().mapToInt(List::size).sum();
    }

    public static org.bukkit.entity.Player getFirstAdmittableQueueIntentPlayer(Game game) {
        return admittableQueueIntentCohorts(game).stream().flatMap(List::stream)
                .map(intent -> Bukkit.getPlayer(intent.playerId()))
                .filter(java.util.Objects::nonNull).filter(org.bukkit.entity.Player::isOnline)
                .findFirst().orElse(null);
    }

    private static List<List<QueueIntent>> admittableQueueIntentCohorts(Game game) {
        LobbyManager lobby = LobbyManager.getInstance();
        if (lobby == null) return List.of();
        return queueAdmissionPlan(game, lobby).readyCohorts();
    }

    /** Shared FIFO/capacity plan used by readiness UX, Rally and actual activation. */
    private static QueueAdmissionPlan queueAdmissionPlan(Game game, LobbyManager lobby) {
        if (game == null || lobby == null || game.getState() != GameState.OPEN || !game.isAdmissionsOpen()) {
            return new QueueAdmissionPlan(List.of(), List.of());
        }
        int remaining = Math.max(0, game.getCapacity() - game.getPlayerCount());
        List<List<QueueIntent>> ready = new ArrayList<>();
        List<List<QueueIntent>> blocked = new ArrayList<>();
        for (List<QueueIntent> cohort : validQueueIntentCohorts(game)) {
            if (cohort.size() > remaining || game.getPartyAdmissionProblem(cohort.size()) != null) continue;
            boolean canAdmit = cohort.stream().allMatch(intent -> {
                org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
                CookiePlayer current = online == null ? null : PlayerManager.getPlayer(online);
                return current != null && lobby.canAdmitQueuedIntent(current, game);
            });
            if (canAdmit) {
                ready.add(cohort);
                remaining -= cohort.size();
            } else {
                blocked.add(cohort);
            }
        }
        return new QueueAdmissionPlan(List.copyOf(ready), List.copyOf(blocked));
    }

    private record QueueAdmissionPlan(List<List<QueueIntent>> readyCohorts,
            List<List<QueueIntent>> blockedCohorts) { }

    private static void notifyQueueAdmissionFailure(QueueIntent intent, long now) {
        org.bukkit.entity.Player online = Bukkit.getPlayer(intent.playerId());
        if (online == null || !online.isOnline()) return;
        long lastNotice = queueIntentFailureNoticeAt.getOrDefault(intent.playerId(), 0L);
        if (now - lastNotice < QUEUE_INTENT_FAILURE_NOTICE_COOLDOWN.toMillis()) return;
        queueIntentFailureNoticeAt.put(intent.playerId(), now);
        online.sendMessage(org.bukkit.ChatColor.RED + com.cookiebuild.cookiedough.utils.LocaleManager
                .getMessage("lobby.queue.leave_failed", online.locale()));
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
                    intent.createdAtMillis(), intent.cohortId(), intent.cohortSize());
        });
        // No replacement yet: keep the cohort intact until addGame adopts it or
        // the normal TTL expires. Losing it here would silently cancel a party.
    }

    private static void adoptWaitingQueueIntents(Game replacement) {
        if (replacement == null || replacement.getState() != GameState.OPEN
                || !replacement.isAdmissionsOpen()) return;
        queueIntents.replaceAll((playerId, intent) -> {
            Game previous = getGameById(intent.gameId());
            if (!shouldAdoptWaitingIntent(intent, replacement, previous)) return intent;
            return new QueueIntent(playerId, replacement.getGameId(), replacement.getGameName(),
                    intent.createdAtMillis(), intent.cohortId(), intent.cohortSize());
        });
    }

    static boolean shouldAdoptWaitingIntent(QueueIntent intent, Game replacement, Game previous) {
        return intent != null && replacement != null
                && intent.gameName().equalsIgnoreCase(replacement.getGameName())
                && (previous == null || previous.getState() != GameState.OPEN || !previous.isAdmissionsOpen());
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
                .flatMap(game -> game.getPlayers().stream())
                .filter(player -> player.getPlayer() != null && player.getPlayer().isOnline())
                .map(player -> player.getPlayer().getUniqueId())
                .distinct()
                .count();
    }
}
