package com.cookiebuild.cookiedough.retention;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable party storage shared by Paper and the mobile API. */
public interface PartyRepository {
    int MAX_PARTY_SIZE = 4;

    enum CreateResult {
        CREATED,
        ALREADY_IN_PARTY
    }

    enum InviteResult {
        INVITED,
        NOT_LEADER,
        PARTY_FULL,
        TARGET_IN_PARTY,
        SELF_INVITE
    }

    enum JoinResult {
        JOINED,
        INVITE_MISSING,
        INVITE_EXPIRED,
        PARTY_FULL,
        ALREADY_IN_PARTY
    }

    enum LeaveResult {
        LEFT,
        NOT_IN_PARTY
    }

    record Party(UUID id, UUID leaderId, List<UUID> members) {
        public Party {
            members = List.copyOf(members);
            if (members.isEmpty() || members.size() > MAX_PARTY_SIZE || !members.contains(leaderId)) {
                throw new IllegalArgumentException("Invalid durable party snapshot");
            }
        }
    }

    record Snapshot(Map<UUID, Party> parties, Map<UUID, UUID> partyByMember) {
        public Snapshot {
            parties = Map.copyOf(parties);
            partyByMember = Map.copyOf(partyByMember);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }

        public static Snapshot of(List<Party> parties) {
            Map<UUID, Party> byId = new LinkedHashMap<>();
            Map<UUID, UUID> byMember = new LinkedHashMap<>();
            for (Party party : parties) {
                if (byId.putIfAbsent(party.id(), party) != null) {
                    throw new IllegalArgumentException("Duplicate party in snapshot: " + party.id());
                }
                for (UUID member : party.members()) {
                    if (byMember.putIfAbsent(member, party.id()) != null) {
                        throw new IllegalArgumentException("Player belongs to multiple active parties: " + member);
                    }
                }
            }
            return new Snapshot(byId, byMember);
        }

        public Party partyFor(UUID playerId) {
            UUID partyId = partyByMember.get(playerId);
            return partyId == null ? null : parties.get(partyId);
        }
    }

    Snapshot load();

    CreateResult create(UUID leaderId);

    InviteResult invite(UUID inviterId, UUID inviteeId);

    JoinResult join(UUID inviteeId, UUID leaderId);

    LeaveResult leave(UUID playerId);
}
