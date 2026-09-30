package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.cosmetics.CosmeticCatalog;
import com.cookiebuild.cookiedough.cosmetics.CosmeticService;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/**
 * "Reasons to come back": the in-game 7-day login calendar, the returning
 * player welcome (absent 14+ days) and delivery of website reward grants
 * (app link, app daily chest). All DB work runs on one worker thread; every
 * player message is plain localized text (works on Bedrock).
 */
public final class RetentionRewardService implements Listener {
    private static final long POLL_TICKS = 200L; // ~10 s
    private static final int GRANT_BATCH = 50;
    private static final Duration TABLE_MISSING_BACKOFF = Duration.ofMinutes(10);
    public static final String STREAK_COSMETIC_SOURCE = "login-calendar:streak-star";

    private final CookieDough plugin;
    private final PostgresLoginRewardRepository calendar = new PostgresLoginRewardRepository();
    private final PostgresRewardGrantRepository grants = new PostgresRewardGrantRepository();
    private final ExecutorService worker;
    private final Map<UUID, Instant> sessionStarts = new ConcurrentHashMap<>();
    private final Map<UUID, LocalDate> claimedDays = new ConcurrentHashMap<>();
    private final Set<UUID> claimsInFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> warnings = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean grantPollRunning = new AtomicBoolean();
    private volatile Instant calendarDisabledUntil = Instant.EPOCH;
    private volatile Instant grantsDisabledUntil = Instant.EPOCH;

    public RetentionRewardService(CookieDough plugin) {
        this.plugin = plugin;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CookieDough-RetentionRewards");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        Bukkit.getOnlinePlayers().forEach(player -> sessionStarts.putIfAbsent(player.getUniqueId(), Instant.now()));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, POLL_TICKS);
    }

    public void shutdown(Duration timeout) {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) worker.shutdownNow();
        } catch (InterruptedException interrupted) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessionStarts.put(playerId, Instant.now());
        claimedDays.remove(playerId);
        // Player data is initialized asynchronously; retry for up to ~30 s.
        for (long delay : new long[] { 60L, 200L, 600L }) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && PlayerWrapperListener.isPlayerDataReady(playerId)) {
                    claimCalendar(player);
                    pollGrants(List.of(playerId));
                }
            }, delay);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessionStarts.remove(playerId);
        claimedDays.remove(playerId);
    }

    /** Plain-text 7-day calendar for /calendrier; safe to call from the hub. */
    public void showCalendar(Player player) {
        UUID playerId = player.getUniqueId();
        submit(() -> {
            Optional<LoginCalendarPolicy.State> state = calendar.load(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) LoginCalendarView.lines(player.locale(), state.orElse(null),
                        ParisCalendar.today()).forEach(player::sendMessage);
            });
        }, error -> {
            warnOnce("calendar-read", "Could not read the login calendar: " + error);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.sendMessage(ChatColor.RED + message(player, "calendar.unavailable"));
            });
        });
    }

    private void tick() {
        LocalDate today = ParisCalendar.today();
        List<UUID> ready = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!PlayerWrapperListener.isPlayerDataReady(playerId)) continue;
            ready.add(playerId);
            // Also covers players who stay online across midnight (Paris).
            if (!today.equals(claimedDays.get(playerId))) claimCalendar(player);
        }
        pollGrants(ready);
    }

    private void claimCalendar(Player player) {
        UUID playerId = player.getUniqueId();
        LocalDate today = ParisCalendar.today();
        if (today.equals(claimedDays.get(playerId)) || Instant.now().isBefore(calendarDisabledUntil)
                || !claimsInFlight.add(playerId)) return;
        Instant sessionStart = sessionStarts.getOrDefault(playerId, Instant.now());
        CosmeticService cosmetics = plugin.getCosmeticService();
        submit(() -> {
            try {
                boolean ownsStreakCosmetic = cosmetics != null
                        && cosmetics.inventory(playerId).entitled(CosmeticCatalog.STREAK_STAR_TRAIL);
                Optional<PostgresLoginRewardRepository.Outcome> outcome = calendar.claim(playerId, today,
                        sessionStart, cycleDay -> ownsStreakCosmetic ? LoginCalendarPolicy.DAY_SEVEN_OWNED_BONUS : 0);
                boolean unlockedCosmetic = false;
                if (outcome.isPresent() && outcome.get().claim().daySeven() && !ownsStreakCosmetic && cosmetics != null) {
                    cosmetics.grantReward(playerId, CosmeticCatalog.STREAK_STAR_TRAIL, STREAK_COSMETIC_SOURCE);
                    unlockedCosmetic = true;
                }
                claimedDays.put(playerId, today);
                boolean cosmeticUnlocked = unlockedCosmetic;
                outcome.ifPresent(result -> Bukkit.getScheduler().runTask(plugin,
                        () -> announceClaim(playerId, result, cosmeticUnlocked)));
            } finally {
                claimsInFlight.remove(playerId);
            }
        }, error -> {
            claimsInFlight.remove(playerId);
            if (error.contains("player_login_rewards")) {
                calendarDisabledUntil = Instant.now().plus(TABLE_MISSING_BACKOFF);
            }
            warnOnce("calendar:" + error, "Could not claim the login calendar: " + error);
        });
    }

    private void announceClaim(UUID playerId, PostgresLoginRewardRepository.Outcome outcome, boolean cosmeticUnlocked) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        LobbyScoreboard.invalidatePlayerCache(playerId);
        LoginCalendarPolicy.Claim claim = outcome.claim();
        Locale locale = player.locale();
        if (claim.welcomeBack()) {
            player.showTitle(Title.title(Component.text(message(locale, "welcome_back.title", player.getName()),
                    NamedTextColor.GOLD), Component.text(message(locale, "goals.reward_coins",
                    outcome.welcomeBackCoins()), NamedTextColor.YELLOW)));
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD
                    + message(locale, "welcome_back.title", player.getName()));
            player.sendMessage(ChatColor.YELLOW + message(locale, "welcome_back.gift", claim.absenceDays(),
                    outcome.welcomeBackCoins()));
            player.sendMessage(ChatColor.AQUA + message(locale, "welcome_back.news"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        }
        if (claim.streakReset() && !claim.welcomeBack()) {
            player.sendMessage(ChatColor.GRAY + message(locale, "calendar.streak_reset"));
        }
        if (claim.daySeven()) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + message(locale,
                    cosmeticUnlocked ? "calendar.day7.unlocked" : "calendar.day7.bonus", outcome.calendarCoins(),
                    message(locale, "cosmetics.item." + CosmeticCatalog.STREAK_STAR_TRAIL + ".name")));
        } else {
            player.sendMessage(ChatColor.GREEN + message(locale, "calendar.claimed", claim.cycleDay(),
                    outcome.calendarCoins(), claim.streak()));
        }
        int nextDay = LoginCalendarPolicy.cycleDay(claim.streak() + 1);
        player.sendMessage(ChatColor.GRAY + message(locale, "calendar.tomorrow",
                LoginCalendarPolicy.coinsForCycleDay(nextDay), nextDay));
        if (!claim.welcomeBack()) player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
    }

    private void pollGrants(List<UUID> players) {
        if (players.isEmpty() || Instant.now().isBefore(grantsDisabledUntil)
                || !grantPollRunning.compareAndSet(false, true)) return;
        List<UUID> snapshot = List.copyOf(players);
        submit(() -> {
            try {
                List<PostgresRewardGrantRepository.Grant> delivered = grants.deliverPending(snapshot, GRANT_BATCH,
                        warning -> warnOnce(warning, warning));
                if (!delivered.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> delivered.forEach(this::announceGrant));
                }
            } finally {
                grantPollRunning.set(false);
            }
        }, error -> {
            grantPollRunning.set(false);
            if (error.contains("player_reward_grants")) {
                grantsDisabledUntil = Instant.now().plus(TABLE_MISSING_BACKOFF);
            }
            warnOnce("grants:" + error, "Could not poll reward grants: " + error);
        });
    }

    private void announceGrant(PostgresRewardGrantRepository.Grant grant) {
        CosmeticService cosmetics = plugin.getCosmeticService();
        if (cosmetics != null && grant.cosmeticId() != null) cosmetics.notifyExternalChange(grant.playerId());
        LobbyScoreboard.invalidatePlayerCache(grant.playerId());
        Player player = Bukkit.getPlayer(grant.playerId());
        if (player == null) return;
        Locale locale = player.locale();
        String cosmeticName = grant.cosmeticId() == null ? null
                : CosmeticCatalog.find(grant.cosmeticId()).map(item -> message(locale, item.nameKey())).orElse(null);
        String parts = RewardGrantText.parts(grant.coins(), grant.xp(), cosmeticName,
                coins -> message(locale, "goals.reward_coins", coins));
        player.sendMessage(ChatColor.LIGHT_PURPLE + message(locale, RewardGrantText.messageKey(grant.source()), parts));
        if (cosmeticName != null) {
            player.sendMessage(ChatColor.GRAY + message(locale, "rewards.cosmetic_hint"));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
    }

    private void submit(Runnable work, java.util.function.Consumer<String> onError) {
        try {
            worker.submit(() -> {
                try {
                    work.run();
                } catch (RuntimeException error) {
                    onError.accept(PostgresRewardGrantRepository.rootMessage(error));
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Shutting down.
        }
    }

    private void warnOnce(String key, String warning) {
        if (warnings.size() > 200) warnings.clear();
        if (warnings.add(key)) plugin.getLogger().warning(warning);
    }

    private static String message(Player player, String key, Object... args) {
        return LocaleManager.getMessage(key, player.locale(), args);
    }

    private static String message(Locale locale, String key, Object... args) {
        return LocaleManager.getMessage(key, locale, args);
    }
}
