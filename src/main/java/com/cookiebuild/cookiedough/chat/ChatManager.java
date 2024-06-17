package com.cookiebuild.cookiedough.chat;

import java.util.ArrayList;

/**
 * Used for Chat censorship & username / roles formatting in messages
 */
public class ChatManager {
    ArrayList<ChatBlocker> chatBlockers = new ArrayList<>();

    public void addChatBlocker(ChatBlocker chatBlocker) {
        chatBlockers.add(chatBlocker);
    }

    public void removeChatBlocker(ChatBlocker chatBlocker) {
        chatBlockers.remove(chatBlocker);
    }

    public ArrayList<ChatBlocker> getChatBlockers() {
        return chatBlockers;
    }

    public void clearChatBlockers() {
        chatBlockers.clear();
    }

    public boolean isChatBlocked(String message) {
        for (ChatBlocker chatBlocker : chatBlockers) {
            if (chatBlocker.matches(message)) {
                return true;
            }
        }
        return false;
    }
}
