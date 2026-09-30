package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Asynchronous in-game facade for the friend graph shared with the mobile app. */
public final class FriendManager {
    /** Friend graph plus the viewer's blocks, loaded in one worker round-trip for the social menu. */
    public record MenuSnapshot(FriendRepository.Snapshot friends, Set<UUID> blocked) {
        public MenuSnapshot {
            blocked = Set.copyOf(blocked);
        }
    }

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
        submit(actor, target.actorId(), "send friend request",
                () -> repository.request(target.actorId(), target.actorName(), target.targetName()),
                result -> completion.accept(switch (result) {
                    case REQUESTED -> {
                        notifyOnline(target.targetName(), NamedTextColor.GOLD, true,
                                "friend.request.received", target.actorName());
                        yield message(actor, "friend.request.sent", target.targetName());
                    }
                    case ACCEPTED -> {
                        notifyOnline(target.targetName(), NamedTextColor.GREEN, false,
                                "friend.now_friends", target.actorName());
                        yield message(actor, "friend.now_friends", target.targetName());
                    }
                    case ALREADY_REQUESTED -> message(actor, "friend.request.already_sent", target.targetName());
                    case ALREADY_FRIENDS -> message(actor, "friend.request.already_friends", target.targetName());
                    case PLAYER_NOT_FOUND -> message(actor, "friend.error.not_found");
                    case PLAYER_NAME_AMBIGUOUS -> message(actor, "friend.error.ambiguous");
                    case SELF -> message(actor, "friend.request.self");
                    case TOO_MANY_PENDING -> message(actor, "friend.request.too_many");
                    case RECENTLY_REQUESTED -> message(actor, "friend.request.recent");
                    case UNAVAILABLE -> message(actor, "friend.error.player_unavailable");
                }), completion);
    }

    public void toggleBlock(Player actor, Player target,
            Consumer<FriendRepository.BlockResult> completion, Consumer<String> failure) {
        if (!profileReady(actor, failure)) return;
        if (!PlayerWrapperListener.isPlayerDataReady(target.getUniqueId())) {
            failure.accept(message(actor, "friend.error.target_loading"));
            return;
        }
        submit(actor, actor.getUniqueId(), "update player block",
                () -> repository.toggleBlock(actor.getUniqueId(), target.getUniqueId()), completion, failure);
    }

    public void report(Player actor, Player target, String reason,
            Consumer<FriendRepository.ReportResult> completion, Consumer<String> failure) {
        if (!profileReady(actor, failure)) return;
        if (!PlayerWrapperListener.isPlayerDataReady(target.getUniqueId())) {
            failure.accept(message(actor, "friend.error.target_loading"));
            return;
        }
        submit(actor, actor.getUniqueId(), "record player report",
                () -> repository.report(actor.getUniqueId(), target.getUniqueId(), reason), completion, failure);
    }

    public void loadBlocks(Player actor, Consumer<Set<UUID>> completion, Consumer<String> failure) {
        if (!profileReady(actor, failure)) return;
        submit(actor, actor.getUniqueId(), "load player blocks",
                () -> repository.blockedPlayers(actor.getUniqueId()), completion, failure);
    }

    /**
     * Loads the friend graph and the viewer's blocks off the main thread; both
     * callbacks run on the Paper main thread.
     */
    public void loadMenu(Player actor, Consumer<MenuSnapshot> completion, Consumer<String> failure) {
        if (!profileReady(actor, failure)) return;
        UUID actorId = actor.getUniqueId();
        submit(actor, actorId, "load social menu",
                () -> new MenuSnapshot(repository.snapshot(actorId), repository.blockedPlayers(actorId)),
                completion, failure);
    }

    public void accept(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        submit(actor, target.actorId(), "accept friend request",
                () -> repository.accept(target.actorId(), target.targetName()),
                result -> completion.accept(switch (result) {
                    case ACCEPTED -> {
                        notifyOnline(target.targetName(), NamedTextColor.GREEN, false,
                                "friend.accept.notify", target.actorName());
                        yield message(actor, "friend.now_friends", target.targetName());
                    }
                    case REQUEST_NOT_FOUND -> message(actor, "friend.accept.missing", target.targetName());
                    case PLAYER_NOT_FOUND -> message(actor, "friend.error.not_found");
                    case PLAYER_NAME_AMBIGUOUS -> message(actor, "friend.error.ambiguous");
                    case UNAVAILABLE -> message(actor, "friend.error.player_unavailable");
                }), completion);
    }

    public void deny(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        delete(actor, target.actorId(), "deny friend request",
                () -> repository.deny(target.actorId(), target.targetName()),
                message(actor, "friend.deny.done"), message(actor, "friend.accept.missing", target.targetName()),
                completion);
    }

    public void remove(Player actor, String targetName, Consumer<String> completion) {
        Target target = prepare(actor, targetName, completion);
        if (target == null) return;
        delete(actor, target.actorId(), "remove friend",
                () -> repository.remove(target.actorId(), target.targetName()),
                message(actor, "friend.remove.done", target.targetName()),
                message(actor, "friend.remove.missing", target.targetName()), completion);
    }

    public void describe(Player actor, Consumer<String> completion) {
        UUID actorId = actor.getUniqueId();
        if (!profileReady(actor, completion)) return;
        submit(actor, actorId, "list friends", () -> repository.snapshot(actorId), snapshot -> {
            List<String> friends = snapshot.friends().stream()
                    .map(friend -> (friend.online() ? ChatColor.GREEN : ChatColor.GRAY) + friend.name())
                    .toList();
            StringBuilder text = new StringBuilder();
            text.append(ChatColor.GOLD).append(message(actor, "friend.list.header")).append(' ')
                    .append(friends.isEmpty()
                            ? ChatColor.GRAY + message(actor, "friend.list.none")
                            : String.join(ChatColor.YELLOW + ", ", friends));
            if (!snapshot.incoming().isEmpty()) {
                text.append("\n").append(ChatColor.AQUA).append(message(actor, "friend.list.incoming")).append(' ')
                        .append(String.join(", ", snapshot.incoming()))
                        .append(ChatColor.GRAY).append(" — ").append(message(actor, "friend.list.incoming_hint"));
            }
            if (!snapshot.outgoing().isEmpty()) {
                text.append("\n").append(ChatColor.YELLOW).append(message(actor, "friend.list.outgoing")).append(' ')
                        .append(String.join(", ", snapshot.outgoing()));
            }
            text.append("\n").append(ChatColor.GRAY).append(message(actor, "friend.list.menu_hint"));
            completion.accept(text.toString());
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

    private void delete(Player actor, UUID actorId, String operation, Supplier<FriendRepository.DeleteResult> action,
            String success, String missing, Consumer<String> completion) {
        submit(actor, actorId, operation, action, result -> completion.accept(switch (result) {
            case DELETED -> success;
            case RELATIONSHIP_NOT_FOUND -> missing;
            case PLAYER_NOT_FOUND -> message(actor, "friend.error.not_found");
            case PLAYER_NAME_AMBIGUOUS -> message(actor, "friend.error.ambiguous");
        }), completion);
    }

    private <T> void submit(Player actor, UUID actorId, String operation, Supplier<T> action,
            Consumer<T> success, Consumer<String> failure) {
        if (!inFlight.add(actorId)) {
            failure.accept(message(actor, "friend.error.busy"));
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
                    callback = () -> failure.accept(message(actor, "friend.error.unavailable"));
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
            failure.accept(message(actor, "friend.error.unavailable"));
        }
    }

    /**
     * Plain text is the whole call to action (Bedrock cannot click chat); Java
     * players may additionally click the line to open the social menu.
     */
    private static void notifyOnline(String playerName, NamedTextColor color, boolean openMenuOnClick,
            String key, Object... args) {
        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(playerName))
                .findFirst().orElse(null);
        if (target == null || !target.isOnline()) return;
        Component line = Component.text(message(target, key, args), color);
        if (openMenuOnClick) {
            line = line.clickEvent(ClickEvent.runCommand("/amis"))
                    .hoverEvent(HoverEvent.showText(Component.text("/amis", NamedTextColor.YELLOW)));
        }
        target.sendMessage(line);
    }

    private static boolean profileReady(Player actor, Consumer<String> completion) {
        if (PlayerWrapperListener.isPlayerDataReady(actor.getUniqueId())) return true;
        completion.accept(message(actor, "friend.error.profile_loading"));
        return false;
    }

    private static Target prepare(Player actor, String rawTargetName, Consumer<String> completion) {
        if (!profileReady(actor, completion)) return null;
        String targetName = rawTargetName == null ? "" : rawTargetName.trim();
        if (!isValidName(targetName)) {
            completion.accept(message(actor, "friend.error.invalid_name"));
            return null;
        }
        String actorName = actor.getName();
        if (actorName.equalsIgnoreCase(targetName)) {
            completion.accept(message(actor, "friend.error.self"));
            return null;
        }
        return new Target(actor.getUniqueId(), actorName, targetName);
    }

    /** Same name contract as the repository lookup (Java names and Floodgate-prefixed Bedrock names). */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_ .-]{1,32}");
    }

    private record Target(UUID actorId, String actorName, String targetName) {
    }

    private static String message(Player player, String key, Object... args) {
        Locale locale = player == null ? Locale.ENGLISH : player.locale();
        return LocaleManager.getMessage(key, locale, args);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
