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
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Structured, cooldown-protected player calls for underfilled queues. */
public final class RallyManager {
    private static final Duration AUTOMATIC_COOLDOWN = Duration.ofMinutes(30);
    private static final Duration MANUAL_GAMEMODE_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration LOGIN_RALLY_LIFETIME = Duration.ofMinutes(5);
    private static final long RESPONSE_POLL_INTERVAL_MS = 2_000L;
    private static final int RESPONSE_POLL_LIMIT = 50;
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static final String UNAVAILABLE = "Player calls are temporarily unavailable. Please try again.";

    private final CookieDough plugin;
    private final RallyRepository repository;
    private final RallyQueueTracker tracker = new RallyQueueTracker();
    private final LoginRallyTracker loginTracker = new LoginRallyTracker();
    private final ExecutorService worker;
    private final Set<UUID> manualInFlight = new HashSet<>();
    private final Set<UUID> automaticInFlight = new HashSet<>();
    private final Set<UUID> acceptedUnconfirmed = ConcurrentHashMap.newKeySet();
    private final RallyResponseDeliveryTracker responseDeliveries = new RallyResponseDeliveryTracker();
    private final AtomicLong lastFailureLog = new AtomicLong();
    private long nextResponsePollAt;
    private boolean responsePollInFlight;
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
        pollResponses(now);
        loginTracker.observe(now, RallyManager::isSoloOnline).forEach(
                pending -> cancelAsync(pending.outboxId()));
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
            Player target = firstOnlinePlayer(game);
            if (game != null && target != null && automaticInFlight.add(gameId)) {
                enqueue(game, RallyRepository.Source.AUTOMATIC, null, target, ignored -> {
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
            completion.accept("Unknown gamemode. Use MicroBattles, Pitchout, SkyWars, BuildBattles, TurfWars, or BedWars.");
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
        enqueue(game, RallyRepository.Source.PLAYER, player, player, completion);
    }

    /** Starts a network-wide rally once the first connected player's profile is ready. */
    public void requestSoloLogin(Player player) {
        if (!running || player == null || !player.isOnline() || Bukkit.getOnlinePlayers().size() > 1) {
            return;
        }
        if (!manualInFlight.add(player.getUniqueId())) {
            return;
        }
        enqueue(null, RallyRepository.Source.LOGIN, player, player, ignored -> {
        });
    }

    /** AdminBridge entry point; does not impersonate a player or bypass durable cooldowns. */
    public void requestAdmin(String requestedGamemode, Consumer<String> completion) {
        if (!running) {
            completion.accept(UNAVAILABLE);
            return;
        }
        String gamemode = requestedGamemode == null ? null : normalizeGamemode(requestedGamemode);
        if (gamemode == null) {
            completion.accept("Unknown gamemode. Use MicroBattles, Pitchout, SkyWars, BuildBattles, TurfWars, or BedWars.");
            return;
        }
        Game game = GameManager.getGames().stream()
                .filter(candidate -> gamemode.equals(gamemodeId(candidate.getGameName())))
                .filter(candidate -> queueState(candidate).underfilled())
                .findFirst().orElse(null);
        if (game == null) {
            completion.accept("No underfilled open queue exists for this gamemode.");
            return;
        }
        if (tracker.hasPending(game.getGameId())) {
            completion.accept("A player call is already scheduled for this queue.");
            return;
        }
        if (!automaticInFlight.add(game.getGameId())) {
            completion.accept("A player call for this queue is already being checked.");
            return;
        }
        Player target = firstOnlinePlayer(game);
        if (target == null) {
            automaticInFlight.remove(game.getGameId());
            completion.accept("The queue no longer has an online player.");
            return;
        }
        enqueue(game, RallyRepository.Source.AUTOMATIC, null, target, message -> completion.accept(
                message == null || message.isBlank() ? "Player call scheduled." : message));
    }

    public void shutdown(Duration timeout) {
        running = false;
        Set<UUID> cancellations = new HashSet<>(acceptedUnconfirmed);
        tracker.allPending().forEach(pending -> cancellations.add(pending.outboxId()));
        loginTracker.allPending().forEach(pending -> cancellations.add(pending.outboxId()));
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

    private void enqueue(Game game, RallyRepository.Source source, Player actor, Player target,
            Consumer<String> completion) {
        UUID gameId = game == null ? null : game.getGameId();
        String actorName = actor == null ? null : publicPlayerName(actor);
        RallyRepository.Request request = new RallyRepository.Request(
                UUID.randomUUID(),
                source,
                game == null ? RallyRepository.NETWORK_GAMEMODE : gamemodeId(game.getGameName()),
                game == null ? 0 : game.getPlayerCount(),
                game == null ? 0 : Math.max(0, game.getMinimumPlayers() - game.getPlayerCount()),
                actorName,
                target.getUniqueId(),
                gameId);

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
                runOnMain(() -> handleEnqueueResult(request, actor, result, completion));
            } catch (RuntimeException error) {
                markFailure("enqueue a player call", error);
                runOnMain(() -> {
                    clearInFlight(gameId, source, actor);
                    completion.accept(UNAVAILABLE);
                });
            }
        });
    }

    private void handleEnqueueResult(RallyRepository.Request request, Player actor,
            RallyRepository.EnqueueResult result, Consumer<String> completion) {
        UUID gameId = request.gameId();
        RallyRepository.Source source = request.source();
        if (result.status() == RallyRepository.EnqueueStatus.ENQUEUED) {
            acceptedUnconfirmed.remove(result.outboxId());
        }
        clearInFlight(gameId, source, actor);
        if (!running) {
            return;
        }
        if (result.status() != RallyRepository.EnqueueStatus.ENQUEUED) {
            if (source == RallyRepository.Source.LOGIN) {
                sendRecentCall(Bukkit.getPlayer(request.targetPlayerId()));
                completion.accept("");
                return;
            }
            completion.accept(message(result.status()));
            return;
        }

        Game current = gameId == null ? null : GameManager.getGames().stream()
                .filter(game -> game.getGameId().equals(gameId))
                .findFirst().orElse(null);
        RallyQueueTracker.QueueState currentState = current == null ? null : queueState(current);
        Player target = Bukkit.getPlayer(request.targetPlayerId());
        boolean stillRelevant = switch (source) {
            case LOGIN -> target != null && target.isOnline() && Bukkit.getOnlinePlayers().size() <= 1;
            case AUTOMATIC -> target != null && target.isOnline()
                    && currentState != null && currentState.queuedCount() > 0;
            case PLAYER -> target != null && target.isOnline()
                    && currentState != null && currentState.underfilled();
        };
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
        if (gameId != null) {
            tracker.markScheduled(gameId, result.outboxId(), releaseAt, nextAutomatic,
                    source == RallyRepository.Source.PLAYER);
        } else if (source == RallyRepository.Source.LOGIN) {
            loginTracker.track(request.targetPlayerId(), result.outboxId(),
                    releaseAt + LOGIN_RALLY_LIFETIME.toMillis());
        }
        sendCallLaunched(target, source, current);
        completion.accept(source == RallyRepository.Source.AUTOMATIC ? "Player call scheduled." : "");
    }

    private void clearInFlight(UUID gameId, RallyRepository.Source source, Player actor) {
        if (source == RallyRepository.Source.AUTOMATIC) {
            automaticInFlight.remove(gameId);
        } else if (actor != null) {
            manualInFlight.remove(actor.getUniqueId());
        }
    }

    private void pollResponses(long now) {
        if (responsePollInFlight || now < nextResponsePollAt) {
            return;
        }
        responsePollInFlight = true;
        nextResponsePollAt = now + RESPONSE_POLL_INTERVAL_MS;
        worker.execute(() -> {
            try {
                List<RallyRepository.Response> responses = repository.pendingResponses(RESPONSE_POLL_LIMIT);
                runOnMain(() -> deliverResponses(responses));
            } catch (RuntimeException error) {
                markFailure("poll player rally responses", error);
                runOnMain(() -> responsePollInFlight = false);
            }
        });
    }

    private void deliverResponses(List<RallyRepository.Response> responses) {
        List<RallyResponseDeliveryTracker.Delivery> deliveries = responseDeliveries.ready(
                responses, playerId -> {
                    Player player = Bukkit.getPlayer(playerId);
                    return player != null && player.isOnline();
                });
        List<UUID> acknowledgements = new java.util.ArrayList<>();
        for (RallyResponseDeliveryTracker.Delivery delivery : deliveries) {
            Player target = Bukkit.getPlayer(delivery.response().targetPlayerId());
            if (delivery.firstDisplay()) {
                if (target == null || !target.isOnline()) {
                    responseDeliveries.displayFailed(delivery.response().id());
                    continue;
                }
                target.sendMessage(responseMessage(delivery.response(), target.locale()));
            }
            acknowledgements.add(delivery.response().id());
        }
        if (acknowledgements.isEmpty()) {
            responsePollInFlight = false;
            return;
        }
        worker.execute(() -> {
            for (UUID responseId : acknowledgements) {
                try {
                    repository.markResponseDelivered(responseId);
                    responseDeliveries.acknowledged(responseId);
                } catch (RuntimeException error) {
                    markFailure("acknowledge a player rally response", error);
                }
            }
            runOnMain(() -> responsePollInFlight = false);
        });
    }

    private static void sendCallLaunched(Player target, RallyRepository.Source source, Game game) {
        if (target == null || !target.isOnline()) {
            return;
        }
        String key = source == RallyRepository.Source.LOGIN
                ? "rally.call.login.launched"
                : "rally.call.game.launched";
        String gameName = game == null ? "Cookie Build" : game.getGameName();
        target.sendMessage(ChatColor.LIGHT_PURPLE + LocaleManager.getMessage(key, target.locale(), gameName));
    }

    private static void sendRecentCall(Player target) {
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.LIGHT_PURPLE + LocaleManager.getMessage(
                    "rally.call.recent", target.locale()));
        }
    }

    static Component responseMessage(RallyRepository.Response response, Locale locale) {
        String responder = sanitizePlayerName(response.responderDisplayName());
        String gamemode = displayGamemode(response.gamemode());
        if (response.response() == RallyRepository.ResponseKind.JOINING) {
            return Component.text(LocaleManager.getMessage(
                    "rally.response.joining", locale, responder, gamemode), NamedTextColor.GREEN);
        }
        Component friendAction = Component.text(LocaleManager.getMessage(
                        "rally.response.friend_action", locale, responder), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/friend add " + responder))
                .hoverEvent(HoverEvent.showText(Component.text(LocaleManager.getMessage(
                        "rally.response.friend_hover", locale, responder))));
        return Component.text(LocaleManager.getMessage(
                        "rally.response.unavailable", locale, responder, gamemode), NamedTextColor.YELLOW)
                .append(Component.space())
                .append(friendAction);
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

    private static Player firstOnlinePlayer(Game game) {
        if (game == null) {
            return null;
        }
        return game.getPlayers().stream()
                .map(CookiePlayer::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .findFirst()
                .orElse(null);
    }

    private static boolean isSoloOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline() && Bukkit.getOnlinePlayers().size() <= 1;
    }

    static String normalizeGamemode(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return RallyRepository.SUPPORTED_GAMEMODES.contains(normalized) ? normalized : null;
    }

    static String gamemodeId(String gameName) {
        return normalizeGamemode(gameName);
    }

    static String displayGamemode(String gamemode) {
        return switch (gamemode) {
            case "microbattles" -> "MicroBattles";
            case "pitchout" -> "Pitchout";
            case "skywars" -> "SkyWars";
            case "buildbattles" -> "BuildBattles";
            case "turfwars" -> "TurfWars";
            case "bedwars" -> "BedWars";
            case RallyRepository.NETWORK_GAMEMODE -> "Cookie Build";
            default -> "Cookie Build";
        };
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
