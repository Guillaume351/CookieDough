package com.cookiebuild.cookiedough.activity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.player.CookiePlayer;

/** Thread-safe bridge between CookieDough lifecycle code and persistent plugins. */
public final class ActivityRegistry {
    private static final Map<String, PersistentActivity> ACTIVITIES = new ConcurrentHashMap<>();

    private ActivityRegistry() {
    }

    public static void register(PersistentActivity activity) {
        if (activity == null || activity.name() == null || activity.name().isBlank()) {
            throw new IllegalArgumentException("A named persistent activity is required");
        }
        String key = key(activity.name());
        PersistentActivity previous = ACTIVITIES.putIfAbsent(key, activity);
        if (previous != null && previous != activity) {
            throw new IllegalStateException("Persistent activity already registered: " + activity.name());
        }
    }

    public static void unregister(PersistentActivity activity) {
        if (activity != null) ACTIVITIES.remove(key(activity.name()), activity);
    }

    public static PersistentActivity find(String name) {
        return name == null ? null : ACTIVITIES.get(key(name));
    }

    public static PersistentActivity owner(UUID playerId) {
        if (playerId == null) return null;
        return ACTIVITIES.values().stream().filter(activity -> activity.owns(playerId)).findFirst().orElse(null);
    }

    public static PersistentActivity forWorld(String worldName) {
        if (worldName == null) return null;
        return ACTIVITIES.values().stream().filter(activity -> activity.ownsWorld(worldName)).findFirst().orElse(null);
    }

    /** Returns the durable activity marker stored in the player's Paper data. */
    public static String resumeName(Player player) {
        if (player == null || CookieDough.getInstance() == null) return null;
        return player.getPersistentDataContainer().get(resumeKey(), PersistentDataType.STRING);
    }

    public static PersistentActivity resumeOwner(Player player) {
        return find(resumeName(player));
    }

    public static boolean isResumeMarked(Player player, String activityName) {
        String marked = resumeName(player);
        return marked != null && activityName != null && marked.equalsIgnoreCase(activityName);
    }

    public static void markResume(Player player, String activityName) {
        if (player != null && activityName != null && !activityName.isBlank()) {
            player.getPersistentDataContainer().set(resumeKey(), PersistentDataType.STRING, activityName);
        }
    }

    public static void clearResume(Player player) {
        if (player != null && CookieDough.getInstance() != null) {
            player.getPersistentDataContainer().remove(resumeKey());
        }
    }

    public static ActivityAdmissionResult enter(String name, CookiePlayer player) {
        PersistentActivity activity = find(name);
        if (activity == null || !activity.isAvailable()) {
            return ActivityAdmissionResult.rejected(name + " is temporarily unavailable.");
        }
        PersistentActivity current = owner(player.getPlayer().getUniqueId());
        if (current != null && current != activity) {
            return ActivityAdmissionResult.rejected("Leave " + current.name() + " before joining " + name + ".");
        }
        return activity.enter(player);
    }

    public static boolean leave(CookiePlayer player, String reason) {
        if (player == null || player.getPlayer() == null) return true;
        PersistentActivity activity = owner(player.getPlayer().getUniqueId());
        String safeReason = reason == null ? "unknown" : reason;
        boolean left = activity == null || activity.leave(player, safeReason);
        if (left && "returned_lobby".equals(safeReason)) clearResume(player.getPlayer());
        return left;
    }

    public static boolean canLeave(CookiePlayer player, String reason) {
        if (player == null || player.getPlayer() == null) return true;
        PersistentActivity activity = owner(player.getPlayer().getUniqueId());
        return activity == null || activity.canLeave(player, reason == null ? "unknown" : reason);
    }

    static void clearForTests() {
        ACTIVITIES.clear();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static NamespacedKey resumeKey() {
        return new NamespacedKey(CookieDough.getInstance(), "persistent_activity");
    }
}
