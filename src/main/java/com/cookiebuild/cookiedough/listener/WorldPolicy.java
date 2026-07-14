package com.cookiebuild.cookiedough.listener;

final class WorldPolicy {
    private WorldPolicy() {
    }

    static boolean usesFrozenPhysics(String worldName) {
        return "lobby".equalsIgnoreCase(worldName);
    }
}
