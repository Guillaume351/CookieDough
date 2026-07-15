package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;

/** Asynchronous in-game facade for the friend graph shared with the mobile app. */
public final class FriendManager {
    private static final String UNAVAILABLE = "Friend service is temporarily unavailable. Please try again.";

    private final CookieDough plugin;
    private final FriendRepository repository;
    private final ExecutorService worker;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public FriendManager(CookieDough plugin) {
        this(plugin, new PostgresFriendRepository(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cookie-build-friends");
            thread.setDaemon(true);
            return thread;
        }));
    }

    FriendManager(CookieDough plugin, FriendRepository repository, ExecutorService worker) {
        this.plugin = plugin;
        this.repository = repository;
        this.worker = worker;
    }

    public void request(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        submit(target.actorId(), "send friend request",
                () -> repository.request(target.actorId(), target.actorName(), target.targetName()),
                result -> completion.accept(switch (result) {
                    case REQUESTED -> {
                        notifyOnline(target.targetName(), ChatColor.GOLD + target.actorName()
                                + " sent you a friend request. "
                                + ChatColor.YELLOW + "Use /friend accept " + target.actorName()
                                + " or open the Cookie Build app.");
                        yield "Friend request sent to " + target.targetName() + ".";
                    }
                    case ACCEPTED -> {
                        notifyOnline(target.targetName(), ChatColor.GREEN + "You and " + target.actorName()
                                + " are now friends.");
                        yield "You and " + target.targetName() + " are now friends.";
                    }
                    case ALREADY_REQUESTED -> "You already sent " + target.targetName() + " a friend request.";
                    case ALREADY_FRIENDS -> "You and " + target.targetName() + " are already friends.";
                    case PLAYER_NOT_FOUND -> "Player not found. Use their exact Minecraft name.";
                    case PLAYER_NAME_AMBIGUOUS -> "That player name is ambiguous. Ask an admin for help.";
                    case SELF -> "You cannot add yourself as a friend.";
                    case UNAVAILABLE -> "That player is unavailable.";
                }), completion);
    }

    public void accept(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        submit(target.actorId(), "accept friend request",
                () -> repository.accept(target.actorId(), target.targetName()),
                result -> completion.accept(switch (result) {
                    case ACCEPTED -> {
                        notifyOnline(target.targetName(), ChatColor.GREEN + target.actorName()
                                + " accepted your friend request.");
                        yield "You and " + target.targetName() + " are now friends.";
                    }
                    case REQUEST_NOT_FOUND -> "No incoming friend request from " + target.targetName() + ".";
                    case PLAYER_NOT_FOUND -> "Player not found. Use their exact Minecraft name.";
                    case PLAYER_NAME_AMBIGUOUS -> "That player name is ambiguous. Ask an admin for help.";
                    case UNAVAILABLE -> "That player is unavailable.";
                }), completion);
    }

    public void deny(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        delete(target.actorId(), "deny friend request",
                () -> repository.deny(target.actorId(), target.targetName()),
                "Friend request declined.", "No incoming friend request from " + target.targetName() + ".",
                completion);
    }

    public void remove(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        delete(target.actorId(), "remove friend",
                () -> repository.remove(target.actorId(), target.targetName()),
                target.targetName() + " was removed from your friends.",
                target.targetName() + " is not in your friends.", completion);
    }

    public void describe(Player actor, Consumer<String> completion) {
        UUID actorId = actor.getUniqueId();
        if (!profileReady(actor, completion)) return;
        submit(actorId, "list friends", () -> repository.snapshot(actorId), snapshot -> {
            List<String> friends = snapshot.friends().stream()
                    .map(friend -> (friend.online() ? ChatColor.GREEN : ChatColor.GRAY) + friend.name())
                    .toList();
            StringBuilder message = new StringBuilder();
            message.append(ChatColor.GOLD).append("Friends (online in green): ")
                    .append(friends.isEmpty() ? ChatColor.GRAY + "none" : String.join(ChatColor.YELLOW + ", ", friends));
            if (!snapshot.incoming().isEmpty()) {
                message.append("\n").append(ChatColor.AQUA).append("Incoming: ")
                        .append(String.join(", ", snapshot.incoming()))
                        .append(ChatColor.GRAY).append(" — /friend accept <name>");
            }
            if (!snapshot.outgoing().isEmpty()) {
                message.append("\n").append(ChatColor.YELLOW).append("Sent: ")
                        .append(String.join(", ", snapshot.outgoing()));
            }
            completion.accept(message.toString());
        }, completion);
    }

    public void shutdown(Duration timeout) {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) worker.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    private void delete(UUID actorId, String operation, Supplier<FriendRepository.DeleteResult> action,
            String success, String missing, Consumer<String> completion) {
        submit(actorId, operation, action, result -> completion.accept(switch (result) {
            case DELETED -> success;
            case RELATIONSHIP_NOT_FOUND -> missing;
            case PLAYER_NOT_FOUND -> "Player not found. Use their exact Minecraft name.";
            case PLAYER_NAME_AMBIGUOUS -> "That player name is ambiguous. Ask an admin for help.";
        }), completion);
    }

    private <T> void submit(UUID actorId, String operation, Supplier<T> action,
            Consumer<T> success, Consumer<String> failure) {
        if (!inFlight.add(actorId)) {
            failure.accept("Your previous friend action is still being processed.");
            return;
        }
        try {
            worker.execute(() -> {
                Runnable callback;
                try {
                    T result = action.get();
                    callback = () -> success.accept(result);
                } catch (RuntimeException error) {
                    plugin.getLogger().warning("Could not " + operation + ": " + rootMessage(error));
                    callback = () -> failure.accept(UNAVAILABLE);
                }
                inFlight.remove(actorId);
                if (!plugin.isEnabled()) return;
                try {
                    Bukkit.getScheduler().runTask(plugin, callback);
                } catch (RuntimeException schedulingError) {
                    plugin.getLogger().warning("Could not deliver friend action result: "
                            + rootMessage(schedulingError));
                }
            });
        } catch (RejectedExecutionException rejected) {
            inFlight.remove(actorId);
            failure.accept(UNAVAILABLE);
        }
    }

    private static void notifyOnline(String playerName, String message) {
        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(playerName))
                .findFirst().orElse(null);
        if (target != null && target.isOnline()) target.sendMessage(message);
    }

    private static boolean profileReady(Player actor, Consumer<String> completion) {
        if (PlayerWrapperListener.isPlayerDataReady(actor.getUniqueId())) return true;
        completion.accept("Your player profile is still loading. Please try again shortly.");
        return false;
    }

    private static Target prepare(Player actor, String rawTargetName, Consumer<String> completion) {
        if (!profileReady(actor, completion)) return null;
        String targetName = rawTargetName == null ? "" : rawTargetName.trim();
        if (!targetName.matches("[A-Za-z0-9_ .-]{1,32}")) {
            completion.accept("Use the player's exact Minecraft name (1–32 letters, numbers, spaces, _ . or -).");
            return null;
        }
        String actorName = actor.getName();
        if (actorName.equalsIgnoreCase(targetName)) {
            completion.accept("You cannot target yourself.");
            return null;
        }
        return new Target(actor.getUniqueId(), actorName, targetName);
    }

    private record Target(UUID actorId, String actorName, String targetName) {
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
