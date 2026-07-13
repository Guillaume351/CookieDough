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

        ChatManager.ModerationResult moderation = chatManager.checkChat(player, message);
        if (moderation.blocked()) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.Component.text(moderation.reason(),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        event.viewers().removeIf(audience -> audience instanceof Player viewer
                && chatManager.isBlocked(viewer.getUniqueId(), player.getUniqueId()));

        ChatMessage chatMessage = new ChatMessage(player.getUniqueId(), player.getWorld().getName(), message);

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            GenericDAOImpl<ChatMessage> chatMessageDAO = new GenericDAOImpl<>(ChatMessage.class);
            chatMessageDAO.save(chatMessage);
        });
        chatManager.addChatMessage(player, chatMessage);
    }
}
