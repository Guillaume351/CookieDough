package com.cookiebuild.cookiedough.theme.advent;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.theme.hunt.CollectibleRepository;
import com.cookiebuild.cookiedough.theme.hunt.HuntRewards;
import com.cookiebuild.cookiedough.ui.PlatformText;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * The advent calendar behind the musician: a click opens today's gift once.
 * The row in {@code seasonal_collectibles} (primary key player, calendar,
 * day) is the only truth, so two clicks, two servers or a restart can never
 * pay twice; the coin ledger key {@code theme:<id>:dNN} is idempotent too.
 * Players who have not opened today's gift get one reminder when they join.
 */
public final class AdventService implements Listener {
    private static final long REMINDER_DELAY_TICKS = 20L * 6;

    private final JavaPlugin plugin;
    private final CollectibleRepository repository;
    private final HuntRewards rewards;
    private final Clock clock;
    /** Players with a claim in flight, so a double tap sends one request. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private AdventCalendar calendar;
    private String messages;
    private String lobbyWorld;

    public AdventService(JavaPlugin plugin, CollectibleRepository repository, HuntRewards rewards, Clock clock) {
        this.plugin = plugin;
        this.repository = repository;
        this.rewards = rewards;
        this.clock = clock;
    }

    public void activate(AdventCalendar calendar, String messagePrefix, String lobbyWorld) {
        this.calendar = calendar;
        this.messages = messagePrefix;
        this.lobbyWorld = lobbyWorld;
        plugin.getLogger().info("Advent calendar " + calendar.id() + " is "
                + calendar.phase(clock.instant()).name().toLowerCase(java.util.Locale.ROOT)
                + calendar.dayAt(clock.instant()).map(day -> " (day " + day + ")").orElse(""));
    }

    public void deactivate() {
        calendar = null;
        pending.clear();
    }

    public Optional<AdventCalendar> calendar() {
        return Optional.ofNullable(calendar);
    }

    /** A player clicked the musician (main thread). */
    public void open(Player player) {
        AdventCalendar current = calendar;
        if (current == null) return;
        String prefix = messages;
        switch (current.phase(clock.instant())) {
            case BEFORE -> {
                player.sendMessage(LocaleManager.getMessage(prefix + ".advent.soon", player.locale()));
                return;
            }
            case AFTER -> {
                player.sendMessage(LocaleManager.getMessage(prefix + ".advent.over", player.locale()));
                return;
            }
            case OPEN -> { }
        }
        int day = current.dayAt(clock.instant()).orElseThrow();
        UUID playerId = player.getUniqueId();
        if (!pending.add(playerId)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Result result;
            try {
                if (repository.record(playerId, current.id(), AdventCalendar.collectibleId(day))) {
                    int coins = current.coinsFor(day);
                    if (coins > 0) rewards.coins(playerId, current.rewardKey(day), coins);
                    result = Result.OPENED;
                } else {
                    result = Result.ALREADY;
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Advent gift " + current.id() + " day " + day + " for "
                        + playerId + " could not be saved", error);
                result = Result.UNAVAILABLE;
            }
            Result outcome = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(playerId);
                if (player.isOnline()) tell(player, prefix, current, day, outcome);
            });
        });
    }

    private enum Result { OPENED, ALREADY, UNAVAILABLE }

    private void tell(Player player, String prefix, AdventCalendar current, int day, Result result) {
        switch (result) {
            case OPENED -> {
                boolean last = current.isLastDay(day);
                player.sendMessage(LocaleManager.getMessage(prefix + (last ? ".advent.opened.last" : ".advent.opened"),
                        player.locale(), day, current.coinsFor(day)));
                Location at = player.getLocation().add(0, 1.2, 0);
                player.spawnParticle(Particle.ITEM, at, 12, 0.3, 0.3, 0.3, 0.08, new ItemStack(Material.RED_WOOL));
                player.spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.4, 0.4, 0.4, 0.0);
                player.playSound(at, "block.note_block.chime", SoundCategory.PLAYERS, 0.8f, 1.2f);
                player.playSound(at, "entity.player.levelup", SoundCategory.PLAYERS, 0.35f, 1.5f);
            }
            case ALREADY -> player.sendMessage(LocaleManager.getMessage(
                    prefix + (current.isLastDay(day) ? ".advent.already.last" : ".advent.already"), player.locale()));
            case UNAVAILABLE -> player.sendMessage(LocaleManager.getMessage(prefix + ".advent.unavailable",
                    player.locale()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        AdventCalendar current = calendar;
        if (current == null) return;
        Optional<Integer> today = current.dayAt(clock.instant());
        if (today.isEmpty()) return;
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String id = AdventCalendar.collectibleId(today.get());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean opened;
            try {
                opened = repository.found(playerId, current.id()).contains(id);
            } catch (RuntimeException error) {
                return; // no reminder without the database: the click will say so
            }
            if (opened) return;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (calendar != current || !player.isOnline()) return;
                if (lobbyWorld != null && !player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) return;
                player.sendMessage(PlatformText.message(player, messages + ".advent.reminder"));
            }, REMINDER_DELAY_TICKS);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
