package com.cookiebuild.cookiedough.listener;

public final class WorldPolicy {
    private static final java.util.Set<String> PERSISTENT_WORLDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private WorldPolicy() {
    }

    static boolean usesFrozenPhysics(String worldName) {
        return "lobby".equalsIgnoreCase(worldName);
    }

    static boolean blocksHostileSpawn(String worldName, boolean hostile, boolean customSpawn) {
        return "lobby".equalsIgnoreCase(worldName) && hostile && !customSpawn;
    }

    public static void registerPersistentWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) throw new IllegalArgumentException("worldName is required");
        PERSISTENT_WORLDS.add(worldName.toLowerCase(java.util.Locale.ROOT));
    }

    public static void unregisterPersistentWorld(String worldName) {
        if (worldName != null) PERSISTENT_WORLDS.remove(worldName.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean isPersistent(String worldName) {
        return worldName != null && PERSISTENT_WORLDS.contains(worldName.toLowerCase(java.util.Locale.ROOT));
    }

    static boolean shouldAutoSave(String worldName) {
        return "lobby".equalsIgnoreCase(worldName) || isPersistent(worldName);
    }
}
