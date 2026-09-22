package com.cookiebuild.cookiedough.retention;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the lock-free Paper read model synchronized after every durable write.
 * Callers run its blocking methods away from the Paper main thread.
 */
final class PartyCoordinator {
    private final PartyRepository repository;
    private final AtomicReference<PartyRepository.Snapshot> snapshot =
            new AtomicReference<>(PartyRepository.Snapshot.empty());

    PartyCoordinator(PartyRepository repository) {
        this.repository = repository;
    }

    PartyRepository.Snapshot snapshot() {
        return snapshot.get();
    }

    PartyRepository.Snapshot refresh() {
        PartyRepository.Snapshot loaded = repository.load();
        snapshot.set(loaded);
        return loaded;
    }

    PartyRepository.CreateResult create(UUID leaderId) {
        PartyRepository.CreateResult result = repository.create(leaderId);
        refresh();
        return result;
    }

    PartyRepository.InviteResult invite(UUID inviterId, UUID inviteeId) {
        PartyRepository.InviteResult result = repository.invite(inviterId, inviteeId);
        refresh();
        return result;
    }

    PartyRepository.JoinResult join(UUID inviteeId, UUID leaderId) {
        PartyRepository.JoinResult result = repository.join(inviteeId, leaderId);
        refresh();
        return result;
    }

    PartyRepository.LeaveResult leave(UUID playerId) {
        PartyRepository.LeaveResult result = repository.leave(playerId);
        refresh();
        return result;
    }
}
