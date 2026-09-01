package com.cookiebuild.cookiedough.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Small bounded pool for fully prepared game instances.
 *
 * <p>World-backed games are deliberately constructed before they enter this
 * pool. Polling during a countdown is therefore constant-time and can never
 * start an expensive world load on the server tick that filled a queue.</p>
 */
public final class StandbyGamePool<T> {
    private final int targetSize;
    private final ArrayDeque<T> games = new ArrayDeque<>();

    public StandbyGamePool(int targetSize) {
        if (targetSize < 1) {
            throw new IllegalArgumentException("targetSize must be positive");
        }
        this.targetSize = targetSize;
    }

    public synchronized boolean offer(T game) {
        Objects.requireNonNull(game, "game");
        if (games.size() >= targetSize) {
            return false;
        }
        games.addLast(game);
        return true;
    }

    public synchronized T poll() {
        return games.pollFirst();
    }

    public synchronized boolean needsRefill() {
        return games.size() < targetSize;
    }

    public synchronized int size() {
        return games.size();
    }

    public synchronized int targetSize() {
        return targetSize;
    }

    public synchronized List<T> drain() {
        List<T> drained = new ArrayList<>(games);
        games.clear();
        return drained;
    }
}
