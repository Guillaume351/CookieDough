package com.cookiebuild.cookiedough.retention;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.Session;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** PostgreSQL friend operations compatible with the website/mobile transaction rules. */
public final class PostgresFriendRepository implements FriendRepository {
    private static final int OUTGOING_REQUEST_LIMIT = 10;
    private static final int FRIEND_REQUEST_COOLDOWN_DAYS = 30;

    private record PlayerRow(UUID id, String name) {
    }

    private record Pair(UUID low, UUID high) {
    }

    private record Existing(UUID requestedBy, String status) {
    }

    @Override
    public RequestResult request(UUID actorId, String actorName, String targetName) {
        return transaction("send friend request", connection -> {
            PlayerRow target = exactPlayer(connection, targetName);
            if (target == null) return RequestResult.PLAYER_NOT_FOUND;
            if (target.id().equals(actorId)) return RequestResult.SELF;
            if (target.name() == null) return RequestResult.PLAYER_NAME_AMBIGUOUS;

            Pair pair = lockPair(connection, actorId, target.id());
            if (blocked(connection, actorId, target.id())) return RequestResult.UNAVAILABLE;
            Existing existing = friendship(connection, pair);
            if (existing != null && "accepted".equals(existing.status())) {
                return RequestResult.ALREADY_FRIENDS;
            }
            if (existing != null && actorId.equals(existing.requestedBy())) {
                return RequestResult.ALREADY_REQUESTED;
            }
            if (existing != null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE player_friendships
                           SET status = 'accepted', accepted_at = now(), updated_at = now()
                         WHERE player_low_id = ? AND player_high_id = ?
                        """)) {
                    statement.setObject(1, pair.low());
                    statement.setObject(2, pair.high());
                    statement.executeUpdate();
                }
                return RequestResult.ACCEPTED;
            }
            if (friendRequestOnCooldown(connection, actorId, target.id())) {
                return RequestResult.RECENTLY_REQUESTED;
            }
            if (outgoingPendingCount(connection, actorId) >= OUTGOING_REQUEST_LIMIT) {
                return RequestResult.TOO_MANY_PENDING;
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_friendships
                        (player_low_id, player_high_id, requested_by_player_id, status)
                    VALUES (?, ?, ?, 'pending')
                    """)) {
                statement.setObject(1, pair.low());
                statement.setObject(2, pair.high());
                statement.setObject(3, actorId);
                statement.executeUpdate();
            }
            recordFriendRequestCooldown(connection, actorId, target.id());
            queueRequestNotification(connection, target.id(), actorId, actorName);
            return RequestResult.REQUESTED;
        });
    }

    @Override
    public AcceptResult accept(UUID actorId, String targetName) {
        return transaction("accept friend request", connection -> {
            PlayerRow target = exactPlayer(connection, targetName);
            if (target == null) return AcceptResult.PLAYER_NOT_FOUND;
            if (target.name() == null) return AcceptResult.PLAYER_NAME_AMBIGUOUS;
            Pair pair = lockPair(connection, actorId, target.id());
            if (blocked(connection, actorId, target.id())) return AcceptResult.UNAVAILABLE;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_friendships
                       SET status = 'accepted', accepted_at = now(), updated_at = now()
                     WHERE player_low_id = ? AND player_high_id = ?
                       AND status = 'pending' AND requested_by_player_id = ?
                    """)) {
                statement.setObject(1, pair.low());
                statement.setObject(2, pair.high());
                statement.setObject(3, target.id());
                return statement.executeUpdate() == 1
                        ? AcceptResult.ACCEPTED : AcceptResult.REQUEST_NOT_FOUND;
            }
        });
    }

    @Override
    public DeleteResult deny(UUID actorId, String targetName) {
        return delete(actorId, targetName, "status = 'pending' AND requested_by_player_id = ?", true);
    }

    @Override
    public DeleteResult remove(UUID actorId, String targetName) {
        return delete(actorId, targetName, "status = 'accepted'", false);
    }

    @Override
    public Snapshot snapshot(UUID actorId) {
        return transaction("list friends", connection -> {
            List<Friend> friends = new ArrayList<>();
            List<String> incoming = new ArrayList<>();
            List<String> outgoing = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT other.name,
                           friendship.status,
                           friendship.requested_by_player_id,
                           CASE WHEN NOT EXISTS (
                             SELECT 1
                               FROM mobile_player_links privacy_link
                               JOIN mobile_users privacy_user
                                 ON privacy_user.firebase_uid = privacy_link.firebase_uid
                                AND privacy_user.deleted_at IS NULL
                               JOIN mobile_notification_preferences privacy_pref
                                 ON privacy_pref.mobile_user_id = privacy_user.id
                              WHERE privacy_link.player_id = other.id
                                AND privacy_link.is_primary
                                AND privacy_link.revoked_at IS NULL
                                AND privacy_pref.online_visibility = 'hidden'
                           ) THEN EXISTS (
                             SELECT 1
                               FROM player_sessions session
                              WHERE session.player_id = other.id AND session.end_time IS NULL
                           ) ELSE false END AS online
                      FROM player_friendships friendship
                      JOIN playerdata other ON other.id = CASE
                        WHEN friendship.player_low_id = ? THEN friendship.player_high_id
                        ELSE friendship.player_low_id END
                     WHERE ? IN (friendship.player_low_id, friendship.player_high_id)
                     ORDER BY friendship.status, online DESC, lower(other.name), other.id
                    """)) {
                statement.setObject(1, actorId);
                statement.setObject(2, actorId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String name = rows.getString("name");
                        if (name == null || name.isBlank()) name = "Unknown player";
                        if ("accepted".equals(rows.getString("status"))) {
                            friends.add(new Friend(name, rows.getBoolean("online")));
                        } else if (actorId.equals(rows.getObject("requested_by_player_id", UUID.class))) {
                            outgoing.add(name);
                        } else {
                            incoming.add(name);
                        }
                    }
                }
            }
            return new Snapshot(friends, incoming, outgoing);
        });
    }

    private static int outgoingPendingCount(Connection connection, UUID actorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                  FROM player_friendships
                 WHERE status = 'pending' AND requested_by_player_id = ?
                """)) {
            statement.setObject(1, actorId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static boolean friendRequestOnCooldown(Connection connection, UUID actorId, UUID targetId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                  FROM player_friend_request_cooldowns
                 WHERE requester_player_id = ? AND target_player_id = ?
                   AND last_requested_at >= now() - (? * interval '1 day')
                 LIMIT 1
                """)) {
            statement.setObject(1, actorId);
            statement.setObject(2, targetId);
            statement.setInt(3, FRIEND_REQUEST_COOLDOWN_DAYS);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void recordFriendRequestCooldown(Connection connection, UUID actorId, UUID targetId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_friend_request_cooldowns
                    (requester_player_id, target_player_id, last_requested_at)
                VALUES (?, ?, now())
                ON CONFLICT (requester_player_id, target_player_id)
                DO UPDATE SET last_requested_at = excluded.last_requested_at
                """)) {
            statement.setObject(1, actorId);
            statement.setObject(2, targetId);
            statement.executeUpdate();
        }
    }

    @Override
    public BlockResult toggleBlock(UUID actorId, UUID targetId) {
        return transaction("update player block", connection -> {
            Pair pair = lockPair(connection, actorId, targetId);
            try (PreparedStatement existing = connection.prepareStatement("""
                    SELECT 1 FROM player_blocks
                     WHERE blocker_player_id = ? AND blocked_player_id = ?
                     FOR UPDATE
                    """)) {
                existing.setObject(1, actorId);
                existing.setObject(2, targetId);
                try (ResultSet rows = existing.executeQuery()) {
                    if (rows.next()) {
                        try (PreparedStatement delete = connection.prepareStatement("""
                                DELETE FROM player_blocks
                                 WHERE blocker_player_id = ? AND blocked_player_id = ?
                                """)) {
                            delete.setObject(1, actorId);
                            delete.setObject(2, targetId);
                            delete.executeUpdate();
                        }
                        return BlockResult.UNBLOCKED;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO player_blocks (blocker_player_id, blocked_player_id)
                    VALUES (?, ?)
                    """)) {
                insert.setObject(1, actorId);
                insert.setObject(2, targetId);
                insert.executeUpdate();
            }
            try (PreparedStatement deleteFriendship = connection.prepareStatement("""
                    DELETE FROM player_friendships
                     WHERE player_low_id = ? AND player_high_id = ?
                    """)) {
                deleteFriendship.setObject(1, pair.low());
                deleteFriendship.setObject(2, pair.high());
                deleteFriendship.executeUpdate();
            }
            try (PreparedStatement deleteAlerts = connection.prepareStatement("""
                    DELETE FROM mobile_friend_online_alerts
                     WHERE (owner_player_id = ? AND target_player_id = ?)
                        OR (owner_player_id = ? AND target_player_id = ?)
                    """)) {
                deleteAlerts.setObject(1, actorId);
                deleteAlerts.setObject(2, targetId);
                deleteAlerts.setObject(3, targetId);
                deleteAlerts.setObject(4, actorId);
                deleteAlerts.executeUpdate();
            }
            try (PreparedStatement cancelInvites = connection.prepareStatement("""
                    UPDATE player_party_invites
                       SET status = 'cancelled', responded_at = now()
                     WHERE status = 'pending'
                       AND ((inviter_player_id = ? AND invitee_player_id = ?)
                         OR (inviter_player_id = ? AND invitee_player_id = ?))
                    """)) {
                cancelInvites.setObject(1, actorId);
                cancelInvites.setObject(2, targetId);
                cancelInvites.setObject(3, targetId);
                cancelInvites.setObject(4, actorId);
                cancelInvites.executeUpdate();
            }
            try (PreparedStatement separateParty = connection.prepareStatement("""
                    WITH shared AS (
                      SELECT actor.party_id, party.leader_player_id
                        FROM player_party_members actor
                        JOIN player_party_members target ON target.party_id = actor.party_id
                        JOIN player_parties party ON party.id = actor.party_id AND party.state = 'active'
                       WHERE actor.player_id = ? AND actor.left_at IS NULL
                         AND target.player_id = ? AND target.left_at IS NULL
                       LIMIT 1
                    )
                    UPDATE player_party_members member
                       SET left_at = now()
                      FROM shared
                     WHERE member.party_id = shared.party_id
                       AND member.player_id = CASE
                         WHEN shared.leader_player_id = ? THEN ?::uuid
                         ELSE ?::uuid
                       END
                       AND member.left_at IS NULL
                    """)) {
                separateParty.setObject(1, actorId);
                separateParty.setObject(2, targetId);
                separateParty.setObject(3, actorId);
                separateParty.setObject(4, targetId);
                separateParty.setObject(5, actorId);
                separateParty.executeUpdate();
            }
            return BlockResult.BLOCKED;
        });
    }

    @Override
    public ReportResult report(UUID actorId, UUID targetId, String reason) {
        return transaction("record player report", connection -> {
            lockPair(connection, actorId, targetId);
            try (PreparedStatement duplicate = connection.prepareStatement("""
                    SELECT 1 FROM player_reports
                     WHERE reporter_player_id = ? AND reported_player_id = ? AND reason = ?
                       AND created_at > now() - interval '24 hours'
                     LIMIT 1
                    """)) {
                duplicate.setObject(1, actorId);
                duplicate.setObject(2, targetId);
                duplicate.setString(3, reason);
                try (ResultSet rows = duplicate.executeQuery()) {
                    if (rows.next()) return ReportResult.DUPLICATE;
                }
            }
            try (PreparedStatement recent = connection.prepareStatement("""
                    SELECT count(*) FROM player_reports
                     WHERE reporter_player_id = ? AND created_at > now() - interval '1 hour'
                    """)) {
                recent.setObject(1, actorId);
                try (ResultSet rows = recent.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) >= 10) return ReportResult.TOO_MANY;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO player_reports (reporter_player_id, reported_player_id, reason)
                    VALUES (?, ?, ?)
                    """)) {
                insert.setObject(1, actorId);
                insert.setObject(2, targetId);
                insert.setString(3, reason);
                insert.executeUpdate();
            }
            return ReportResult.RECORDED;
        });
    }

    @Override
    public Set<UUID> blockedPlayers(UUID actorId) {
        return transaction("load player blocks", connection -> {
            Set<UUID> blocked = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT blocked_player_id FROM player_blocks
                     WHERE blocker_player_id = ?
                    """)) {
                statement.setObject(1, actorId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) blocked.add(rows.getObject(1, UUID.class));
                }
            }
            return Set.copyOf(blocked);
        });
    }

    private DeleteResult delete(UUID actorId, String targetName, String predicate, boolean requesterParameter) {
        return transaction("delete friend relationship", connection -> {
            PlayerRow target = exactPlayer(connection, targetName);
            if (target == null) return DeleteResult.PLAYER_NOT_FOUND;
            if (target.name() == null) return DeleteResult.PLAYER_NAME_AMBIGUOUS;
            Pair pair = lockPair(connection, actorId, target.id());
            String sql = "DELETE FROM player_friendships WHERE player_low_id = ? AND player_high_id = ? AND "
                    + predicate;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, pair.low());
                statement.setObject(2, pair.high());
                if (requesterParameter) statement.setObject(3, target.id());
                if (statement.executeUpdate() != 1) return DeleteResult.RELATIONSHIP_NOT_FOUND;
            }
            if (!requesterParameter) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM mobile_friend_online_alerts
                         WHERE (owner_player_id = ? AND target_player_id = ?)
                            OR (owner_player_id = ? AND target_player_id = ?)
                        """)) {
                    statement.setObject(1, actorId);
                    statement.setObject(2, target.id());
                    statement.setObject(3, target.id());
                    statement.setObject(4, actorId);
                    statement.executeUpdate();
                }
            }
            return DeleteResult.DELETED;
        });
    }

    /** Null row means missing; a null name marks an ambiguous case. */
    private static PlayerRow exactPlayer(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, name FROM playerdata
                 WHERE lower(name) = lower(?)
                 ORDER BY id LIMIT 2
                """)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                PlayerRow first = new PlayerRow(rows.getObject("id", UUID.class), rows.getString("name"));
                return rows.next() ? new PlayerRow(first.id(), null) : first;
            }
        }
    }

    private static Pair lockPair(Connection connection, UUID first, UUID second) throws SQLException {
        UUID low = first.toString().compareTo(second.toString()) < 0 ? first : second;
        UUID high = low.equals(first) ? second : first;
        advisoryLock(connection, low);
        advisoryLock(connection, high);
        return new Pair(low, high);
    }

    private static void advisoryLock(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, playerId.toString());
            statement.execute();
        }
    }

    private static boolean blocked(Connection connection, UUID first, UUID second) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM player_blocks
                 WHERE (blocker_player_id = ? AND blocked_player_id = ?)
                    OR (blocker_player_id = ? AND blocked_player_id = ?)
                 LIMIT 1
                """)) {
            statement.setObject(1, first);
            statement.setObject(2, second);
            statement.setObject(3, second);
            statement.setObject(4, first);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static Existing friendship(Connection connection, Pair pair) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT requested_by_player_id, status FROM player_friendships
                 WHERE player_low_id = ? AND player_high_id = ? FOR UPDATE
                """)) {
            statement.setObject(1, pair.low());
            statement.setObject(2, pair.high());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? new Existing(rows.getObject("requested_by_player_id", UUID.class), rows.getString("status"))
                        : null;
            }
        }
    }

    private static void queueRequestNotification(Connection connection, UUID targetId, UUID actorId, String actorName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mobile_notification_outbox (kind, audience, payload)
                SELECT 'friend_request',
                       jsonb_build_object('firebaseUid', link.firebase_uid),
                       jsonb_build_object(
                         'title', 'New friend request',
                         'body', ? || ' sent you a friend request.',
                         'deepLink', 'cookiebuild://friends',
                         'data', jsonb_build_object('type', 'friend_request', 'playerId', ?::text)
                       )
                  FROM mobile_player_links link
                 WHERE link.player_id = ? AND link.is_primary AND link.revoked_at IS NULL
                 ORDER BY link.linked_at LIMIT 1
                """)) {
            statement.setString(1, actorName);
            statement.setObject(2, actorId);
            statement.setObject(3, targetId);
            statement.executeUpdate();
        }
    }

    private <T> T transaction(String operation, SqlWork<T> work) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                T result = entityManager.unwrap(Session.class)
                        .doReturningWork(connection -> work.execute(connection));
                transaction.commit();
                return result;
            } catch (Exception error) {
                if (transaction.isActive()) transaction.rollback();
                throw new FriendRepositoryException("Could not " + operation, error);
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
