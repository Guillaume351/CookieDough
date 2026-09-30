package com.cookiebuild.cookiedough.retention;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * Community events: a recurring weekly "Soirée Cookie" (coins multiplier and
 * in-game announcements at T-30 min, T-5 min and start) plus one optional
 * one-off event with opt-in reminders. Every message is plain text so it
 * reads the same on Bedrock, where chat components cannot be clicked.
 */
public final class CommunityEventManager {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CookieDough plugin;
    private final Set<UUID> reminders = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reminded = ConcurrentHashMap.newKeySet();
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private volatile WeeklyEventSchedule schedule;

    public CommunityEventManager(CookieDough plugin) {
        this.plugin = plugin;
        this.schedule = loadSchedule();
        MinigameProgressionService.setMatchCoinBonus(this::currentCoinMultiplier, this::notifyBonus);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200L, 400L);
    }

    /** Current multiplier for match coins; 1 outside a Soirée Cookie. Thread-safe. */
    public int currentCoinMultiplier() {
        WeeklyEventSchedule current = schedule;
        return current == null ? 1 : current.multiplierAt(Instant.now());
    }

    /** The running or next recurring occurrence, for other surfaces (hub, scoreboard). */
    public Optional<WeeklyEventSchedule.Occurrence> currentOrNextRecurring() {
        WeeklyEventSchedule current = schedule;
        if (current == null) return Optional.empty();
        Instant now = Instant.now();
        return Optional.of(current.current(now).orElseGet(() -> current.next(now)));
    }

    public void show(Player player) {
        Locale locale = player.locale();
        boolean shown = false;
        WeeklyEventSchedule recurring = schedule;
        if (recurring != null) {
            Instant now = Instant.now();
            String name = message(locale, "events.soiree.name");
            Optional<WeeklyEventSchedule.Occurrence> live = recurring.current(now);
            if (live.isPresent()) {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + message(locale, "events.live", name,
                        localTime(live.get().end(), recurring), recurring.coinMultiplier()));
            } else {
                WeeklyEventSchedule.Occurrence next = recurring.next(now);
                ZonedDateTime start = next.start().atZone(recurring.zone());
                player.sendMessage(ChatColor.GOLD + message(locale, "events.next", name,
                        DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(start),
                        TIME.format(start), formatDuration(locale, Duration.between(now, next.start())),
                        recurring.coinMultiplier()));
            }
            player.sendMessage(ChatColor.YELLOW + message(locale, "events.schedule", joinDays(locale, recurring),
                    TIME.format(recurring.start()), TIME.format(recurring.start().plus(recurring.duration()))));
            shown = true;
        }
        OneOffEvent oneOff = oneOffEvent();
        if (oneOff != null) {
            player.sendMessage(ChatColor.GOLD + message(locale, "events.one_off.next", oneOff.title(),
                    formatDuration(locale, Duration.between(Instant.now(), oneOff.time()))));
            if (!oneOff.url().isBlank()) {
                player.sendMessage(ChatColor.AQUA + message(locale, "events.one_off.url", oneOff.url()));
            }
            player.sendMessage(ChatColor.GRAY + message(locale, "events.remind.hint"));
            shown = true;
        }
        if (!shown) {
            player.sendMessage(ChatColor.YELLOW + message(locale, "events.none"));
        }
    }

    public boolean toggleReminder(Player player) {
        if (!reminders.add(player.getUniqueId())) {
            reminders.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void tick() {
        announceRecurring();
        sendDueReminders();
    }

    private void announceRecurring() {
        WeeklyEventSchedule recurring = schedule;
        if (recurring == null) return;
        Instant now = Instant.now();
        WeeklyEventSchedule.Occurrence occurrence = recurring.current(now).orElseGet(() -> recurring.next(now));
        Optional<WeeklyEventSchedule.Phase> phase = recurring.dueAnnouncement(occurrence, now);
        if (phase.isEmpty() || !announced.add(WeeklyEventSchedule.announcementKey(occurrence, phase.get()))) return;
        if (announced.size() > 64) announced.clear();
        long minutes = Math.max(1, (Duration.between(now, occurrence.start()).getSeconds() + 59) / 60);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Locale locale = player.locale();
            String name = message(locale, "events.soiree.name");
            String text = phase.get() == WeeklyEventSchedule.Phase.STARTED
                    ? message(locale, "events.announce.start", name, recurring.coinMultiplier(),
                            localTime(occurrence.end(), recurring))
                    : message(locale, "events.announce.soon", name, minutes, recurring.coinMultiplier(),
                            localTime(occurrence.start(), recurring));
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "» " + ChatColor.RESET + ChatColor.GOLD + text);
            if (phase.get() != WeeklyEventSchedule.Phase.THIRTY_MINUTES) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
            }
        }
        plugin.getLogger().info("Soirée Cookie announcement sent: " + phase.get());
    }

    private void sendDueReminders() {
        OneOffEvent event = oneOffEvent();
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
                player.sendMessage(ChatColor.GOLD + message(player.locale(), "events.one_off.reminder",
                        event.title(), formatDuration(player.locale(), until)));
            }
        }
    }

    private void notifyBonus(UUID playerId, int bonusCoins) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) return;
            player.sendMessage(ChatColor.GOLD + message(player.locale(), "events.bonus",
                    message(player.locale(), "events.soiree.name"), bonusCoins));
        });
    }

    private WeeklyEventSchedule loadSchedule() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("community-event.recurring");
        if (section == null || !section.getBoolean("enabled", false)) return null;
        try {
            return WeeklyEventSchedule.parse(section.getStringList("days"), section.getString("start", "21:00"),
                    section.getInt("duration-minutes", 60), section.getString("timezone", "Europe/Paris"),
                    section.getInt("coin-multiplier", 2));
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Invalid community-event.recurring configuration; Soirée Cookie disabled: "
                    + error.getMessage());
            return null;
        }
    }

    private OneOffEvent oneOffEvent() {
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
            return new OneOffEvent(plugin.getConfig().getString("community-event.title", "Cookie Build"), time, url);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Invalid community-event.time; expected an ISO-8601 UTC instant");
            return null;
        }
    }

    static String formatDuration(Locale locale, Duration duration) {
        long minutes = Math.max(0, (duration.getSeconds() + 59) / 60);
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long remainingMinutes = minutes % 60;
        if (days > 0) {
            return message(locale, "events.duration.days", days, hours);
        }
        return hours > 0 ? message(locale, "events.duration.hours", hours, remainingMinutes)
                : message(locale, "events.duration.minutes", remainingMinutes);
    }

    private static String joinDays(Locale locale, WeeklyEventSchedule recurring) {
        List<String> names = recurring.days().stream().sorted()
                .map(day -> dayName(day, locale)).toList();
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1))
                + message(locale, "events.and") + names.get(names.size() - 1);
    }

    private static String dayName(DayOfWeek day, Locale locale) {
        return day.getDisplayName(TextStyle.FULL, locale);
    }

    private static String localTime(Instant instant, WeeklyEventSchedule recurring) {
        return TIME.format(instant.atZone(recurring.zone()));
    }

    private static String message(Locale locale, String key, Object... args) {
        return LocaleManager.getMessage(key, locale, args);
    }

    private record OneOffEvent(String title, Instant time, String url) {
    }
}
