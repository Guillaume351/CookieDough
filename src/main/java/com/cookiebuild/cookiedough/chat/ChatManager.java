package com.cookiebuild.cookiedough.chat;

import com.cookiebuild.cookiedough.model.ChatMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chat censorship and formatting in messages.
 */
public class ChatManager {
    public record ModerationResult(boolean blocked, String reason) {
    }

    private static final int MAX_RECENT_MESSAGES = 50;
    private final List<ChatBlocker> chatBlockers = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, SpamBlocker> spamBlockers = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<ChatMessage>> chatMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blockedPlayers = new ConcurrentHashMap<>();

    public void addChatBlocker(ChatBlocker chatBlocker) {
        chatBlockers.add(chatBlocker);
    }

    public void removeChatBlocker(ChatBlocker chatBlocker) {
        chatBlockers.remove(chatBlocker);
    }

    public List<ChatBlocker> getChatBlockers() {
        return chatBlockers;
    }

    public void clearChatBlockers() {
        chatBlockers.clear();
    }

    public void addChatMessage(Player player, ChatMessage chatMessage) {
        ArrayDeque<ChatMessage> messages = chatMessages.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        synchronized (messages) {
            messages.addLast(chatMessage);
            while (messages.size() > MAX_RECENT_MESSAGES) {
                messages.removeFirst();
            }
        }
    }

    public boolean isChatBlocked(Player player, String message) {
        return checkChat(player, message).blocked();
    }

    public ModerationResult checkChat(Player player, String message) {
        SpamBlocker spamBlocker = spamBlockers.computeIfAbsent(player.getUniqueId(), ignored -> new SpamBlocker());

        if (spamBlocker.matches(message)) {
            return new ModerationResult(true, "You're sending messages too quickly. Please wait a moment.");
        }

        synchronized (chatBlockers) {
            for (ChatBlocker chatBlocker : chatBlockers) {
                if (chatBlocker.matches(message)) {
                    return new ModerationResult(true, "That message was blocked by the chat filter.");
                }
            }
        }
        return new ModerationResult(false, "");
    }

    public boolean toggleBlock(UUID viewer, UUID sender) {
        Set<UUID> blocked = blockedPlayers.computeIfAbsent(viewer, ignored -> ConcurrentHashMap.newKeySet());
        if (!blocked.add(sender)) {
            blocked.remove(sender);
            return false;
        }
        return true;
    }

    public void setBlocked(UUID viewer, UUID sender, boolean blockedState) {
        Set<UUID> blocked = blockedPlayers.computeIfAbsent(viewer, ignored -> ConcurrentHashMap.newKeySet());
        if (blockedState) blocked.add(sender);
        else blocked.remove(sender);
    }

    public void replaceBlockedPlayers(UUID viewer, Set<UUID> blocked) {
        Set<UUID> replacement = ConcurrentHashMap.newKeySet();
        replacement.addAll(blocked);
        blockedPlayers.put(viewer, replacement);
    }

    public boolean isBlocked(UUID viewer, UUID sender) {
        return blockedPlayers.getOrDefault(viewer, Set.of()).contains(sender);
    }

    public void cleanup(UUID playerId) {
        spamBlockers.remove(playerId);
        chatMessages.remove(playerId);
        blockedPlayers.remove(playerId);
    }
}
