package com.cookiebuild.cookiedough.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.admin.moderation.ModerationAction;
import com.cookiebuild.cookiedough.admin.moderation.ModerationService;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Executes only the closed, typed command set. There is no console/RCON path. */
final class AdminCommandExecutor implements AutoCloseable {
    private static final long MAX_DURATION_SECONDS = 365L * 24L * 60L * 60L;
    private final CookieDough plugin;
    private final ObjectMapper mapper;
    private final ModerationService moderation;
    private final AdminServerState serverState;
    private final ExecutorService persistenceWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CookieDough-admin-persistence");
        thread.setDaemon(true);
        return thread;
    });

    AdminCommandExecutor(CookieDough plugin, ObjectMapper mapper, ModerationService moderation,
            AdminServerState serverState) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.moderation = moderation;
        this.serverState = serverState;
    }

    CompletableFuture<AdminCommandResult> execute(AdminCommand command) {
        try {
            return switch (command.type()) {
                case BAN_TEMP, BAN_PERMANENT, MUTE -> createModerationAction(command);
                case UNMUTE -> revokeModeration(command, ModerationAction.Type.MUTE);
                case UNBAN -> revokeModeration(command, ModerationAction.Type.BAN);
                case RALLY -> executeRally(command);
                default -> onMain(() -> executeOnMain(command));
            };
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(AdminCommandResult.failed(error.getMessage()));
        }
    }

    private AdminCommandResult executeOnMain(AdminCommand command) {
        return switch (command.type()) {
            case MESSAGE_ALL -> message(Bukkit.getOnlinePlayers().stream().toList(), requiredMessage(command), "all");
            case MESSAGE_LOBBY -> message(PlayerManager.getPlayers().stream()
                    .filter(player -> player.getState() == PlayerState.LOBBY)
                    .map(CookiePlayer::getPlayer).toList(), requiredMessage(command), "lobby");
            case MESSAGE_GAME -> {
                Game game = requireGame(command.targetId());
                yield message(game.getPlayers().stream().map(CookiePlayer::getPlayer).toList(),
                        requiredMessage(command), "game:" + game.getGameId());
            }
            case MESSAGE_PLAYER -> message(List.of(requireOnlinePlayer(command.targetId())), requiredMessage(command),
                    "player:" + command.targetId());
            case KICK -> {
                Player player = requireOnlinePlayer(command.targetId());
                String reason = text(command.payload(), "reason", "Removed by an administrator", 500);
                player.kickPlayer(reason);
                yield completed().put("player_id", player.getUniqueId().toString()).put("kicked", true).build();
            }
            case RETURN_LOBBY -> {
                Player player = requireOnlinePlayer(command.targetId());
                CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
                if (cookiePlayer == null) throw new IllegalArgumentException("Player wrapper is not ready");
                LobbyManager.teleportPlayerToLobby(cookiePlayer);
                yield completed().put("player_id", player.getUniqueId().toString()).put("returned_to_lobby", true).build();
            }
            case CLOSE_ADMISSIONS -> {
                Game game = requireGame(command.targetId());
                game.closeAdmissions();
                yield gameResult(game, "admissions_closed");
            }
            case REOPEN_ADMISSIONS -> {
                Game game = requireGame(command.targetId());
                if (!game.reopenAdmissions()) throw new IllegalStateException("Only an open game can accept admissions");
                yield gameResult(game, "admissions_reopened");
            }
            case SAFE_CANCEL -> safeCancel(command);
            case MAINTENANCE -> maintenance(command);
            case DRAIN -> drain(command);
            case RESTART_READY -> restartReady();
            case BAN_TEMP, BAN_PERMANENT, MUTE, UNMUTE, UNBAN, RALLY -> throw new IllegalStateException("Command routed incorrectly");
        };
    }

    private CompletableFuture<AdminCommandResult> createModerationAction(AdminCommand command) {
        UUID playerId = requiredUuid(command.targetId(), "player target");
        String playerName = text(command.payload(), "player_name", "Unknown player", 64);
        String reason = text(command.payload(), "reason", "No reason supplied", 500);
        String actorId = requiredActorId(command.payload().path("actor_id").asText(null));
        String actorName = text(command.payload(), "actor_display_name", "Admin", 120);
        Instant now = Instant.now();
        ModerationAction.Type actionType = command.type() == AdminCommand.Type.MUTE
                ? ModerationAction.Type.MUTE : ModerationAction.Type.BAN;
        Instant expiresAt = switch (command.type()) {
            case BAN_PERMANENT -> null;
            case BAN_TEMP -> now.plus(requiredDuration(command.payload()), ChronoUnit.SECONDS);
            case MUTE -> optionalDuration(command.payload(), now);
            default -> throw new IllegalStateException("Not a moderation command");
        };
        JsonNode metadata = command.payload().path("metadata");
        ModerationAction action = new ModerationAction(
                UUID.randomUUID(), playerId, playerName, actionType, reason, actorId, actorName,
                now, expiresAt, command.id(), metadata.isObject() ? metadata.toString() : "{}", now);

        return CompletableFuture.supplyAsync(() -> moderation.create(action), persistenceWorker)
                .thenCompose(persisted -> onMain(() -> {
                    if (persisted.type() == ModerationAction.Type.BAN) {
                        Player current = Bukkit.getPlayer(playerId);
                        if (current != null) current.kickPlayer("Banned: " + persisted.reason());
                    }
                    ObjectNode result = mapper.createObjectNode();
                    result.put("moderation_action_id", persisted.id().toString());
                    result.put("player_id", playerId.toString());
                    result.put("action_type", persisted.type().wireValue());
                    if (persisted.expiresAt() != null) result.put("expires_at", persisted.expiresAt().toString());
                    else result.putNull("expires_at");
                    return AdminCommandResult.completed(result);
                })).exceptionally(error -> AdminCommandResult.failed(rootMessage(error)));
    }

    private CompletableFuture<AdminCommandResult> revokeModeration(
            AdminCommand command, ModerationAction.Type actionType) {
        UUID playerId = requiredUuid(command.targetId(), "player target");
        String actorId = requiredActorId(command.payload().path("actor_id").asText(null));
        return CompletableFuture.supplyAsync(
                () -> moderation.revoke(playerId, actionType, actorId), persistenceWorker)
                .thenApply(count -> {
                    ObjectNode result = mapper.createObjectNode();
                    result.put("player_id", playerId.toString());
                    result.put("action_type", actionType.wireValue());
                    result.put("revoked_actions", count);
                    return AdminCommandResult.completed(result);
                }).exceptionally(error -> AdminCommandResult.failed(rootMessage(error)));
    }

    private CompletableFuture<AdminCommandResult> executeRally(AdminCommand command) {
        String gamemode = text(command.payload(), "gamemode", command.targetId(), 64);
        CompletableFuture<AdminCommandResult> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getRallyManager().requestAdmin(gamemode, message -> {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("gamemode", gamemode);
            payload.put("message", message);
            boolean accepted = message.startsWith("Player call scheduled");
            if (accepted) result.complete(AdminCommandResult.completed(payload));
            else result.complete(AdminCommandResult.failed(message));
        }));
        return result;
    }

    private AdminCommandResult safeCancel(AdminCommand command) {
        Game game = requireGame(command.targetId());
        String reason = text(command.payload(), "reason", "Game cancelled by an administrator", 500);
        game.closeAdmissions();
        List<CookiePlayer> players = game.getPlayers();
        for (CookiePlayer player : players) {
            if (player.getPlayer().isOnline()) {
                player.getPlayer().sendMessage(Component.text(reason, NamedTextColor.YELLOW));
            }
        }
        game.shutdown();
        ObjectNode result = mapper.createObjectNode();
        result.put("game_id", game.getGameId().toString());
        result.put("cancelled", true);
        result.put("players_returned", players.size());
        return AdminCommandResult.completed(result);
    }

    private AdminCommandResult maintenance(AdminCommand command) {
        boolean enabled = command.payload().path("enabled").asBoolean(true);
        serverState.setMaintenance(enabled);
        GameManager.setGlobalAdmissionsOpen(!enabled);
        String defaultMessage = enabled
                ? "Server maintenance is starting; new connections are temporarily disabled."
                : "Server maintenance has ended.";
        String message = text(command.payload(), "message", defaultMessage, 500);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(Component.text(message, NamedTextColor.YELLOW)));
        ObjectNode result = mapper.createObjectNode();
        result.put("maintenance", enabled);
        result.put("online_players", Bukkit.getOnlinePlayers().size());
        return AdminCommandResult.completed(result);
    }

    private AdminCommandResult drain(AdminCommand command) {
        serverState.startDrain();
        GameManager.setGlobalAdmissionsOpen(false);
        String message = text(command.payload(), "message",
                "The server is preparing for maintenance. No new games will start.", 500);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(Component.text(message, NamedTextColor.YELLOW)));
        ObjectNode result = mapper.createObjectNode();
        result.put("draining", true);
        result.put("online_players", Bukkit.getOnlinePlayers().size());
        result.put("active_games", activeGameCount());
        return AdminCommandResult.completed(result);
    }

    private AdminCommandResult restartReady() {
        int players = Bukkit.getOnlinePlayers().size();
        long activeGames = activeGameCount();
        ObjectNode result = mapper.createObjectNode();
        result.put("ready", players == 0 && activeGames == 0);
        result.put("online_players", players);
        result.put("active_games", activeGames);
        result.put("maintenance", serverState.maintenance());
        result.put("draining", serverState.draining());
        return AdminCommandResult.completed(result);
    }

    private long activeGameCount() {
        return GameManager.getGames().stream()
                .filter(game -> game.getState() == GameState.RUNNING || game.getPlayerCount() > 0).count();
    }

    private AdminCommandResult message(List<? extends Player> recipients, String message, String audience) {
        Component component = Component.text("[Cookie Build] " + message, NamedTextColor.GOLD);
        recipients.forEach(player -> player.sendMessage(component));
        ObjectNode result = mapper.createObjectNode();
        result.put("audience", audience);
        result.put("recipients", recipients.size());
        return AdminCommandResult.completed(result);
    }

    private AdminCommandResult gameResult(Game game, String action) {
        ObjectNode result = mapper.createObjectNode();
        result.put("game_id", game.getGameId().toString());
        result.put("action", action);
        result.put("admissions_open", game.isAdmissionsOpen());
        return AdminCommandResult.completed(result);
    }

    private Game requireGame(String targetId) {
        Game game = null;
        try {
            game = GameManager.getGameById(UUID.fromString(targetId));
        } catch (IllegalArgumentException ignored) { }
        if (game == null) game = GameManager.getGames().stream()
                .filter(candidate -> candidate.getGameName().equalsIgnoreCase(targetId)).findFirst().orElse(null);
        if (game == null) throw new IllegalArgumentException("Game not found: " + targetId);
        return game;
    }

    private Player requireOnlinePlayer(String targetId) {
        Player player = null;
        try {
            player = Bukkit.getPlayer(UUID.fromString(targetId));
        } catch (IllegalArgumentException ignored) { }
        if (player == null) player = Bukkit.getPlayerExact(targetId);
        if (player == null || !player.isOnline()) throw new IllegalArgumentException("Player is not online: " + targetId);
        return player;
    }

    private String requiredMessage(AdminCommand command) {
        String message = text(command.payload(), "message", null, 2_000);
        if (message == null) throw new IllegalArgumentException("A non-empty message is required");
        return message;
    }

    private static String text(ObjectNode payload, String field, String fallback, int maximumLength) {
        JsonNode node = payload.get(field);
        String value = node != null && node.isTextual() ? node.asText().trim() : fallback;
        if (value == null || value.isBlank()) return fallback;
        value = value.replaceAll("[\\r\\n\\t]", " ");
        if (value.length() > maximumLength) throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        return value;
    }

    private static long requiredDuration(ObjectNode payload) {
        if (!payload.has("duration_seconds") || !payload.path("duration_seconds").canConvertToLong()) {
            throw new IllegalArgumentException("duration_seconds is required for a temporary ban");
        }
        long duration = payload.path("duration_seconds").asLong();
        if (duration < 60L || duration > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("duration_seconds must be between 60 and " + MAX_DURATION_SECONDS);
        }
        return duration;
    }

    private static Instant optionalDuration(ObjectNode payload, Instant now) {
        if (!payload.has("duration_seconds") || payload.path("duration_seconds").isNull()) return null;
        return now.plus(requiredDuration(payload), ChronoUnit.SECONDS);
    }

    private static UUID requiredUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static String requiredActorId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("actor_id is required");
        }
        String actorId = value.trim();
        if (actorId.length() > 128 || actorId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("actor_id must contain 1 to 128 non-control characters");
        }
        return actorId;
    }

    private CompletableFuture<AdminCommandResult> onMain(Supplier<AdminCommandResult> action) {
        CompletableFuture<AdminCommandResult> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                result.complete(action.get());
            } catch (RuntimeException error) {
                result.complete(AdminCommandResult.failed(error.getMessage()));
            }
        });
        return result;
    }

    private ResultBuilder completed() {
        return new ResultBuilder(mapper.createObjectNode());
    }

    private record ResultBuilder(ObjectNode node) {
        ResultBuilder put(String field, String value) { node.put(field, value); return this; }
        ResultBuilder put(String field, boolean value) { node.put(field, value); return this; }
        AdminCommandResult build() { return AdminCommandResult.completed(node); }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public void close() {
        persistenceWorker.shutdown();
        try {
            if (!persistenceWorker.awaitTermination(5, TimeUnit.SECONDS)) persistenceWorker.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            persistenceWorker.shutdownNow();
        }
    }
}
