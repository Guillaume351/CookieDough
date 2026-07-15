package com.cookiebuild.cookiedough.admin.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ModerationServiceTest {
    @Test
    void preLoginLoadsDurableBanAndMuteAndRevocationUpdatesTheCache() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        FakeRepository repository = new FakeRepository();
        repository.actions.add(action(playerId, ModerationAction.Type.BAN, now.plusSeconds(600)));
        repository.actions.add(action(playerId, ModerationAction.Type.MUTE, null));
        ModerationService service = new ModerationService(repository);

        assertTrue(service.refreshForLogin(playerId).isPresent());
        assertTrue(service.isMuted(playerId));

        service.revoke(playerId, ModerationAction.Type.MUTE, "firebase-admin:moderator_123");
        assertFalse(service.isMuted(playerId));
        service.revoke(playerId, ModerationAction.Type.BAN, "firebase-admin:moderator_123");
        assertFalse(service.refreshForLogin(playerId).isPresent());
    }

    @Test
    void expiredActionsAreNotActive() {
        UUID playerId = UUID.randomUUID();
        ModerationAction expired = action(playerId, ModerationAction.Type.BAN, Instant.now().minusSeconds(1));
        assertFalse(expired.activeAt(Instant.now()));
    }

    @Test
    void preservesANonUuidFirebaseActorId() {
        ModerationAction action = action(UUID.randomUUID(), ModerationAction.Type.BAN, null);
        assertTrue(action.actorId().equals("firebase-admin:moderator_123"));
    }

    private static ModerationAction action(UUID playerId, ModerationAction.Type type, Instant expiresAt) {
        Instant now = Instant.now().minusSeconds(10);
        return new ModerationAction(UUID.randomUUID(), playerId, "Player", type, "Reason",
                "firebase-admin:moderator_123",
                "Admin", now, expiresAt, UUID.randomUUID(), "{}", now);
    }

    private static final class FakeRepository implements ModerationRepository {
        private final List<ModerationAction> actions = new ArrayList<>();

        @Override public List<ModerationAction> findActive(UUID playerId) {
            return actions.stream().filter(action -> action.playerId().equals(playerId))
                    .filter(action -> action.activeAt(Instant.now())).toList();
        }
        @Override public ModerationAction create(ModerationAction action) { actions.add(action); return action; }
        @Override public int revoke(UUID playerId, ModerationAction.Type type, String revokedBy) {
            int before = actions.size();
            actions.removeIf(action -> action.playerId().equals(playerId) && action.type() == type);
            return before - actions.size();
        }
    }
}
