package com.cookiebuild.cookiedough.retention;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Five-round reaction drill that gives solo lobby visitors an immediate activity. */
public final class PracticeManager implements Listener {
    private static final int ROUNDS = 5;
    private static final int PRACTICE_SLOT = 7;
    private static final class Session {
        int completed;
        long totalMillis;
        long targetAt;
        boolean armed;
        int taskId = -1;
        ItemStack previousItem;
    }

    private final JavaPlugin plugin;
    private final NamespacedKey practiceKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PracticeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.practiceKey = new NamespacedKey(plugin, "reaction_practice");
    }

    public boolean toggle(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            stop(player, true);
            return false;
        }
        var cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || (cookiePlayer.getState() != PlayerState.LOBBY
                && cookiePlayer.getState() != PlayerState.QUEUED)) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "practice.unavailable", player.locale()));
            return false;
        }
        Session session = new Session();
        ItemStack existing = player.getInventory().getItem(PRACTICE_SLOT);
        session.previousItem = existing == null ? null : existing.clone();
        sessions.put(player.getUniqueId(), session);
        ItemStack target = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = target.getItemMeta();
        meta.displayName(Component.text(LocaleManager.getMessage(
                "practice.item.name", player.locale()), NamedTextColor.AQUA));
        meta.lore(java.util.List.of(Component.text(LocaleManager.getMessage(
                "practice.item.lore", player.locale()), NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(practiceKey, PersistentDataType.BYTE, (byte) 1);
        target.setItemMeta(meta);
        player.getInventory().setItem(PRACTICE_SLOT, target);
        player.sendMessage(ChatColor.AQUA + LocaleManager.getMessage("practice.started", player.locale()));
        FunnelTelemetry.record(player, FunnelTelemetry.Event.TUTORIAL_STARTED, "tutorial=reaction");
        scheduleRound(player, session);
        return true;
    }

    public boolean isActive(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onPracticeClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getItem().hasItemMeta()
                || !event.getItem().getItemMeta().getPersistentDataContainer().has(practiceKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        if (!session.armed) {
            event.getPlayer().sendActionBar(Component.text(LocaleManager.getMessage(
                    "practice.too_soon", event.getPlayer().locale()), NamedTextColor.RED));
            return;
        }
        session.armed = false;
        long reaction = Math.max(0, System.currentTimeMillis() - session.targetAt);
        session.totalMillis += reaction;
        session.completed++;
        event.getPlayer().sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                "practice.round", event.getPlayer().locale(), session.completed, reaction));
        if (session.completed >= ROUNDS) {
            long average = session.totalMillis / ROUNDS;
            event.getPlayer().sendMessage(ChatColor.GOLD + LocaleManager.getMessage(
                    "practice.complete", event.getPlayer().locale(), average));
            FunnelTelemetry.record(event.getPlayer(), FunnelTelemetry.Event.TUTORIAL_COMPLETED,
                    "tutorial=reaction average_ms=" + average);
            stop(event.getPlayer(), false);
            if (plugin instanceof com.cookiebuild.cookiedough.CookieDough cookieDough) {
                cookieDough.getGoalTracker().grantAchievement(event.getPlayer(), "reaction_rookie",
                        "Reaction Rookie", 10);
            }
            return;
        }
        scheduleRound(event.getPlayer(), session);
    }

    private void scheduleRound(Player player, Session session) {
        long delay = ThreadLocalRandom.current().nextLong(40, 101);
        player.sendActionBar(Component.text(LocaleManager.getMessage(
                "practice.wait", player.locale()), NamedTextColor.YELLOW));
        session.taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                return;
            }
            session.targetAt = System.currentTimeMillis();
            session.armed = true;
            player.sendActionBar(Component.text(LocaleManager.getMessage(
                    "practice.go", player.locale()), NamedTextColor.GREEN));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.5f);
        }, delay).getTaskId();
    }

    public void stop(Player player, boolean notify) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && session.taskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(session.taskId);
        }
        ItemStack item = player.getInventory().getItem(PRACTICE_SLOT);
        if (item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(practiceKey, PersistentDataType.BYTE)) {
            player.getInventory().setItem(PRACTICE_SLOT, session == null ? null : session.previousItem);
        }
        if (notify) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                    "practice.stopped", player.locale()));
        }
    }
}
