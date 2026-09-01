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
            player.sendMessage(ChatColor.YELLOW + "/feedback <message>");
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastFeedback.getOrDefault(player.getUniqueId(), 0L) < COOLDOWN_MS) {
            player.sendMessage(ChatColor.RED + "Please wait before sending more feedback.");
            return true;
        }
        String message = String.join(" ", args).replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        String webhook = System.getenv("DISCORD_FEEDBACK_WEBHOOK_URL");
        if (webhook == null || webhook.isBlank()) {
            player.sendMessage(ChatColor.RED + "Feedback delivery is temporarily unavailable. Please use /events for our community link.");
            return true;
        }
        lastFeedback.put(player.getUniqueId(), now);
        player.sendMessage(ChatColor.YELLOW + "Sending your feedback…");
        DiscordUtils.sendDiscordMessage(webhook, "PLAYER FEEDBACK from " + player.getName() + ": " + message)
                .thenAccept(delivered -> CookieDough.getInstance().getServer().getScheduler().runTask(
                        CookieDough.getInstance(), () -> {
                            FunnelTelemetry.record(player, FunnelTelemetry.Event.FEEDBACK,
                                    "delivered=" + delivered);
                            if (player.isOnline()) {
                                player.sendMessage((delivered ? ChatColor.GREEN : ChatColor.RED)
                                        + (delivered ? "Feedback sent. Thank you!"
                                                : "Feedback could not be delivered. Please try again later."));
                            }
                        }));
        return true;
    }
}
