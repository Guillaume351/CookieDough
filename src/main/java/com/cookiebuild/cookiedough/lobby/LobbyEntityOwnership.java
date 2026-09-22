package com.cookiebuild.cookiedough.lobby;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Durable ownership marker used to reconcile only Cookie Build lobby entities. */
final class LobbyEntityOwnership {
    private static final String OWNER_KEY = "managed_lobby_entity";
    private static final String OWNER_VALUE = "cookiebuild";

    private LobbyEntityOwnership() { }

    static void mark(JavaPlugin plugin, Entity entity, String kind) {
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, OWNER_KEY),
                PersistentDataType.STRING, OWNER_VALUE + ":" + kind);
    }

    static boolean isOwned(JavaPlugin plugin, Entity entity) {
        if (entity == null || entity instanceof Player) return false;
        boolean owned = entity.getPersistentDataContainer().has(
                new NamespacedKey(plugin, OWNER_KEY), PersistentDataType.STRING);
        boolean recognizedLegacy = entity.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "game_npc"), PersistentDataType.STRING)
                || entity.getPersistentDataContainer().has(
                        new NamespacedKey(plugin, "champion_head"), PersistentDataType.STRING)
                || entity.getPersistentDataContainer().has(
                        new NamespacedKey(plugin, "lobby_billboard"), PersistentDataType.STRING);
        return cleanupDecision(false, owned, recognizedLegacy);
    }

    static boolean cleanupDecision(boolean player, boolean owned, boolean recognizedLegacy) {
        return !player && (owned || recognizedLegacy);
    }
}
