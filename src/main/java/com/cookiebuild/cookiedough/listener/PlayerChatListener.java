package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.model.ChatMessage;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PlayerChatListener implements Listener {

    private final ChatManager chatManager;

    public PlayerChatListener(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // TODO: see the new messages api
        if (chatManager.isChatBlocked(player, event.message().toString())) {
            event.setCancelled(true);
            return;
        }

        ChatMessage chatMessage = new ChatMessage(player.getUniqueId(), event.message().toString());
        chatManager.addChatMessage(player, chatMessage);
    }
}