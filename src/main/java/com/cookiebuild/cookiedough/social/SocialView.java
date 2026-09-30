package com.cookiebuild.cookiedough.social;

import java.util.List;
import java.util.UUID;

/**
 * Immutable, Bukkit-free snapshot the social menu is rendered from: the durable
 * friend graph (loaded asynchronously) joined with live, main-thread server state.
 */
public record SocialView(
        String viewerName,
        boolean bedrock,
        boolean viewerCanQueue,
        String viewerActivity,
        List<Friend> friends,
        List<String> incoming,
        List<String> outgoing,
        Party party,
        List<PartyInvite> partyInvites,
        List<String> onlinePlayers) {

    public SocialView {
        viewerActivity = viewerActivity == null ? "" : viewerActivity;
        friends = List.copyOf(friends);
        incoming = List.copyOf(incoming);
        outgoing = List.copyOf(outgoing);
        partyInvites = List.copyOf(partyInvites);
        onlinePlayers = List.copyOf(onlinePlayers);
    }

    /** What an online friend is doing; only the first six mean "on this server". */
    public enum Presence {
        LOBBY,
        QUEUED,
        PLAYING,
        SPECTATING,
        ACTIVITY,
        ONLINE,
        ELSEWHERE,
        OFFLINE;

        public boolean onServer() {
            return this != ELSEWHERE && this != OFFLINE;
        }
    }

    public record Friend(String name, Presence presence, String activity, boolean inMyParty) {
        public Friend {
            activity = activity == null ? "" : activity;
        }

        /** A friend can be followed only into a named queue, match or persistent activity. */
        public boolean joinable() {
            return !activity.isBlank()
                    && (presence == Presence.QUEUED || presence == Presence.PLAYING || presence == Presence.ACTIVITY);
        }
    }

    public record Member(String name, boolean online, boolean leader) {
    }

    public record Party(String leaderName, boolean viewerIsLeader, List<Member> members, int maxSize) {
        public Party {
            members = List.copyOf(members);
        }

        public boolean full() {
            return members.size() >= maxSize;
        }
    }

    public record PartyInvite(UUID leaderId, String leaderName) {
    }

    public Friend friend(String name) {
        return friends.stream().filter(friend -> friend.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public List<Friend> friendsOnServer() {
        return friends.stream().filter(friend -> friend.presence().onServer()).toList();
    }

    public boolean hasIncoming(String name) {
        return incoming.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(name));
    }

    /** A viewer may invite when solo (the invite creates the party) or when leading a party with room. */
    public boolean canInvite() {
        return party == null || (party.viewerIsLeader() && !party.full());
    }
}
