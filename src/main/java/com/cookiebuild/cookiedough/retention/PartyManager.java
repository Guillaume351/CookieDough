package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Durable parties synchronized with the website/mobile party API. */
public final class PartyManager {
    private static final long REFRESH_TICKS = 40L;
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static final String UNAVAILABLE = "party.error.unavailable";

    record PartyGameSelection(Game game, String rejectionReason) {
    }

    private final CookieDough plugin;
    private final PartyCoordinator coordinator;
    private final ExecutorService worker;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final AtomicBoolean healthy = new AtomicBoolean();
    private final AtomicLong lastFailureLog = new AtomicLong();
    private volatile boolean initialized;
    private volatile BukkitTask refreshTask;

    public PartyManager(CookieDough plugin) {
        this(plugin, new PostgresPartyRepository(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cookie-build-parties");
            thread.setDaemon(true);
            return thread;
        }));
    }

    PartyManager(CookieDough plugin, PartyRepository repository, ExecutorService worker) {
        this.plugin = plugin;
        this.coordinator = new PartyCoordinator(repository);
        this.worker = worker;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        refreshAsync();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAsync,
                REFRESH_TICKS, REFRESH_TICKS);
    }

    public boolean isAvailable() {
        return initialized && healthy.get();
    }

    public void create(Player leader, Consumer<String> completion) {
        UUID leaderId = leader.getUniqueId();
        mutate(leader, "create party", () -> coordinator.create(leaderId), result -> {
            String message = result == PartyRepository.CreateResult.CREATED
                    ? message(leader, "party.create.done")
                    : message(leader, "party.already_in_party");
            completion.accept(message);
        }, completion);
    }

    public void invite(Player inviter, Player target, Consumer<String> completion) {
        UUID inviterId = inviter.getUniqueId();
        UUID targetId = target.getUniqueId();
        String inviterName = inviter.getName();
        String targetName = target.getName();
        mutate(inviter, "invite player to party", () -> coordinator.invite(inviterId, targetId), result -> {
            String message = switch (result) {
                case INVITED -> {
                    if (target.isOnline()) {
                        // The plain text is the whole call to action for Bedrock;
                        // Java players can also click it to open the social menu.
                        target.sendMessage(Component.text(message(target, "party.invite.received", inviterName),
                                        NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/amis"))
                                .hoverEvent(HoverEvent.showText(Component.text("/amis", NamedTextColor.YELLOW))));
                    }
                    yield message(inviter, "party.invite.sent", targetName);
                }
                case NOT_LEADER -> message(inviter, "party.invite.not_leader");
                case PARTY_FULL -> message(inviter, "party.invite.full");
                case TARGET_IN_PARTY -> message(inviter, "party.invite.target_in_party", targetName);
                case SELF_INVITE -> message(inviter, "party.invite.self");
            };
            completion.accept(message);
        }, completion);
    }

    public void join(Player player, Player leader, Consumer<String> completion) {
        join(player, leader.getUniqueId(), leader.getName(), completion);
    }

    /** Accepts a pending invitation even when the leader is offline (e.g. invited from the app). */
    public void join(Player player, UUID leaderId, String leaderName, Consumer<String> completion) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        mutate(player, "join party", () -> coordinator.join(playerId, leaderId), result -> {
            String message = switch (result) {
                case JOINED -> {
                    PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
                    if (party != null) {
                        broadcast(party.id(), "party.join.broadcast", playerName);
                    }
                    yield message(player, "party.join.done", leaderName);
                }
                case INVITE_MISSING -> message(player, "party.join.missing");
                case INVITE_EXPIRED -> message(player, "party.join.expired");
                case PARTY_FULL -> message(player, "party.join.full");
                case ALREADY_IN_PARTY -> message(player, "party.already_in_party");
            };
            completion.accept(message);
        }, completion);
    }

    public void leave(Player player, Consumer<String> completion) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        PartyRepository.Party before = coordinator.snapshot().partyFor(playerId);
        UUID formerPartyId = before == null ? null : before.id();
        mutate(player, "leave party", () -> coordinator.leave(playerId), result -> {
            if (result == PartyRepository.LeaveResult.NOT_IN_PARTY) {
                completion.accept(message(player, "party.leave.not_in_party"));
                return;
            }
            if (formerPartyId != null) {
                broadcast(formerPartyId, "party.leave.broadcast", playerName);
            }
            completion.accept(message(player, "party.leave.done"));
        }, completion);
    }

    /**
     * Loads leaders who sent this player a pending invitation. Runs on the party
     * worker; the completion runs on the main thread and receives an empty list
     * when the service is unavailable.
     */
    public void loadPendingInvites(Player player, Consumer<List<UUID>> completion) {
        UUID playerId = player.getUniqueId();
        if (!running.get()) {
            completion.accept(List.of());
            return;
        }
        try {
            worker.execute(() -> {
                List<UUID> leaders;
                try {
                    leaders = coordinator.pendingInviteLeaders(playerId);
                } catch (RuntimeException error) {
                    markUnavailable("list pending party invitations", error);
                    leaders = List.of();
                }
                List<UUID> result = leaders;
                runOnMain(() -> completion.accept(result));
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            completion.accept(List.of());
        }
    }

    /** Leader of the player's durable party, or null when the player is not in one. */
    public UUID getLeaderId(UUID playerId) {
        PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
        return party == null ? null : party.leaderId();
    }

    public static int maxPartySize() {
        return PartyRepository.MAX_PARTY_SIZE;
    }

    public String queueParty(Player requester) {
        return queueParty(requester, null);
    }

    /** Queues the entire durable party for one exact arena without splitting it. */
    public String queueParty(Player requester, Game requestedGame) {
        if (!isAvailable()) {
            return message(requester, UNAVAILABLE);
        }
        PartyRepository.Party party = coordinator.snapshot().partyFor(requester.getUniqueId());
        if (party == null) {
            return null;
        }
        if (!hasOnlinePartyCompanions(requester.getUniqueId())) {
            return null;
        }
        if (!party.leaderId().equals(requester.getUniqueId())) {
            return message(requester, "party.queue.leader_only");
        }
        List<CookiePlayer> members = onlineCookiePlayers(party);
        if (members.size() != party.members().size()) {
            return message(requester, "party.queue.all_online");
        }
        if (members.stream().anyMatch(member -> !PlayerWrapperListener.isPlayerDataReady(
                member.getPlayer().getUniqueId()))) {
            return message(requester, "party.queue.member_loading");
        }
        PartyGameSelection selection = requestedGame == null
                ? selectPartyGame(GameManager.getGames(), members.size())
                : requestedGame.getState() == com.cookiebuild.cookiedough.game.GameState.OPEN
                        && requestedGame.isAdmissionsOpen()
                        && requestedGame.getPartyAdmissionProblem(members.size()) == null
                                ? new PartyGameSelection(requestedGame, "")
                                : new PartyGameSelection(null,
                                        requestedGame.getPartyAdmissionProblem(members.size()) == null
                                                ? message(requester, "party.queue.not_available")
                                                : requestedGame.getPartyAdmissionProblem(members.size()));
        Game game = selection.game();
        if (game == null) {
            String reason = selection.rejectionReason();
            return reason == null || reason.isBlank() ? message(requester, "party.queue.no_game") : reason;
        }
        if (!GameManager.registerPartyQueueIntent(members, game, party.id())) {
            return LocaleManager.getMessage(
                    "lobby.queue.leave_failed", requester.locale());
        }
        for (CookiePlayer member : members) {
            member.getPlayer().sendMessage(ChatColor.GOLD + message(member.getPlayer(), "party.prefix") + " "
                    + ChatColor.GREEN
                    + LocaleManager.getMessage(
                            "lobby.queue.intent_registered", member.getPlayer().locale(), game.getGameName()));
        }
        return "";
    }

    static PartyGameSelection selectPartyGame(List<Game> games, int partySize) {
        List<Game> openGames = games.stream()
                .filter(candidate -> candidate.getState() == com.cookiebuild.cookiedough.game.GameState.OPEN)
                .toList();
        String firstRejection = null;
        List<Game> compatible = new ArrayList<>();
        for (Game candidate : openGames) {
            String problem = candidate.getPartyAdmissionProblem(partySize);
            if (problem == null) {
                compatible.add(candidate);
            }
            else if (firstRejection == null) {
                firstRejection = problem;
            }
        }
        Game selected = GameManager.selectBestOpenGame(compatible);
        if (selected != null) {
            return new PartyGameSelection(selected, "");
        }
        // A null reason means "no open game at all"; queueParty localizes it.
        return new PartyGameSelection(null, firstRejection);
    }

    public UUID getPartyId(UUID playerId) {
        PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
        return party == null ? null : party.id();
    }

    /** Durable offline members must not turn an effectively solo player into a blocked party. */
    public boolean hasOnlinePartyCompanions(UUID playerId) {
        PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
        return party != null && hasOnlineCompanions(playerId, onlineMemberIds(party));
    }

    static boolean hasOnlineCompanions(UUID playerId, List<UUID> onlineMemberIds) {
        return playerId != null && onlineMemberIds != null && onlineMemberIds.stream()
                .anyMatch(memberId -> memberId != null && !memberId.equals(playerId));
    }

    public boolean arePartyMembers(UUID first, UUID second) {
        UUID party = coordinator.snapshot().partyByMember().get(first);
        return party != null && party.equals(coordinator.snapshot().partyByMember().get(second));
    }

    public List<UUID> getMembers(UUID playerId) {
        PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
        return party == null ? List.of() : party.members();
    }

    /**
     * Confirms that a passive queue cohort still represents the complete durable
     * party after an in-game leave/join or a periodic mobile-app refresh.
     */
    public boolean isCurrentQueueCohort(UUID cohortId, List<UUID> memberIds) {
        return currentPartyMatches(coordinator.snapshot(), cohortId, memberIds);
    }

    /** A solo queue remains valid until another member of the durable party is online. */
    public boolean isCurrentSoloQueueCohort(UUID playerId) {
        PartyRepository.Snapshot snapshot = coordinator.snapshot();
        PartyRepository.Party party = snapshot.partyFor(playerId);
        return currentSoloPlayerHasNoOnlineCompanions(snapshot, playerId,
                party == null ? List.of(playerId) : onlineMemberIds(party));
    }

    static boolean currentSoloPlayerHasNoOnlineCompanions(PartyRepository.Snapshot snapshot,
            UUID playerId, List<UUID> onlineMemberIds) {
        if (snapshot == null || playerId == null || onlineMemberIds == null) {
            return false;
        }
        PartyRepository.Party party = snapshot.partyFor(playerId);
        return party == null || !hasOnlineCompanions(playerId, onlineMemberIds);
    }

    static boolean currentPartyMatches(PartyRepository.Snapshot snapshot, UUID cohortId,
            List<UUID> memberIds) {
        if (snapshot == null || cohortId == null || memberIds == null || memberIds.isEmpty()
                || memberIds.stream().anyMatch(java.util.Objects::isNull)
                || memberIds.stream().distinct().count() != memberIds.size()) {
            return false;
        }
        PartyRepository.Party party = snapshot.parties().get(cohortId);
        return party != null && party.members().size() == memberIds.size()
                && java.util.Set.copyOf(party.members()).equals(java.util.Set.copyOf(memberIds));
    }

    public String describe(Player player) {
        if (!isAvailable()) {
            return message(player, UNAVAILABLE);
        }
        PartyRepository.Party party = coordinator.snapshot().partyFor(player.getUniqueId());
        if (party == null) {
            return message(player, "party.describe.none");
        }
        return message(player, "party.describe.summary", playerName(party.leaderId()),
                String.join(", ", party.members().stream().map(this::playerName).toList()));
    }

    /** A disconnect no longer destroys durable party membership. */
    public void disconnect(UUID playerId) {
        // Intentionally retained for the player lifecycle call site. The next
        // login or app action sees the same durable membership.
    }

    public void shutdown(Duration timeout) {
        running.set(false);
        BukkitTask task = refreshTask;
        if (task != null) {
            task.cancel();
        }
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

    private List<UUID> onlineMemberIds(PartyRepository.Party party) {
        return party.members().stream().filter(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            return player != null && player.isOnline();
        }).toList();
    }

    private List<CookiePlayer> onlineCookiePlayers(PartyRepository.Party party) {
        return party.members().stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .filter(Player::isOnline)
                .map(PlayerManager::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void broadcast(UUID partyId, String key, Object... args) {
        PartyRepository.Party party = coordinator.snapshot().parties().get(partyId);
        if (party == null) {
            return;
        }
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + message(player, "party.prefix") + " " + ChatColor.YELLOW
                        + message(player, key, args));
            }
        }
    }

    private static String message(Player player, String key, Object... args) {
        return LocaleManager.getMessage(key, player == null ? java.util.Locale.ENGLISH : player.locale(), args);
    }

    /** Best-effort display name for a party member, including offline members. */
    public String playerName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    private void refreshAsync() {
        if (!running.get() || !refreshQueued.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            PartyRepository.Snapshot before = coordinator.snapshot();
            boolean notify = initialized;
            try {
                PartyRepository.Snapshot after = coordinator.refresh();
                initialized = true;
                healthy.set(true);
                if (notify && !before.partyByMember().equals(after.partyByMember())) {
                    runOnMain(() -> notifyExternalChanges(before, after));
                }
            } catch (RuntimeException error) {
                markUnavailable("refresh parties", error);
            } finally {
                refreshQueued.set(false);
            }
        });
    }

    private <T> void mutate(Player actor, String operation, ThrowingSupplier<T> work,
            Consumer<T> onSuccess, Consumer<String> onFailure) {
        if (!running.get()) {
            onFailure.accept(message(actor, UNAVAILABLE));
            return;
        }
        worker.execute(() -> {
            try {
                T result = work.get();
                initialized = true;
                healthy.set(true);
                runOnMain(() -> onSuccess.accept(result));
            } catch (RuntimeException error) {
                markUnavailable(operation, error);
                runOnMain(() -> onFailure.accept(message(actor, UNAVAILABLE)));
            }
        });
    }

    private void notifyExternalChanges(PartyRepository.Snapshot before, PartyRepository.Snapshot after) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            UUID previous = before.partyByMember().get(playerId);
            UUID current = after.partyByMember().get(playerId);
            if (!java.util.Objects.equals(previous, current)) {
                player.sendMessage(ChatColor.GOLD + message(player, "party.prefix") + " " + ChatColor.YELLOW
                        + message(player, "party.synced"));
            }
        }
    }

    private void markUnavailable(String operation, RuntimeException error) {
        healthy.set(false);
        long now = System.currentTimeMillis();
        long previous = lastFailureLog.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS && lastFailureLog.compareAndSet(previous, now)) {
            plugin.getLogger().severe("Party service unavailable while attempting to " + operation
                    + ": " + error.getMessage());
        }
    }

    private void runOnMain(Runnable work) {
        if (running.get()) {
            Bukkit.getScheduler().runTask(plugin, work);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
