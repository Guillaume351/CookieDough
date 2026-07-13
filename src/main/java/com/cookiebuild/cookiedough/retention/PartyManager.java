package com.cookiebuild.cookiedough.retention;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;

/** Small in-memory parties; membership intentionally expires on restart. */
public final class PartyManager {
    private static final int MAX_PARTY_SIZE = 4;
    private static final long INVITE_TTL_MS = 120_000;
    private record Invitation(UUID leaderId, long expiresAt) {
    }

    private final Map<UUID, LinkedHashSet<UUID>> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invitation> invitations = new ConcurrentHashMap<>();

    public boolean create(Player leader) {
        if (partyByMember.containsKey(leader.getUniqueId())) {
            return false;
        }
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(leader.getUniqueId());
        parties.put(leader.getUniqueId(), members);
        partyByMember.put(leader.getUniqueId(), leader.getUniqueId());
        return true;
    }

    public String invite(Player inviter, Player target) {
        UUID leaderId = partyByMember.get(inviter.getUniqueId());
        if (leaderId == null) {
            create(inviter);
            leaderId = inviter.getUniqueId();
        }
        if (!leaderId.equals(inviter.getUniqueId())) {
            return "Only the party leader can invite players.";
        }
        Set<UUID> members = parties.get(leaderId);
        if (members == null || members.size() >= MAX_PARTY_SIZE) {
            return "Your party is full.";
        }
        if (partyByMember.containsKey(target.getUniqueId())) {
            return target.getName() + " is already in a party.";
        }
        invitations.put(target.getUniqueId(), new Invitation(leaderId, System.currentTimeMillis() + INVITE_TTL_MS));
        target.sendMessage(ChatColor.GOLD + inviter.getName() + " invited you to a party. "
                + ChatColor.YELLOW + "Use /party join " + inviter.getName());
        return "Party invitation sent to " + target.getName() + ".";
    }

    public String join(Player player, Player leader) {
        Invitation invitation = invitations.remove(player.getUniqueId());
        if (invitation == null || invitation.expiresAt() < System.currentTimeMillis()
                || !invitation.leaderId().equals(leader.getUniqueId())) {
            return "That party invitation is missing or expired.";
        }
        LinkedHashSet<UUID> members = parties.get(invitation.leaderId());
        if (members == null || members.size() >= MAX_PARTY_SIZE || partyByMember.containsKey(player.getUniqueId())) {
            return "You could not join that party.";
        }
        members.add(player.getUniqueId());
        partyByMember.put(player.getUniqueId(), invitation.leaderId());
        broadcast(invitation.leaderId(), player.getName() + " joined the party.");
        return "Joined " + leader.getName() + "'s party.";
    }

    public String leave(Player player) {
        UUID leaderId = partyByMember.remove(player.getUniqueId());
        if (leaderId == null) {
            return "You are not in a party.";
        }
        LinkedHashSet<UUID> members = parties.get(leaderId);
        if (members == null) {
            return "You left the party.";
        }
        members.remove(player.getUniqueId());
        if (leaderId.equals(player.getUniqueId())) {
            if (members.isEmpty()) {
                parties.remove(leaderId);
            } else {
                UUID newLeader = members.getFirst();
                parties.remove(leaderId);
                parties.put(newLeader, members);
                members.forEach(member -> partyByMember.put(member, newLeader));
                broadcast(newLeader, player.getName() + " left. " + playerName(newLeader) + " is the new leader.");
            }
        } else {
            broadcast(leaderId, player.getName() + " left the party.");
        }
        return "You left the party.";
    }

    public String queueParty(Player requester) {
        UUID leaderId = partyByMember.get(requester.getUniqueId());
        if (leaderId == null) {
            return null; // Caller performs solo Quick Play.
        }
        if (!leaderId.equals(requester.getUniqueId())) {
            return "Only the party leader can start Quick Play.";
        }
        List<CookiePlayer> members = onlineCookiePlayers(leaderId);
        if (members.stream().anyMatch(member -> !PlayerWrapperListener.isPlayerDataReady(
                member.getPlayer().getUniqueId()))) {
            return "A party member's profile is still loading.";
        }
        Game game = GameManager.getGames().stream()
                .filter(candidate -> candidate.getState() == com.cookiebuild.cookiedough.game.GameState.OPEN)
                .filter(candidate -> candidate.getCapacity() - candidate.getPlayerCount() >= members.size())
                .max(java.util.Comparator.comparingInt(Game::getPlayerCount)).orElse(null);
        if (game == null) {
            return "No game currently has room for the whole party.";
        }
        List<CookiePlayer> added = new ArrayList<>();
        for (CookiePlayer member : members) {
            if (!game.addPlayerToAvailableTeam(member)) {
                added.forEach(addedMember -> game.removePlayer(addedMember, "party_admission_rollback"));
                return "The party could not join together. Please try again.";
            }
            added.add(member);
        }
        broadcast(leaderId, "Party Quick Play: joined " + game.getGameName() + " ("
                + game.getPlayerCount() + "/" + game.getCapacity() + ").");
        return "";
    }

    public UUID getPartyId(UUID playerId) {
        return partyByMember.get(playerId);
    }

    public boolean arePartyMembers(UUID first, UUID second) {
        UUID party = partyByMember.get(first);
        return party != null && party.equals(partyByMember.get(second));
    }

    public List<UUID> getMembers(UUID playerId) {
        UUID leader = partyByMember.get(playerId);
        return leader == null ? List.of() : List.copyOf(parties.getOrDefault(leader, new LinkedHashSet<>()));
    }

    public String describe(Player player) {
        UUID leader = partyByMember.get(player.getUniqueId());
        if (leader == null) {
            return "You are not in a party. Use /party create or /party invite <player>.";
        }
        return "Party leader: " + playerName(leader) + " | Members: " + getMembers(player.getUniqueId()).stream()
                .map(this::playerName).toList();
    }

    public void disconnect(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            leave(player);
        } else {
            UUID leaderId = partyByMember.remove(playerId);
            LinkedHashSet<UUID> members = leaderId == null ? null : parties.get(leaderId);
            if (members != null) {
                members.remove(playerId);
                if (leaderId.equals(playerId)) {
                    parties.remove(leaderId);
                    if (!members.isEmpty()) {
                        UUID newLeader = members.getFirst();
                        parties.put(newLeader, members);
                        members.forEach(member -> partyByMember.put(member, newLeader));
                    }
                }
            }
        }
        invitations.remove(playerId);
    }

    private List<CookiePlayer> onlineCookiePlayers(UUID leaderId) {
        return parties.getOrDefault(leaderId, new LinkedHashSet<>()).stream()
                .map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).map(PlayerManager::getPlayer)
                .filter(java.util.Objects::nonNull).toList();
    }

    private void broadcast(UUID leaderId, String message) {
        for (UUID member : parties.getOrDefault(leaderId, new LinkedHashSet<>())) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "[Party] " + ChatColor.YELLOW + message);
            }
        }
    }

    private String playerName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }
}
