package com.cookiebuild.cookiedough.retention;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.Session;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** PostgreSQL implementation of the party tables shared with the mobile API. */
public final class PostgresPartyRepository implements PartyRepository {
    private static final String ACTIVE_MEMBERSHIP = """
            SELECT p.id, p.leader_player_id, m.role
              FROM player_party_members m
              JOIN player_parties p ON p.id = m.party_id
             WHERE m.player_id = ?
               AND m.left_at IS NULL
               AND p.state = 'active'
             FOR UPDATE OF p, m
            """;

    private record Membership(UUID partyId, UUID leaderId, String role) {
    }

    @Override
    public Snapshot load() {
        return transaction("load durable parties", connection -> {
            Map<UUID, PartyAccumulator> parties = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT p.id, p.leader_player_id, m.player_id, m.role
                      FROM player_parties p
                      LEFT JOIN player_party_members m
                        ON m.party_id = p.id AND m.left_at IS NULL
                     WHERE p.state = 'active'
                     ORDER BY p.created_at, p.id, m.joined_at, m.player_id
                    """);
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID partyId = rows.getObject("id", UUID.class);
                    UUID leaderId = rows.getObject("leader_player_id", UUID.class);
                    UUID memberId = rows.getObject("player_id", UUID.class);
                    String role = rows.getString("role");
                    if (memberId == null) {
                        throw new PartyRepositoryException("Active party has no active members");
                    }
                    PartyAccumulator party = parties.computeIfAbsent(
                            partyId, ignored -> new PartyAccumulator(partyId, leaderId));
                    if (!party.leaderId.equals(leaderId)) {
                        throw new PartyRepositoryException("Party leader changed within one database snapshot");
                    }
                    party.members.add(memberId);
                    if ("leader".equals(role)) {
                        party.leaderRoles += 1;
                        if (!leaderId.equals(memberId)) {
                            throw new PartyRepositoryException("Active leader role does not match player_parties");
                        }
                    }
                }
            }

            List<Party> snapshot = new ArrayList<>(parties.size());
            for (PartyAccumulator party : parties.values()) {
                if (party.leaderRoles != 1) {
                    throw new PartyRepositoryException("Active party must have exactly one active leader member");
                }
                snapshot.add(new Party(party.id, party.leaderId, party.members));
            }
            return Snapshot.of(snapshot);
        });
    }

    @Override
    public CreateResult create(UUID leaderId) {
        return transaction("create durable party", connection -> {
            advisoryLock(connection, leaderId);
            if (activeMembership(connection, leaderId) != null) {
                return CreateResult.ALREADY_IN_PARTY;
            }

            UUID partyId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_parties
                        (id, leader_player_id, state, created_at, updated_at, disbanded_at)
                    VALUES (?, ?, 'active', now(), now(), NULL)
                    """)) {
                statement.setObject(1, partyId);
                statement.setObject(2, leaderId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_party_members
                        (party_id, player_id, role, joined_at, left_at)
                    VALUES (?, ?, 'leader', now(), NULL)
                    """)) {
                statement.setObject(1, partyId);
                statement.setObject(2, leaderId);
                statement.executeUpdate();
            }
            return CreateResult.CREATED;
        });
    }

    @Override
    public InviteResult invite(UUID inviterId, UUID inviteeId) {
        if (inviterId.equals(inviteeId)) {
            return InviteResult.SELF_INVITE;
        }

        // Preserve the in-game contract where /party invite also creates the
        // inviter's party. Keeping creation in its own transaction prevents a
        // cross-invite deadlock while the shared invite transaction follows the
        // website's invitee-advisory-lock then party-row-lock order.
        create(inviterId);

        return transaction("create durable party invitation", connection -> {
            advisoryLock(connection, inviteeId);
            if (activeMembership(connection, inviteeId) != null) {
                return InviteResult.TARGET_IN_PARTY;
            }

            Membership inviter = activeMembership(connection, inviterId);
            if (inviter == null || !inviterId.equals(inviter.leaderId()) || !"leader".equals(inviter.role())) {
                return InviteResult.NOT_LEADER;
            }
            if (activeMemberCount(connection, inviter.partyId()) >= MAX_PARTY_SIZE) {
                return InviteResult.PARTY_FULL;
            }

            UUID inviteId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_party_invites
                        (id, party_id, inviter_player_id, invitee_player_id, status,
                         created_at, expires_at, responded_at)
                    VALUES (?, ?, ?, ?, 'pending', now(), now() + interval '15 minutes', NULL)
                    ON CONFLICT (party_id, invitee_player_id) WHERE status = 'pending'
                    DO UPDATE SET
                        inviter_player_id = EXCLUDED.inviter_player_id,
                        created_at = now(),
                        expires_at = now() + interval '15 minutes',
                        responded_at = NULL
                    """)) {
                statement.setObject(1, inviteId);
                statement.setObject(2, inviter.partyId());
                statement.setObject(3, inviterId);
                statement.setObject(4, inviteeId);
                statement.executeUpdate();
            }
            return InviteResult.INVITED;
        });
    }

    @Override
    public JoinResult join(UUID inviteeId, UUID leaderId) {
        return transaction("accept durable party invitation", connection -> {
            advisoryLock(connection, inviteeId);
            if (activeMembership(connection, inviteeId) != null) {
                return JoinResult.ALREADY_IN_PARTY;
            }

            UUID inviteId;
            UUID partyId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT i.id, i.party_id
                      FROM player_party_invites i
                      JOIN player_parties p ON p.id = i.party_id
                     WHERE i.invitee_player_id = ?
                       AND i.status = 'pending'
                       AND p.state = 'active'
                       AND p.leader_player_id = ?
                     ORDER BY i.created_at DESC
                     LIMIT 1
                    """)) {
                statement.setObject(1, inviteeId);
                statement.setObject(2, leaderId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return JoinResult.INVITE_MISSING;
                    }
                    inviteId = rows.getObject("id", UUID.class);
                    partyId = rows.getObject("party_id", UUID.class);
                }
            }

            // Match the website lock order exactly: invitee advisory lock,
            // then active party row, then the invitation row.
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id
                      FROM player_parties
                     WHERE id = ? AND leader_player_id = ? AND state = 'active'
                     FOR UPDATE
                    """)) {
                statement.setObject(1, partyId);
                statement.setObject(2, leaderId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return JoinResult.INVITE_MISSING;
                    }
                }
            }

            boolean expired;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT expires_at <= now() AS expired
                      FROM player_party_invites
                     WHERE id = ?
                       AND party_id = ?
                       AND invitee_player_id = ?
                       AND status = 'pending'
                     FOR UPDATE
                    """)) {
                statement.setObject(1, inviteId);
                statement.setObject(2, partyId);
                statement.setObject(3, inviteeId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return JoinResult.INVITE_MISSING;
                    }
                    expired = rows.getBoolean("expired");
                }
            }

            if (expired) {
                respondToInvite(connection, inviteId, "expired");
                return JoinResult.INVITE_EXPIRED;
            }
            if (activeMemberCount(connection, partyId) >= MAX_PARTY_SIZE) {
                return JoinResult.PARTY_FULL;
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_party_members
                        (party_id, player_id, role, joined_at, left_at)
                    VALUES (?, ?, 'member', now(), NULL)
                    ON CONFLICT (party_id, player_id)
                    DO UPDATE SET role = 'member', joined_at = now(), left_at = NULL
                    """)) {
                statement.setObject(1, partyId);
                statement.setObject(2, inviteeId);
                statement.executeUpdate();
            }
            respondToInvite(connection, inviteId, "accepted");
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_party_invites
                       SET status = 'cancelled', responded_at = now()
                     WHERE invitee_player_id = ?
                       AND status = 'pending'
                       AND id <> ?
                    """)) {
                statement.setObject(1, inviteeId);
                statement.setObject(2, inviteId);
                statement.executeUpdate();
            }
            touchParty(connection, partyId);
            return JoinResult.JOINED;
        });
    }

    @Override
    public LeaveResult leave(UUID playerId) {
        return transaction("leave durable party", connection -> {
            advisoryLock(connection, playerId);
            Membership membership = activeMembership(connection, playerId);
            if (membership == null) {
                return LeaveResult.NOT_IN_PARTY;
            }

            boolean wasLeader = playerId.equals(membership.leaderId()) && "leader".equals(membership.role());
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_party_members
                       SET role = 'member', left_at = now()
                     WHERE party_id = ? AND player_id = ? AND left_at IS NULL
                    """)) {
                statement.setObject(1, membership.partyId());
                statement.setObject(2, playerId);
                statement.executeUpdate();
            }

            UUID nextLeader = firstActiveMember(connection, membership.partyId());
            if (nextLeader == null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE player_parties
                           SET state = 'disbanded', updated_at = now(), disbanded_at = now()
                         WHERE id = ? AND state = 'active'
                        """)) {
                    statement.setObject(1, membership.partyId());
                    statement.executeUpdate();
                }
                cancelPendingInvites(connection, membership.partyId());
            } else if (wasLeader) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE player_party_members
                           SET role = 'leader'
                         WHERE party_id = ? AND player_id = ? AND left_at IS NULL
                        """)) {
                    statement.setObject(1, membership.partyId());
                    statement.setObject(2, nextLeader);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE player_parties
                           SET leader_player_id = ?, updated_at = now()
                         WHERE id = ? AND state = 'active'
                        """)) {
                    statement.setObject(1, nextLeader);
                    statement.setObject(2, membership.partyId());
                    statement.executeUpdate();
                }
                // Invites created by the previous leader were invalidated by
                // the former in-memory implementation as well.
                cancelPendingInvites(connection, membership.partyId());
            } else {
                touchParty(connection, membership.partyId());
            }
            return LeaveResult.LEFT;
        });
    }

    private static Membership activeMembership(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_MEMBERSHIP)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? new Membership(rows.getObject(1, UUID.class), rows.getObject(2, UUID.class), rows.getString(3))
                        : null;
            }
        }
    }

    private static int activeMemberCount(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                  FROM player_party_members
                 WHERE party_id = ? AND left_at IS NULL
                """)) {
            statement.setObject(1, partyId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static UUID firstActiveMember(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id
                  FROM player_party_members
                 WHERE party_id = ? AND left_at IS NULL
                 ORDER BY joined_at, player_id
                 LIMIT 1
                """)) {
            statement.setObject(1, partyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getObject(1, UUID.class) : null;
            }
        }
    }

    private static void respondToInvite(Connection connection, UUID inviteId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE player_party_invites
                   SET status = ?, responded_at = now()
                 WHERE id = ? AND status = 'pending'
                """)) {
            statement.setString(1, status);
            statement.setObject(2, inviteId);
            statement.executeUpdate();
        }
    }

    private static void cancelPendingInvites(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE player_party_invites
                   SET status = 'cancelled', responded_at = now()
                 WHERE party_id = ? AND status = 'pending'
                """)) {
            statement.setObject(1, partyId);
            statement.executeUpdate();
        }
    }

    private static void touchParty(Connection connection, UUID partyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE player_parties SET updated_at = now() WHERE id = ? AND state = 'active'
                """)) {
            statement.setObject(1, partyId);
            statement.executeUpdate();
        }
    }

    private static void advisoryLock(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, playerId.toString());
            statement.execute();
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
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                if (error instanceof PartyRepositoryException repositoryError) {
                    throw repositoryError;
                }
                throw new PartyRepositoryException("Could not " + operation, error);
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private static final class PartyAccumulator {
        private final UUID id;
        private final UUID leaderId;
        private final List<UUID> members = new ArrayList<>();
        private int leaderRoles;

        private PartyAccumulator(UUID id, UUID leaderId) {
            this.id = id;
            this.leaderId = leaderId;
        }
    }
}
