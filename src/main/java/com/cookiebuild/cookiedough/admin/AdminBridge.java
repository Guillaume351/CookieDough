package com.cookiebuild.cookiedough.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.admin.moderation.ModerationService;
import com.cookiebuild.cookiedough.admin.moderation.PostgresModerationRepository;
import com.cookiebuild.cookiedough.game.GameManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Secure, typed control-plane bridge. Disabled unless ADMIN_BRIDGE_ENABLED=true. */
public final class AdminBridge implements AutoCloseable {
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private final CookieDough plugin;
    private final AdminBridgeConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdminRabbitClient rabbit;
    private final AdminServerState serverState = new AdminServerState();
    private final ModerationService moderation;
    private final AdminCommandExecutor commandExecutor;
    private final AdminCommandRepository commandRepository;
    private final CommandDeduplicator deduplicator = new CommandDeduplicator(Duration.ofMinutes(30));
    private final AdminSnapshotFactory snapshots;
    private final ExecutorService persistenceWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CookieDough-admin-command-status");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong lastPublishFailure = new AtomicLong();
    private final AdminModerationListener moderationListener;
    private final AdminPlayerEventListener playerEventListener;
    private final GameManager.GameLifecycleListener gameLifecycleListener = this::publishGameEvent;
    private BukkitTask snapshotTask;

    public AdminBridge(CookieDough plugin, AdminBridgeConfig config) {
        if (!config.enabled()) throw new IllegalArgumentException("AdminBridge cannot start from a disabled configuration");
        this.plugin = plugin;
        this.config = config;
        rabbit = new AdminRabbitClient(config, plugin.getLogger());
        moderation = new ModerationService(new PostgresModerationRepository(plugin.getLogger()));
        commandExecutor = new AdminCommandExecutor(plugin, mapper, moderation, serverState);
        commandRepository = new PostgresAdminCommandRepository(mapper, plugin.getLogger());
        snapshots = new AdminSnapshotFactory(mapper, serverState, rabbit);
        moderationListener = new AdminModerationListener(moderation, serverState);
        playerEventListener = new AdminPlayerEventListener(this);
    }

    public static Optional<AdminBridge> createIfEnabled(CookieDough plugin) {
        AdminBridgeConfig config;
        try {
            config = AdminBridgeConfig.fromEnvironment();
        } catch (RuntimeException error) {
            plugin.getLogger().severe("AdminBridge configuration is invalid; bridge remains disabled: " + error.getMessage());
            return Optional.empty();
        }
        if (!config.enabled()) {
            plugin.getLogger().info("AdminBridge is disabled (set ADMIN_BRIDGE_ENABLED=true to opt in)");
            return Optional.empty();
        }
        return Optional.of(new AdminBridge(plugin, config));
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(moderationListener, plugin);
        Bukkit.getPluginManager().registerEvents(playerEventListener, plugin);
        GameManager.setLifecycleListener(gameLifecycleListener);
        rabbit.start(this::handleDelivery);
        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::publishSnapshot,
                20L, config.snapshotIntervalTicks());
        publishEvent("bridge_started", mapper.createObjectNode().put("command_queue", config.commandQueue()));
        plugin.getLogger().info("AdminBridge enabled with typed commands only; console and RCON are not exposed");
    }

    public ModerationService moderation() {
        return moderation;
    }

    private CompletionStage<Void> handleDelivery(String body) {
        AdminCommand command;
        try {
            command = AdminCommand.parse(mapper, body);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Discarding malformed AdminBridge command: " + safeMessage(error));
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<AdminCommandResult> result = deduplicator.executeOnce(
                command.idempotencyKey(), () -> executeDurably(command));
        return result.thenCompose(outcome -> publishCommandResult(command, outcome));
    }

    private CompletableFuture<AdminCommandResult> executeDurably(AdminCommand command) {
        if (command.expiredAt(Instant.now())) {
            AdminCommandResult expired = AdminCommandResult.expired();
            return recordCompleted(command.id(), expired);
        }
        return CompletableFuture.supplyAsync(() -> commandRepository.findTerminal(command.id()), persistenceWorker)
                .thenCompose(existing -> {
                    if (existing.isPresent()) return CompletableFuture.completedFuture(existing.get());
                    return CompletableFuture.runAsync(() -> commandRepository.recordStarted(command.id()), persistenceWorker)
                            .thenCompose(ignored -> commandExecutor.execute(command))
                            .thenCompose(result -> recordCompleted(command.id(), result));
                }).exceptionally(error -> AdminCommandResult.failed(safeMessage(error)));
    }

    private CompletableFuture<AdminCommandResult> recordCompleted(UUID commandId, AdminCommandResult result) {
        return CompletableFuture.supplyAsync(() -> {
            commandRepository.recordCompleted(commandId, result);
            return result;
        }, persistenceWorker);
    }

    private CompletableFuture<Void> publishCommandResult(AdminCommand command, AdminCommandResult result) {
        ObjectNode data = mapper.createObjectNode();
        data.put("command_id", command.id().toString());
        data.put("idempotency_key", command.idempotencyKey());
        data.put("status", result.status());
        data.set("result", result.result());
        if (result.error() == null) data.putNull("error");
        else data.put("error", result.error());
        return publishEnvelope("command_result", data);
    }

    private void publishSnapshot() {
        publishEnvelope("snapshot", snapshots.capture()).exceptionally(error -> {
            logPublishFailure(error);
            return null;
        });
    }

    void publishPlayerEvent(String type, Player player) {
        ObjectNode data = mapper.createObjectNode();
        data.put("player_id", player.getUniqueId().toString());
        data.put("player_name", player.getName());
        data.put("online_players", Bukkit.getOnlinePlayers().size());
        publishEvent(type, data);
    }

    private void publishGameEvent(GameManager.GameLifecycleEvent event) {
        ObjectNode data = mapper.createObjectNode();
        data.put("kind", event.kind());
        data.put("game_id", event.gameId().toString());
        data.put("game_name", event.gameName());
        data.put("state", event.state().name());
        publishEvent("game_changed", data);
    }

    private void publishEvent(String type, ObjectNode data) {
        publishEnvelope(type, data).exceptionally(error -> {
            logPublishFailure(error);
            return null;
        });
    }

    private CompletableFuture<Void> publishEnvelope(String type, ObjectNode data) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("server_id", config.serverId());
        envelope.put("occurred_at", Instant.now().toString());
        envelope.set("data", data);
        return rabbit.publish(config.eventRoutingKey(type), envelope.toString());
    }

    private void logPublishFailure(Throwable error) {
        long now = System.currentTimeMillis();
        long previous = lastPublishFailure.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS && lastPublishFailure.compareAndSet(previous, now)) {
            plugin.getLogger().warning("AdminBridge event publication is unavailable; gameplay continues: "
                    + safeMessage(error));
        }
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) return root.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]", " ");
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    @Override
    public void close() {
        if (snapshotTask != null) snapshotTask.cancel();
        GameManager.clearLifecycleListener(gameLifecycleListener);
        HandlerList.unregisterAll(moderationListener);
        HandlerList.unregisterAll(playerEventListener);
        rabbit.close();
        commandExecutor.close();
        persistenceWorker.shutdown();
        try {
            if (!persistenceWorker.awaitTermination(5, TimeUnit.SECONDS)) persistenceWorker.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            persistenceWorker.shutdownNow();
        }
    }
}
