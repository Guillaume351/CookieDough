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
import com.cookiebuild.cookiedough.retention.FriendManager;
import com.cookiebuild.cookiedough.retention.FriendRepository;
import com.cookiebuild.cookiedough.utils.DiscordUtils;

public final class SocialSafetyCommand implements CommandExecutor {
    private static final long REPORT_COOLDOWN_MS = 60_000;
    private final ChatManager chatManager;
    private final FriendManager friends;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    public SocialSafetyCommand(ChatManager chatManager, FriendManager friends) {
        this.chatManager = chatManager;
        this.friends = friends;
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
        if (command.getName().equalsIgnoreCase("mute")) {
            boolean blocked = chatManager.toggleBlock(player.getUniqueId(), target.getUniqueId());
            player.sendMessage((blocked ? ChatColor.GREEN + "Muted " : ChatColor.YELLOW + "Unmuted ")
                    + target.getName() + ".");
            return true;
        }
        if (command.getName().equalsIgnoreCase("block")) {
            friends.toggleBlock(player, target, result -> {
                boolean blocked = result == FriendRepository.BlockResult.BLOCKED;
                chatManager.setBlocked(player.getUniqueId(), target.getUniqueId(), blocked);
                player.sendMessage((blocked
                        ? ChatColor.GREEN + "Blocked "
                        : ChatColor.YELLOW + "Unblocked ") + target.getName() + ".");
            }, error -> player.sendMessage(ChatColor.RED + error));
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
        String submittedReason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String details = submittedReason.length() > 200 ? submittedReason.substring(0, 200) : submittedReason;
        friends.report(player, target, reportCategory(details), result -> {
            switch (result) {
                case RECORDED -> {
                    String report = "REPORT: " + player.getName() + " reported " + target.getName() + ": " + details;
                    CookieDough.getInstance().getLogger().warning(report);
                    DiscordUtils.sendDiscordMessage(System.getenv("DISCORD_MODERATION_WEBHOOK_URL"), report);
                    player.sendMessage(ChatColor.GREEN
                            + "Report sent. Thank you for helping keep the server welcoming.");
                }
                case DUPLICATE -> player.sendMessage(ChatColor.YELLOW
                        + "You already reported that issue recently.");
                case TOO_MANY -> player.sendMessage(ChatColor.RED
                        + "Too many reports were sent recently. Please contact the team if this is urgent.");
            }
        }, error -> player.sendMessage(ChatColor.RED + error));
        return true;
    }

    static String reportCategory(String details) {
        String normalized = details == null ? "" : details.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("spam")) return "spam";
        if (normalized.contains("racis") || normalized.contains("hate")
                || normalized.contains("discrimin")) return "hate_or_discrimination";
        if (normalized.contains("sex")) return "sexual_content";
        if (normalized.contains("threat") || normalized.contains("menace")) return "threats";
        if (normalized.contains("imperson") || normalized.contains("usurp")) return "impersonation";
        if (normalized.contains("cheat") || normalized.contains("hack") || normalized.contains("triche")) {
            return "cheating";
        }
        if (normalized.contains("name") || normalized.contains("pseudo")) return "inappropriate_name";
        return "harassment";
    }
}
