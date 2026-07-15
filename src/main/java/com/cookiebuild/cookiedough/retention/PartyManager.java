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

/** Durable parties synchronized with the website/mobile party API. */
public final class PartyManager {
    private static final long REFRESH_TICKS = 40L;
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static final String UNAVAILABLE = "Party service is temporarily unavailable. Please try again.";

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
        mutate("create party", () -> coordinator.create(leaderId), result -> {
            String message = result == PartyRepository.CreateResult.CREATED
                    ? "Party created."
                    : "You are already in a party.";
            completion.accept(message);
        }, completion);
    }

    public void invite(Player inviter, Player target, Consumer<String> completion) {
        UUID inviterId = inviter.getUniqueId();
        UUID targetId = target.getUniqueId();
        String inviterName = inviter.getName();
        String targetName = target.getName();
        mutate("invite player to party", () -> coordinator.invite(inviterId, targetId), result -> {
            String message = switch (result) {
                case INVITED -> {
                    if (target.isOnline()) {
                        target.sendMessage(ChatColor.GOLD + inviterName + " invited you to a party. "
                                + ChatColor.YELLOW + "Use /party join " + inviterName
                                + " or accept in the Cookie Build app.");
                    }
                    yield "Party invitation sent to " + targetName + ".";
                }
                case NOT_LEADER -> "Only the party leader can invite players.";
                case PARTY_FULL -> "Your party is full.";
                case TARGET_IN_PARTY -> targetName + " is already in a party.";
                case SELF_INVITE -> "You cannot invite yourself.";
            };
            completion.accept(message);
        }, completion);
    }

    public void join(Player player, Player leader, Consumer<String> completion) {
        UUID playerId = player.getUniqueId();
        UUID leaderId = leader.getUniqueId();
        String playerName = player.getName();
        String leaderName = leader.getName();
        mutate("join party", () -> coordinator.join(playerId, leaderId), result -> {
            String message = switch (result) {
                case JOINED -> {
                    PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
                    if (party != null) {
                        broadcast(party.id(), playerName + " joined the party.");
                    }
                    yield "Joined " + leaderName + "'s party.";
                }
                case INVITE_MISSING -> "That party invitation is missing or already used.";
                case INVITE_EXPIRED -> "That party invitation expired.";
                case PARTY_FULL -> "That party is full.";
                case ALREADY_IN_PARTY -> "You are already in a party.";
            };
            completion.accept(message);
        }, completion);
    }

    public void leave(Player player, Consumer<String> completion) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        PartyRepository.Party before = coordinator.snapshot().partyFor(playerId);
        UUID formerPartyId = before == null ? null : before.id();
        mutate("leave party", () -> coordinator.leave(playerId), result -> {
            if (result == PartyRepository.LeaveResult.NOT_IN_PARTY) {
                completion.accept("You are not in a party.");
                return;
            }
            if (formerPartyId != null) {
                broadcast(formerPartyId, playerName + " left the party.");
            }
            completion.accept("You left the party.");
        }, completion);
    }

    public String queueParty(Player requester) {
        if (!isAvailable()) {
            return UNAVAILABLE;
        }
        PartyRepository.Party party = coordinator.snapshot().partyFor(requester.getUniqueId());
        if (party == null) {
            return null;
        }
        if (!party.leaderId().equals(requester.getUniqueId())) {
            return "Only the party leader can start Quick Play.";
        }
        List<CookiePlayer> members = onlineCookiePlayers(party);
        if (members.size() != party.members().size()) {
            return "All party members must be online before starting Quick Play.";
        }
        if (members.stream().anyMatch(member -> !PlayerWrapperListener.isPlayerDataReady(
                member.getPlayer().getUniqueId()))) {
            return "A party member's profile is still loading.";
        }
        PartyGameSelection selection = selectPartyGame(GameManager.getGames(), members.size());
        Game game = selection.game();
        if (game == null) {
            return selection.rejectionReason();
        }
        List<CookiePlayer> added = new ArrayList<>();
        for (CookiePlayer member : members) {
            if (!game.addPlayerToAvailableTeam(member)) {
                added.forEach(addedMember -> game.removePlayer(addedMember, "party_admission_rollback"));
                return "The party could not join together. Please try again.";
            }
            added.add(member);
        }
        broadcast(party.id(), "Party Quick Play: joined " + game.getGameName() + " ("
                + game.getPlayerCount() + "/" + game.getCapacity() + ").");
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
        return new PartyGameSelection(null, firstRejection == null
                ? "No game is currently available for your party."
                : firstRejection);
    }

    public UUID getPartyId(UUID playerId) {
        PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
        return party == null ? null : party.id();
    }

    public boolean arePartyMembers(UUID first, UUID second) {
        UUID party = coordinator.snapshot().partyByMember().get(first);
        return party != null && party.equals(coordinator.snapshot().partyByMember().get(second));
    }

    public List<UUID> getMembers(UUID playerId) {
        PartyRepository.Party party = coordinator.snapshot().partyFor(playerId);
        return party == null ? List.of() : party.members();
    }

    public String describe(Player player) {
        if (!isAvailable()) {
            return UNAVAILABLE;
        }
        PartyRepository.Party party = coordinator.snapshot().partyFor(player.getUniqueId());
        if (party == null) {
            return "You are not in a party. Use /party create or /party invite <player>.";
        }
        return "Party leader: " + playerName(party.leaderId()) + " | Members: " + party.members().stream()
                .map(this::playerName).toList();
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

    private List<CookiePlayer> onlineCookiePlayers(PartyRepository.Party party) {
        return party.members().stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .map(PlayerManager::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void broadcast(UUID partyId, String message) {
        PartyRepository.Party party = coordinator.snapshot().parties().get(partyId);
        if (party == null) {
            return;
        }
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "[Party] " + ChatColor.YELLOW + message);
            }
        }
    }

    private String playerName(UUID playerId) {
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

    private <T> void mutate(String operation, ThrowingSupplier<T> work,
            Consumer<T> onSuccess, Consumer<String> onFailure) {
        if (!running.get()) {
            onFailure.accept(UNAVAILABLE);
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
                runOnMain(() -> onFailure.accept(UNAVAILABLE));
            }
        });
    }

    private void notifyExternalChanges(PartyRepository.Snapshot before, PartyRepository.Snapshot after) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            UUID previous = before.partyByMember().get(playerId);
            UUID current = after.partyByMember().get(playerId);
            if (!java.util.Objects.equals(previous, current)) {
                player.sendMessage(ChatColor.GOLD + "[Party] " + ChatColor.YELLOW
                        + "Party membership synced from the Cookie Build app.");
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
