package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;

/** Structured, cooldown-protected player calls for underfilled queues. */
public final class RallyManager {
    private static final Duration AUTOMATIC_COOLDOWN = Duration.ofMinutes(30);
    private static final Duration MANUAL_GAMEMODE_COOLDOWN = Duration.ofMinutes(5);
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static final String UNAVAILABLE = "Player calls are temporarily unavailable. Please try again.";

    private final CookieDough plugin;
    private final RallyRepository repository;
    private final RallyQueueTracker tracker = new RallyQueueTracker();
    private final ExecutorService worker;
    private final Set<UUID> manualInFlight = new HashSet<>();
    private final Set<UUID> automaticInFlight = new HashSet<>();
    private final Set<UUID> acceptedUnconfirmed = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastFailureLog = new AtomicLong();
    private volatile boolean running;

    public RallyManager(CookieDough plugin) {
        this(plugin, new PostgresRallyRepository(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cookie-build-rallies");
            thread.setDaemon(true);
            return thread;
        }));
    }

    RallyManager(CookieDough plugin, RallyRepository repository, ExecutorService worker) {
        this.plugin = plugin;
        this.repository = repository;
        this.worker = worker;
    }

    public void start() {
        running = true;
    }

    /** Called once per second on the Paper main thread after game ticks. */
    public void tick(List<Game> games) {
        if (!running) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<UUID, Game> byId = new HashMap<>();
        List<RallyQueueTracker.QueueState> states = games.stream()
                .filter(game -> normalizeGamemode(game.getGameName()) != null)
                .map(game -> {
                    byId.put(game.getGameId(), game);
                    return queueState(game);
                }).toList();

        RallyQueueTracker.Observation observation = tracker.observe(states, now);
        observation.cancellations().forEach(pending -> cancelAsync(pending.outboxId()));
        tracker.releaseReady(now);

        for (UUID gameId : observation.automaticCandidates()) {
            Game game = byId.get(gameId);
            if (game != null && automaticInFlight.add(gameId)) {
                enqueue(game, RallyRepository.Source.AUTOMATIC, null, ignored -> {
                });
            }
        }
    }

    public void request(Player player, String requestedGamemode, Consumer<String> completion) {
        if (!running) {
            completion.accept(UNAVAILABLE);
            return;
        }
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
        if (game == null) {
            completion.accept("Join a waiting game before calling more players.");
            return;
        }

        String gameId = gamemodeId(game.getGameName());
        if (gameId == null) {
            completion.accept("Player calls are not available for this gamemode.");
            return;
        }
        String requested = requestedGamemode == null || requestedGamemode.isBlank()
                ? gameId
                : normalizeGamemode(requestedGamemode);
        if (requested == null) {
            completion.accept("Unknown gamemode. Use MicroBattles, Pitchout, SkyWars, or BuildBattles.");
            return;
        }
        if (!requested.equals(gameId)) {
            completion.accept("You can only call players for the queue you are currently waiting in.");
            return;
        }
        if (!queueState(game).underfilled()) {
            completion.accept(game.getState() == GameState.OPEN && game.getPlayerCount() >= game.getMinimumPlayers()
                    ? "This game already has enough players and is preparing to start."
                    : "That queue is no longer waiting for players.");
            return;
        }
        if (tracker.hasPending(game.getGameId())) {
            completion.accept("A player call is already scheduled for this queue.");
            return;
        }
        if (!manualInFlight.add(player.getUniqueId())) {
            completion.accept("Your previous player call is still being checked.");
            return;
        }
        enqueue(game, RallyRepository.Source.PLAYER, player, completion);
    }

    public void shutdown(Duration timeout) {
        running = false;
        Set<UUID> cancellations = new HashSet<>(acceptedUnconfirmed);
        tracker.allPending().forEach(pending -> cancellations.add(pending.outboxId()));
        cancellations.forEach(outboxId -> worker.execute(() -> cancelSafely(outboxId)));
        worker.shutdown();
        try {
            if (!worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    private void enqueue(Game game, RallyRepository.Source source, Player actor, Consumer<String> completion) {
        UUID gameId = game.getGameId();
        String actorName = actor == null ? null : publicPlayerName(actor);
        RallyRepository.Request request = new RallyRepository.Request(
                UUID.randomUUID(),
                source,
                gamemodeId(game.getGameName()),
                game.getPlayerCount(),
                Math.max(0, game.getMinimumPlayers() - game.getPlayerCount()),
                actorName);

        worker.execute(() -> {
            try {
                RallyRepository.EnqueueResult result = repository.enqueue(request);
                if (result.status() == RallyRepository.EnqueueStatus.ENQUEUED) {
                    acceptedUnconfirmed.add(result.outboxId());
                    if (!running) {
                        cancelSafely(result.outboxId());
                        acceptedUnconfirmed.remove(result.outboxId());
                        return;
                    }
                }
                runOnMain(() -> handleEnqueueResult(gameId, source, actor, result, completion));
            } catch (RuntimeException error) {
                markFailure("enqueue a player call", error);
                runOnMain(() -> {
                    clearInFlight(gameId, source, actor);
                    completion.accept(UNAVAILABLE);
                });
            }
        });
    }

    private void handleEnqueueResult(UUID gameId, RallyRepository.Source source, Player actor,
            RallyRepository.EnqueueResult result, Consumer<String> completion) {
        if (result.status() == RallyRepository.EnqueueStatus.ENQUEUED) {
            acceptedUnconfirmed.remove(result.outboxId());
        }
        clearInFlight(gameId, source, actor);
        if (!running) {
            return;
        }
        if (result.status() != RallyRepository.EnqueueStatus.ENQUEUED) {
            completion.accept(message(result.status()));
            return;
        }

        Game current = GameManager.getGames().stream()
                .filter(game -> game.getGameId().equals(gameId))
                .findFirst().orElse(null);
        RallyQueueTracker.QueueState currentState = current == null ? null : queueState(current);
        boolean stillRelevant = source == RallyRepository.Source.AUTOMATIC
                ? currentState != null && currentState.queuedCount() > 0
                : currentState != null && currentState.underfilled();
        if (!stillRelevant) {
            cancelAsync(result.outboxId());
            completion.accept("The queue changed, so no player call was sent.");
            return;
        }

        long now = System.currentTimeMillis();
        long releaseAt = result.availableAt().toEpochMilli();
        long nextAutomatic = now + (source == RallyRepository.Source.AUTOMATIC
                ? AUTOMATIC_COOLDOWN.toMillis()
                : MANUAL_GAMEMODE_COOLDOWN.toMillis());
        tracker.markScheduled(gameId, result.outboxId(), releaseAt, nextAutomatic,
                source == RallyRepository.Source.PLAYER);
        completion.accept(source == RallyRepository.Source.PLAYER
                ? "Player call scheduled. It will be cancelled automatically if the queue fills or closes."
                : "");
    }

    private void clearInFlight(UUID gameId, RallyRepository.Source source, Player actor) {
        if (source == RallyRepository.Source.AUTOMATIC) {
            automaticInFlight.remove(gameId);
        } else if (actor != null) {
            manualInFlight.remove(actor.getUniqueId());
        }
    }

    private void cancelAsync(UUID outboxId) {
        if (running) {
            worker.execute(() -> cancelSafely(outboxId));
        }
    }

    private void cancelSafely(UUID outboxId) {
        try {
            repository.cancel(outboxId);
        } catch (RuntimeException error) {
            markFailure("cancel a stale player call", error);
        }
    }

    private void markFailure(String operation, RuntimeException error) {
        long now = System.currentTimeMillis();
        long previous = lastFailureLog.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS && lastFailureLog.compareAndSet(previous, now)) {
            plugin.getLogger().severe("Could not " + operation + ": " + error.getMessage());
        }
    }

    private void runOnMain(Runnable work) {
        if (running) {
            Bukkit.getScheduler().runTask(plugin, work);
        }
    }

    private static RallyQueueTracker.QueueState queueState(Game game) {
        return new RallyQueueTracker.QueueState(
                game.getGameId(), game.getState() == GameState.OPEN,
                game.getPlayerCount(), game.getMinimumPlayers());
    }

    static String normalizeGamemode(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return RallyRepository.SUPPORTED_GAMEMODES.contains(normalized) ? normalized : null;
    }

    static String gamemodeId(String gameName) {
        return normalizeGamemode(gameName);
    }

    private static String publicPlayerName(Player player) {
        return sanitizePlayerName(player.getName());
    }

    static String sanitizePlayerName(String rawName) {
        String name = rawName.trim().replaceAll("[^\\p{L}\\p{N}_. -]", "_");
        name = name.length() <= 32 ? name : name.substring(0, 32);
        name = name.trim();
        if (name.isEmpty()) {
            return "Player";
        }
        return name.length() <= 32 ? name : name.substring(0, 32);
    }

    private static String message(RallyRepository.EnqueueStatus status) {
        return switch (status) {
            case GLOBAL_COOLDOWN -> "A network-wide player call was sent recently. Please wait a moment.";
            case GAMEMODE_COOLDOWN -> "A player call for this gamemode was sent recently.";
            case PLAYER_COOLDOWN -> "You can call players for this gamemode once every 15 minutes.";
            case AUTOMATIC_COOLDOWN -> "The automatic player call is cooling down.";
            case DUPLICATE -> "This player call was already scheduled.";
            case ENQUEUED -> "Player call scheduled.";
        };
    }
}
