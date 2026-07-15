package com.cookiebuild.cookiedough.listener;

final class WorldPolicy {
    private WorldPolicy() {
    }

    static boolean usesFrozenPhysics(String worldName) {
        return "lobby".equalsIgnoreCase(worldName);
    }

    static boolean blocksHostileSpawn(String worldName, boolean hostile, boolean customSpawn) {
        return "lobby".equalsIgnoreCase(worldName) && hostile && !customSpawn;
    }
}
