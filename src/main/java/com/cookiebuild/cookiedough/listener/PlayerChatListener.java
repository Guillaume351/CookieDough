package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.model.ChatMessage;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import com.cookiebuild.cookiedough.cosmetics.CosmeticCatalog;
import com.cookiebuild.cookiedough.cosmetics.CosmeticSlot;
import com.cookiebuild.cookiedough.cosmetics.SupporterChatRenderer;
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

        var moderationService = CookieDough.getInstance().getModerationService();
        if (moderationService != null && moderationService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "You are currently muted.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        ChatManager.ModerationResult moderation = chatManager.checkChat(player, message);
        if (moderation.blocked()) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.Component.text(moderation.reason(),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        event.viewers().removeIf(audience -> audience instanceof Player viewer
                && chatManager.isBlocked(viewer.getUniqueId(), player.getUniqueId()));

        // Wrap the renderer only after moderation/viewer filtering. The original
        // display name, message component and viewer-aware renderer remain the
        // source of truth; only the authorized Supporter name prefix is added.
        var cosmeticEffects = CookieDough.getInstance().getCosmeticEffects();
        if (cosmeticEffects != null) {
            ChatRenderer originalRenderer = event.renderer();
            event.renderer(SupporterChatRenderer.wrap(originalRenderer,
                    playerId -> cosmeticEffects.hasActiveSelection(playerId,
                            CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE)));
        }

        ChatMessage chatMessage = new ChatMessage(player.getUniqueId(), player.getWorld().getName(), message);

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            GenericDAOImpl<ChatMessage> chatMessageDAO = new GenericDAOImpl<>(ChatMessage.class);
            chatMessageDAO.save(chatMessage);
        });
        chatManager.addChatMessage(player, chatMessage);
    }
}
