package com.cookiebuild.cookiedough.commands;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.DiscordUtils;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;

public final class FeedbackCommand implements CommandExecutor {
    private static final long COOLDOWN_MS = 120_000;
    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                    "feedback.command.usage", player.locale()));
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastFeedback.getOrDefault(player.getUniqueId(), 0L) < COOLDOWN_MS) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "feedback.command.cooldown", player.locale()));
            return true;
        }
        String message = normalizeMessage(args);
        String dedicatedWebhook = System.getenv("DISCORD_FEEDBACK_WEBHOOK_URL");
        String webhook = selectWebhook(dedicatedWebhook,
                System.getenv("DISCORD_PLAYER_STATUS_WEBHOOK_URL"));
        if (webhook == null) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "feedback.command.unavailable", player.locale()));
            return true;
        }
        String channel = dedicatedWebhook != null && !dedicatedWebhook.isBlank()
                ? "dedicated" : "player_status";
        lastFeedback.put(player.getUniqueId(), now);
        player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                "feedback.command.sending", player.locale()));
        DiscordUtils.sendDiscordMessage(webhook, "PLAYER FEEDBACK from " + player.getName() + ": " + message)
                .thenAccept(delivered -> CookieDough.getInstance().getServer().getScheduler().runTask(
                        CookieDough.getInstance(), () -> {
                            FunnelTelemetry.record(player, FunnelTelemetry.Event.FEEDBACK,
                                    "delivered=" + delivered + " channel=" + channel);
                            if (player.isOnline()) {
                                player.sendMessage((delivered ? ChatColor.GREEN : ChatColor.RED)
                                        + LocaleManager.getMessage(delivered
                                                        ? "feedback.command.sent" : "feedback.command.failed",
                                                player.locale()));
                            }
                        }));
        return true;
    }

    static String selectWebhook(String dedicatedWebhook, String sharedStatusWebhook) {
        if (dedicatedWebhook != null && !dedicatedWebhook.isBlank()) {
            return dedicatedWebhook;
        }
        return sharedStatusWebhook == null || sharedStatusWebhook.isBlank() ? null : sharedStatusWebhook;
    }

    static String normalizeMessage(String[] args) {
        String message = String.join(" ", args).replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
