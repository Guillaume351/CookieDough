package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class PartyCoordinatorTest {
    @Test
    void persistsLifecycleAndHandsLeadershipToOldestRemainingMember() {
        InMemoryPartyRepository repository = new InMemoryPartyRepository();
        PartyCoordinator coordinator = new PartyCoordinator(repository);
        UUID leader = UUID.randomUUID();
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();

        assertEquals(PartyRepository.CreateResult.CREATED, coordinator.create(leader));
        assertEquals(PartyRepository.InviteResult.INVITED, coordinator.invite(leader, firstMember));
        assertEquals(PartyRepository.JoinResult.JOINED, coordinator.join(firstMember, leader));
        assertEquals(PartyRepository.InviteResult.INVITED, coordinator.invite(leader, secondMember));
        assertEquals(PartyRepository.JoinResult.JOINED, coordinator.join(secondMember, leader));
        assertEquals(PartyRepository.LeaveResult.LEFT, coordinator.leave(leader));

        PartyRepository.Party party = coordinator.snapshot().partyFor(firstMember);
        assertNotNull(party);
        assertEquals(firstMember, party.leaderId());
        assertEquals(List.of(firstMember, secondMember), party.members());
        assertNull(coordinator.snapshot().partyFor(leader));
    }

    @Test
    void aFreshCoordinatorRestoresMembershipAfterRestartAndReconnect() {
        InMemoryPartyRepository repository = new InMemoryPartyRepository();
        PartyCoordinator firstProcess = new PartyCoordinator(repository);
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        firstProcess.create(leader);
        firstProcess.invite(leader, member);
        firstProcess.join(member, leader);

        PartyCoordinator restartedProcess = new PartyCoordinator(repository);
        restartedProcess.refresh();

        PartyRepository.Party restored = restartedProcess.snapshot().partyFor(member);
        assertNotNull(restored);
        assertEquals(leader, restored.leaderId());
        assertEquals(List.of(leader, member), restored.members());
    }

    @Test
    void concurrentAcceptsCannotExceedFourPlayers() throws Exception {
        InMemoryPartyRepository repository = new InMemoryPartyRepository();
        PartyCoordinator setup = new PartyCoordinator(repository);
        UUID leader = UUID.randomUUID();
        UUID memberTwo = UUID.randomUUID();
        UUID memberThree = UUID.randomUUID();
        UUID candidateFour = UUID.randomUUID();
        UUID rejectedFive = UUID.randomUUID();

        setup.create(leader);
        setup.invite(leader, memberTwo);
        setup.join(memberTwo, leader);
        setup.invite(leader, memberThree);
        setup.join(memberThree, leader);
        setup.invite(leader, candidateFour);
        setup.invite(leader, rejectedFive);

        PartyCoordinator paper = new PartyCoordinator(repository);
        PartyCoordinator mobileApi = new PartyCoordinator(repository);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PartyRepository.JoinResult> first = workers.submit(() -> paper.join(candidateFour, leader));
            Future<PartyRepository.JoinResult> second = workers.submit(() -> mobileApi.join(rejectedFive, leader));
            List<PartyRepository.JoinResult> results = List.of(await(first), await(second));

            assertEquals(1, results.stream().filter(result -> result == PartyRepository.JoinResult.JOINED).count());
            assertEquals(1, results.stream().filter(result -> result == PartyRepository.JoinResult.PARTY_FULL).count());
            PartyRepository.Snapshot finalState = setup.refresh();
            assertEquals(PartyRepository.MAX_PARTY_SIZE, finalState.partyFor(leader).members().size());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void snapshotRejectsOnePlayerInTwoActiveParties() {
        UUID duplicate = UUID.randomUUID();
        PartyRepository.Party first = new PartyRepository.Party(
                UUID.randomUUID(), duplicate, List.of(duplicate));
        UUID otherLeader = UUID.randomUUID();
        PartyRepository.Party second = new PartyRepository.Party(
                UUID.randomUUID(), otherLeader, List.of(otherLeader, duplicate));

        boolean rejected = false;
        try {
            PartyRepository.Snapshot.of(List.of(first, second));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    private static PartyRepository.JoinResult await(Future<PartyRepository.JoinResult> future)
            throws InterruptedException, ExecutionException {
        return future.get();
    }

    /**
     * Deterministic contract double. Synchronized methods model the database's
     * party-row and invitee advisory locks while the coordinator remains the
     * exact production code under test.
     */
    private static final class InMemoryPartyRepository implements PartyRepository {
        private final Map<UUID, State> parties = new LinkedHashMap<>();
        private final Map<UUID, UUID> partyByMember = new LinkedHashMap<>();
        private final Map<UUID, UUID> pendingPartyByInvitee = new LinkedHashMap<>();

        @Override
        public synchronized Snapshot load() {
            return Snapshot.of(parties.values().stream()
                    .map(state -> new Party(state.id, state.leader, new ArrayList<>(state.members)))
                    .toList());
        }

        @Override
        public synchronized CreateResult create(UUID leaderId) {
            if (partyByMember.containsKey(leaderId)) {
                return CreateResult.ALREADY_IN_PARTY;
            }
            State party = new State(UUID.randomUUID(), leaderId);
            party.members.add(leaderId);
            parties.put(party.id, party);
            partyByMember.put(leaderId, party.id);
            return CreateResult.CREATED;
        }

        @Override
        public synchronized InviteResult invite(UUID inviterId, UUID inviteeId) {
            if (inviterId.equals(inviteeId)) {
                return InviteResult.SELF_INVITE;
            }
            create(inviterId);
            State party = parties.get(partyByMember.get(inviterId));
            if (!party.leader.equals(inviterId)) {
                return InviteResult.NOT_LEADER;
            }
            if (party.members.size() >= MAX_PARTY_SIZE) {
                return InviteResult.PARTY_FULL;
            }
            if (partyByMember.containsKey(inviteeId)) {
                return InviteResult.TARGET_IN_PARTY;
            }
            pendingPartyByInvitee.put(inviteeId, party.id);
            return InviteResult.INVITED;
        }

        @Override
        public synchronized JoinResult join(UUID inviteeId, UUID leaderId) {
            if (partyByMember.containsKey(inviteeId)) {
                return JoinResult.ALREADY_IN_PARTY;
            }
            UUID partyId = pendingPartyByInvitee.get(inviteeId);
            State party = partyId == null ? null : parties.get(partyId);
            if (party == null || !party.leader.equals(leaderId)) {
                return JoinResult.INVITE_MISSING;
            }
            if (party.members.size() >= MAX_PARTY_SIZE) {
                return JoinResult.PARTY_FULL;
            }
            party.members.add(inviteeId);
            partyByMember.put(inviteeId, party.id);
            pendingPartyByInvitee.remove(inviteeId);
            return JoinResult.JOINED;
        }

        @Override
        public synchronized LeaveResult leave(UUID playerId) {
            UUID partyId = partyByMember.remove(playerId);
            if (partyId == null) {
                return LeaveResult.NOT_IN_PARTY;
            }
            State party = parties.get(partyId);
            party.members.remove(playerId);
            if (party.members.isEmpty()) {
                parties.remove(partyId);
                pendingPartyByInvitee.values().removeIf(partyId::equals);
            } else if (party.leader.equals(playerId)) {
                party.leader = party.members.getFirst();
                pendingPartyByInvitee.values().removeIf(partyId::equals);
            }
            return LeaveResult.LEFT;
        }

        private static final class State {
            private final UUID id;
            private UUID leader;
            private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

            private State(UUID id, UUID leader) {
                this.id = id;
                this.leader = leader;
            }
        }
    }
}
