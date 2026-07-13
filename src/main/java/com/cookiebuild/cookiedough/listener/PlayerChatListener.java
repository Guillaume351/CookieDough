package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.model.ChatMessage;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerChatListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER =
            PlainTextComponentSerializer.plainText();

    private final ChatManager chatManager;

    public PlayerChatListener(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PLAIN_TEXT_SERIALIZER.serialize(event.message());

        if (chatManager.isChatBlocked(player, message)) {
            event.setCancelled(true);
            return;
        }

        ChatMessage chatMessage = new ChatMessage(player.getUniqueId(), player.getWorld().getName(), message);

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            GenericDAOImpl<ChatMessage> chatMessageDAO = new GenericDAOImpl<>(ChatMessage.class);
            chatMessageDAO.save(chatMessage);
        });


        chatManager.addChatMessage(player, chatMessage);
    }
}
