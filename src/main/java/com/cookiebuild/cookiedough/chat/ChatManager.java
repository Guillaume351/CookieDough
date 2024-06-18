package com.cookiebuild.cookiedough.chat;

import com.cookiebuild.cookiedough.model.ChatMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages chat censorship and formatting in messages.
 */
public class ChatManager {
    private List<ChatBlocker> chatBlockers = new ArrayList<>();
    private Map<Player, SpamBlocker> spamBlockers = new HashMap<>();
    private Map<Player, List<ChatMessage>> chatMessages = new HashMap<>();

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
        chatMessages.computeIfAbsent(player, k -> new ArrayList<>()).add(chatMessage);
    }

    public boolean isChatBlocked(Player player, String message) {
        SpamBlocker spamBlocker = spamBlockers.computeIfAbsent(player, k -> new SpamBlocker());

        if (spamBlocker.matches(message)) {
            return true;
        }

        for (ChatBlocker chatBlocker : chatBlockers) {
            if (chatBlocker.matches(message)) {
                return true;
            }
        }
        return false;
    }
}