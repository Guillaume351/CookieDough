package com.cookiebuild.cookiedough.commands;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.service.MobileLinkService;
import com.cookiebuild.cookiedough.service.MobileLinkService.LinkChallenge;
import com.cookiebuild.cookiedough.service.MobileLinkService.Purpose;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Creates commerce-session challenges without exposing a player identity. */
public final class SupportLinkCommand implements CommandExecutor {
    private static final long LINK_COOLDOWN_MS = 30_000L;
    private static final String SHOP_URL = "https://www.cookie-build.com/cosmetics";

    private final CookieDough plugin;
    private final MobileLinkService linkService;
    private final ScheduledExecutorService executor;
    private final LinkChallengeThrottle throttle = new LinkChallengeThrottle(LINK_COOLDOWN_MS);

    public SupportLinkCommand(CookieDough plugin, MobileLinkService linkService) {
        this.plugin = plugin;
        this.linkService = linkService;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CookieDough-SupportLink");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length != 1 || !"link".equals(args[0].toLowerCase(Locale.ROOT))) {
            player.sendMessage(ChatColor.YELLOW + message(player, "support.link.help"));
            return true;
        }
        createLink(player);
        return true;
    }

    private void createLink(Player player) {
        UUID playerId = player.getUniqueId();
        if (!PlayerWrapperListener.isPlayerDataReady(playerId)) {
            player.sendMessage(ChatColor.YELLOW + message(player, "support.link.profile_loading"));
            return;
        }
        long now = System.currentTimeMillis();
        switch (throttle.begin(playerId, now)) {
            case IN_FLIGHT -> {
                player.sendMessage(ChatColor.YELLOW + message(player, "support.link.busy"));
                return;
            }
            case COOLDOWN -> {
                player.sendMessage(ChatColor.YELLOW + message(player, "support.link.cooldown"));
                return;
            }
            case ALLOWED -> { }
        }
        player.sendMessage(ChatColor.YELLOW + message(player, "support.link.creating"));
        try {
            executor.submit(() -> issue(player));
        } catch (RejectedExecutionException error) {
            throttle.complete(playerId);
            player.sendMessage(ChatColor.RED + message(player, "support.link.shutdown"));
        }
    }

    private void issue(Player player) {
        UUID playerId = player.getUniqueId();
        LinkChallenge challenge = null;
        Throwable error = null;
        try {
            challenge = linkService.createChallenge(playerId,
                    AppLinkCommand.detectEdition(playerId), Purpose.COMMERCE_SESSION);
        } catch (Throwable throwable) {
            error = throwable;
        }
        LinkChallenge completed = challenge;
        Throwable completedError = error;
        throttle.complete(playerId);
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (completedError != null) {
                plugin.getLogger().warning("Support link action failed: "
                        + completedError.getClass().getSimpleName());
                player.sendMessage(ChatColor.RED + message(player, "support.link.error"));
                return;
            }
            showChallenge(player, completed);
        });
    }

    private static void showChallenge(Player player, LinkChallenge challenge) {
        player.sendMessage(ChatColor.GOLD + message(player, "support.link.code_title"));
        player.sendMessage(ChatColor.AQUA.toString() + ChatColor.BOLD + challenge.code());
        player.sendMessage(Component.text(message(player, "support.link.code_open") + " ",
                        NamedTextColor.GRAY)
                .append(Component.text("cookie-build.com/cosmetics", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(SHOP_URL)))
                .append(Component.text(" " + message(player, "support.link.code_instructions"),
                        NamedTextColor.GRAY)));
    }

    public void shutdown(Duration timeout) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String message(Player player, String key) {
        return LocaleManager.getMessage(key, player.locale());
    }
}
