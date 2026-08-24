package com.cookiebuild.cookiedough.listener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Gives slow Java and Bedrock clients a short anti-floating grace window while
 * their profile, destination chunks and cross-world teleport are acknowledged.
 */
public final class PlayerTransitionFlightGuard {
    static final long LOADING_WINDOW_TICKS = 20L * 10L;
    static final int MINIMUM_LANDING_GRACE_TICKS = 20 * 2;
    static final int LANDING_WINDOW_TICKS = 20 * 10;

    interface FlightSubject {
        UUID id();
        boolean online();
        boolean onGround();
        boolean retainsFlight();
        void flying(boolean value);
        void allowFlight(boolean value);
    }

    @FunctionalInterface
    interface Scheduler { void later(Runnable action, long delayTicks); }

    private final Scheduler scheduler;
    private final ConcurrentMap<UUID, Long> generations = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public PlayerTransitionFlightGuard(Plugin plugin) {
        this((action, delay) -> plugin.getServer().getScheduler().runTaskLater(plugin, action, delay));
    }

    PlayerTransitionFlightGuard(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Protects an asynchronous join and invokes the safe fallback on timeout. */
    public void protectLoading(Player player, Runnable timeoutFallback) {
        protectLoading(subject(player), timeoutFallback);
    }

    void protectLoading(FlightSubject subject, Runnable timeoutFallback) {
        long generation = begin(subject);
        scheduler.later(() -> {
            if (!isCurrent(subject.id(), generation) || !subject.online()) return;
            timeoutFallback.run();
            revokeIfCurrent(subject, generation);
        }, LOADING_WINDOW_TICKS);
    }

    public boolean teleport(Player player, Location destination) {
        if (player == null || destination == null || destination.getWorld() == null) return false;
        return teleport(subject(player), () -> player.teleport(destination));
    }

    boolean teleport(FlightSubject subject, BooleanSupplier teleport) {
        long generation = begin(subject);
        boolean teleported;
        try {
            teleported = teleport.getAsBoolean();
        } catch (Throwable error) {
            revokeIfCurrent(subject, generation);
            throw error;
        }
        if (!teleported) {
            revokeIfCurrent(subject, generation);
            return false;
        }
        scheduler.later(() -> awaitLanding(subject, generation, 1,
                () -> revokeIfCurrent(subject, generation)), 1L);
        return true;
    }

    /** Keeps the grace permission until landing, then restores a reconnect snapshot exactly. */
    public void protectLanding(Player player, boolean restoreAllowFlight, boolean restoreFlying) {
        protectLanding(subject(player), restoreAllowFlight, restoreFlying);
    }

    void protectLanding(FlightSubject subject, boolean restoreAllowFlight, boolean restoreFlying) {
        long generation = begin(subject);
        scheduler.later(() -> awaitLanding(subject, generation, 1,
                () -> restoreIfCurrent(subject, generation, restoreAllowFlight, restoreFlying)), 1L);
    }

    /** Stops tracking and revokes the temporary permission unless the game mode owns flight. */
    public void abandon(Player player) {
        if (player != null) abandon(subject(player));
    }

    void abandon(FlightSubject subject) {
        if (subject == null || generations.remove(subject.id()) == null
                || !subject.online() || subject.retainsFlight()) return;
        subject.flying(false);
        subject.allowFlight(false);
    }

    public boolean isProtected(UUID playerId) {
        return playerId != null && generations.containsKey(playerId);
    }

    private long begin(FlightSubject subject) {
        long generation = sequence.incrementAndGet();
        generations.put(subject.id(), generation);
        subject.flying(false);
        subject.allowFlight(true);
        return generation;
    }

    private void awaitLanding(FlightSubject subject, long generation, int elapsedTicks, Runnable completion) {
        if (!isCurrent(subject.id(), generation)) return;
        if (!subject.online()) {
            generations.remove(subject.id(), generation);
            return;
        }
        if (elapsedTicks >= LANDING_WINDOW_TICKS
                || subject.onGround() && elapsedTicks >= MINIMUM_LANDING_GRACE_TICKS) {
            completion.run();
            return;
        }
        scheduler.later(() -> awaitLanding(subject, generation, elapsedTicks + 1, completion), 1L);
    }

    private void restoreIfCurrent(FlightSubject subject, long generation,
            boolean restoreAllowFlight, boolean restoreFlying) {
        if (!generations.remove(subject.id(), generation) || !subject.online()) return;
        subject.flying(false);
        subject.allowFlight(restoreAllowFlight);
        subject.flying(restoreAllowFlight && restoreFlying);
    }

    private void revokeIfCurrent(FlightSubject subject, long generation) {
        if (!generations.remove(subject.id(), generation) || !subject.online() || subject.retainsFlight()) return;
        subject.flying(false);
        subject.allowFlight(false);
    }

    private boolean isCurrent(UUID playerId, long generation) {
        return Long.valueOf(generation).equals(generations.get(playerId));
    }

    private static FlightSubject subject(Player player) {
        return new FlightSubject() {
            @Override public UUID id() { return player.getUniqueId(); }
            @Override public boolean online() { return player.isOnline(); }
            @Override public boolean onGround() { return player.isOnGround(); }
            @Override public boolean retainsFlight() {
                return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
            }
            @Override public void flying(boolean value) { player.setFlying(value); }
            @Override public void allowFlight(boolean value) { player.setAllowFlight(value); }
        };
    }
}
