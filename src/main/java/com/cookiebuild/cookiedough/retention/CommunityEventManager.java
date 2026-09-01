package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Reads one safely optional community event from config and handles opt-in reminders. */
public final class CommunityEventManager {
    private final CookieDough plugin;
    private final Set<UUID> reminders = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reminded = ConcurrentHashMap.newKeySet();

    public CommunityEventManager(CookieDough plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sendDueReminders, 1200L, 1200L);
    }

    public void show(Player player) {
        Event event = nextEvent();
        if (event == null) {
            player.sendMessage(ChatColor.YELLOW + "No community session is scheduled yet. Check Discord for announcements.");
            return;
        }
        Duration until = Duration.between(Instant.now(), event.time());
        String relative = formatDuration(until);
        Component message = Component.text("Next event: " + event.title() + " — " + relative,
                NamedTextColor.GOLD);
        if (!event.url().isBlank()) {
            message = message.clickEvent(ClickEvent.openUrl(event.url()));
        }
        player.sendMessage(message);
        player.sendMessage(ChatColor.YELLOW + "Use /events remind to toggle a 30-minute reminder.");
    }

    public boolean toggleReminder(Player player) {
        if (!reminders.add(player.getUniqueId())) {
            reminders.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void sendDueReminders() {
        Event event = nextEvent();
        if (event == null) {
            reminded.clear();
            return;
        }
        Duration until = Duration.between(Instant.now(), event.time());
        if (until.isNegative() || until.compareTo(Duration.ofMinutes(30)) > 0) {
            return;
        }
        for (UUID playerId : reminders) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && reminded.add(playerId)) {
                player.sendMessage(ChatColor.GOLD + "Community event starts in " + formatDuration(until)
                        + ": " + event.title());
            }
        }
    }

    private Event nextEvent() {
        if (!plugin.getConfig().getBoolean("community-event.enabled", false)) {
            return null;
        }
        String rawTime = plugin.getConfig().getString("community-event.time", "").trim();
        if (rawTime.isBlank()) {
            return null;
        }
        try {
            Instant time = Instant.parse(rawTime);
            if (time.isBefore(Instant.now())) {
                return null;
            }
            String url = plugin.getConfig().getString("community-event.url", "").trim();
            if (!url.isBlank() && !(url.startsWith("https://") || url.startsWith("http://"))) {
                url = "";
            }
            return new Event(plugin.getConfig().getString("community-event.title", "Community Session"), time, url);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Invalid community-event.time; expected an ISO-8601 UTC instant");
            return null;
        }
    }

    private String formatDuration(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long remainingMinutes = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        return hours > 0 ? hours + "h " + remainingMinutes + "m" : remainingMinutes + "m";
    }

    private record Event(String title, Instant time, String url) {
    }
}
