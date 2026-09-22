package com.cookiebuild.cookiedough.retention;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared friend graph used by Minecraft and the Cookie Build mobile API. */
public interface FriendRepository {
    enum RequestResult {
        REQUESTED,
        ACCEPTED,
        ALREADY_REQUESTED,
        ALREADY_FRIENDS,
        PLAYER_NOT_FOUND,
        PLAYER_NAME_AMBIGUOUS,
        SELF,
        TOO_MANY_PENDING,
        RECENTLY_REQUESTED,
        UNAVAILABLE
    }

    enum BlockResult {
        BLOCKED,
        UNBLOCKED
    }

    enum ReportResult {
        RECORDED,
        DUPLICATE,
        TOO_MANY
    }

    enum AcceptResult {
        ACCEPTED,
        REQUEST_NOT_FOUND,
        PLAYER_NOT_FOUND,
        PLAYER_NAME_AMBIGUOUS,
        UNAVAILABLE
    }

    enum DeleteResult {
        DELETED,
        RELATIONSHIP_NOT_FOUND,
        PLAYER_NOT_FOUND,
        PLAYER_NAME_AMBIGUOUS
    }

    record Friend(String name, boolean online) {
    }

    record Snapshot(List<Friend> friends, List<String> incoming, List<String> outgoing) {
        public Snapshot {
            friends = List.copyOf(friends);
            incoming = List.copyOf(incoming);
            outgoing = List.copyOf(outgoing);
        }
    }

    RequestResult request(UUID actorId, String actorName, String targetName);

    AcceptResult accept(UUID actorId, String targetName);

    DeleteResult deny(UUID actorId, String targetName);

    DeleteResult remove(UUID actorId, String targetName);

    Snapshot snapshot(UUID actorId);

    BlockResult toggleBlock(UUID actorId, UUID targetId);

    ReportResult report(UUID actorId, UUID targetId, String reason);

    Set<UUID> blockedPlayers(UUID actorId);
}
