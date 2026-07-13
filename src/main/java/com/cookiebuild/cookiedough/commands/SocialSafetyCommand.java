package com.cookiebuild.cookiedough.commands;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.utils.DiscordUtils;

public final class SocialSafetyCommand implements CommandExecutor {
    private static final long REPORT_COOLDOWN_MS = 60_000;
    private final ChatManager chatManager;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    public SocialSafetyCommand(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "/" + label + " <player>" +
                    (command.getName().equalsIgnoreCase("report") ? " <reason>" : ""));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "That player is not available.");
            return true;
        }
        if (!command.getName().equalsIgnoreCase("report")) {
            boolean blocked = chatManager.toggleBlock(player.getUniqueId(), target.getUniqueId());
            player.sendMessage((blocked ? ChatColor.GREEN + "Hidden messages from " : ChatColor.YELLOW + "Showing messages from ")
                    + target.getName() + ".");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "/report <player> <reason>");
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastReport.getOrDefault(player.getUniqueId(), 0L) < REPORT_COOLDOWN_MS) {
            player.sendMessage(ChatColor.RED + "Please wait before sending another report.");
            return true;
        }
        lastReport.put(player.getUniqueId(), now);
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        String report = "REPORT: " + player.getName() + " reported " + target.getName() + ": " + reason;
        CookieDough.getInstance().getLogger().warning(report);
        DiscordUtils.sendDiscordMessage(System.getenv("DISCORD_MODERATION_WEBHOOK_URL"), report);
        player.sendMessage(ChatColor.GREEN + "Report sent. Thank you for helping keep the server welcoming.");
        return true;
    }
}
