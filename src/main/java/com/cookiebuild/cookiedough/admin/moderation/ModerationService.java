package com.cookiebuild.cookiedough.admin.moderation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationService {
    private record State(ModerationAction ban, ModerationAction mute) { }

    private final ModerationRepository repository;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ModerationService(ModerationRepository repository) {
        this.repository = repository;
    }

    /** Called from Paper's asynchronous pre-login event. */
    public Optional<ModerationAction> refreshForLogin(UUID playerId) {
        State state = state(repository.findActive(playerId));
        states.put(playerId, state);
        return Optional.ofNullable(state.ban());
    }

    public boolean isMuted(UUID playerId) {
        State state = states.get(playerId);
        return state != null && state.mute() != null && state.mute().activeAt(Instant.now());
    }

    public ModerationAction create(ModerationAction action) {
        ModerationAction persisted = repository.create(action);
        states.compute(action.playerId(), (ignored, existing) -> {
            State current = existing == null ? new State(null, null) : existing;
            return action.type() == ModerationAction.Type.BAN
                    ? new State(persisted, current.mute())
                    : new State(current.ban(), persisted);
        });
        return persisted;
    }

    public int revoke(UUID playerId, ModerationAction.Type type, String actorId) {
        int count = repository.revoke(playerId, type, actorId);
        states.computeIfPresent(playerId, (ignored, current) -> type == ModerationAction.Type.BAN
                ? new State(null, current.mute()) : new State(current.ban(), null));
        return count;
    }

    public void removeCached(UUID playerId) {
        states.remove(playerId);
    }

    private static State state(List<ModerationAction> actions) {
        Comparator<ModerationAction> newest = Comparator.comparing(ModerationAction::createdAt);
        ModerationAction ban = actions.stream().filter(action -> action.type() == ModerationAction.Type.BAN)
                .max(newest).orElse(null);
        ModerationAction mute = actions.stream().filter(action -> action.type() == ModerationAction.Type.MUTE)
                .max(newest).orElse(null);
        return new State(ban, mute);
    }
}
