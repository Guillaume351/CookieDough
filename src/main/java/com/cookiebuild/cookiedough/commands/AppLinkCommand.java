package com.cookiebuild.cookiedough.commands;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.service.MobileLinkService;
import com.cookiebuild.cookiedough.service.MobileLinkService.LinkChallenge;

/** Handles the secure in-game side of mobile account linking. */
public final class AppLinkCommand implements CommandExecutor {
    private static final long LINK_COOLDOWN_MS = 30_000;
    private final CookieDough plugin;
    private final MobileLinkService linkService;
    private final ScheduledExecutorService executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChallengeAt = new ConcurrentHashMap<>();

    public AppLinkCommand(CookieDough plugin, MobileLinkService linkService) {
        this.plugin = plugin;
        this.linkService = linkService;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CookieDough-MobileLink");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::pruneChallengesSafely, 1, 6, TimeUnit.HOURS);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String action = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "link" -> createLink(player);
            case "status" -> runAsync(player, () -> linkService.hasActiveLink(player.getUniqueId()), linked ->
                    player.sendMessage((linked ? ChatColor.GREEN : ChatColor.YELLOW)
                            + (linked ? "Your account is linked to the Cookie Build app."
                                    : "Your account is not linked. Use /app link to begin.")));
            case "revoke", "unlink" -> runAsync(player, () -> linkService.revoke(player.getUniqueId()), revoked ->
                    player.sendMessage((revoked ? ChatColor.GREEN : ChatColor.YELLOW)
                            + (revoked ? "Your app link was revoked on every device."
                                    : "No active app link was found.")));
            default -> {
                player.sendMessage(ChatColor.GOLD + "Cookie Build app");
                player.sendMessage(ChatColor.YELLOW + "/app link" + ChatColor.GRAY + " - create a one-time code");
                player.sendMessage(ChatColor.YELLOW + "/app status" + ChatColor.GRAY + " - check your link");
                player.sendMessage(ChatColor.YELLOW + "/app revoke" + ChatColor.GRAY + " - unlink every device");
            }
        }
        return true;
    }

    private void createLink(Player player) {
        if (!PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW
                    + "Your player profile is still loading. Please try /app link again in a moment.");
            return;
        }
        if (inFlight.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your previous app request is still being processed.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastChallengeAt.getOrDefault(player.getUniqueId(), 0L) < LINK_COOLDOWN_MS) {
            player.sendMessage(ChatColor.YELLOW + "Please wait before creating another app code.");
            return;
        }
        lastChallengeAt.put(player.getUniqueId(), now);
        String edition = detectEdition(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "Creating a secure one-time app code…");
        runAsync(player,
                () -> linkService.createChallenge(player.getUniqueId(), edition),
                challenge -> showChallenge(player, challenge));
    }

    private void showChallenge(Player player, LinkChallenge challenge) {
        player.sendMessage(ChatColor.GOLD + "Your Cookie Build app code is:");
        player.sendMessage(ChatColor.AQUA.toString() + ChatColor.BOLD + challenge.code());
        player.sendMessage(ChatColor.GRAY + "Enter it in the app within 10 minutes. Never share this code.");
    }

    private <T> void runAsync(Player player, Supplier<T> work, java.util.function.Consumer<T> success) {
        UUID playerId = player.getUniqueId();
        if (!inFlight.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "Your previous app request is still being processed.");
            return;
        }
        try {
            executor.submit(() -> {
                T result = null;
                Throwable error = null;
                try {
                    result = work.get();
                } catch (Throwable throwable) {
                    error = throwable;
                }
                T completedResult = result;
                Throwable completedError = error;
                inFlight.remove(playerId);
                if (!plugin.isEnabled()) {
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (completedError != null) {
                        plugin.getLogger().warning("Mobile link action failed for " + player.getName()
                                + ": " + completedError.getMessage());
                        player.sendMessage(ChatColor.RED
                                + "The app link service is temporarily unavailable. Please try again later.");
                        return;
                    }
                    success.accept(completedResult);
                });
            });
        } catch (RejectedExecutionException exception) {
            inFlight.remove(playerId);
            player.sendMessage(ChatColor.RED + "The app link service is shutting down. Please try again later.");
        }
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

    private void pruneChallengesSafely() {
        try {
            linkService.pruneExpiredChallenges();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not prune mobile link challenges: " + exception.getMessage());
        }
    }

    /** Uses Floodgate when present without making it a hard plugin dependency. */
    private static String detectEdition(UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(api, playerId)) ? "bedrock" : "java";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "java";
        }
    }
}
