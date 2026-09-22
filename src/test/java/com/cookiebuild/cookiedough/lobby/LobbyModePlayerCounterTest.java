package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.activity.ActivityAdmissionResult;
import com.cookiebuild.cookiedough.activity.PersistentActivity;
import com.cookiebuild.cookiedough.player.CookiePlayer;

class LobbyModePlayerCounterTest {
    @Test
    void countsOnlyDistinctOnlinePlayersOwnedByThePersistentActivity() {
        UUID activePlayer = UUID.randomUUID();
        UUID secondActivePlayer = UUID.randomUUID();
        UUID offlineReservation = UUID.randomUUID();
        UUID lobbyPlayer = UUID.randomUUID();
        PersistentActivity activity = activityOwning(activePlayer, secondActivePlayer, offlineReservation);

        int count = LobbyModePlayerCounter.countOwnedOnline(activity, List.of(
                player(activePlayer, true),
                player(activePlayer, true),
                player(secondActivePlayer, true),
                player(offlineReservation, false),
                player(lobbyPlayer, true)));

        assertEquals(2, count);
    }

    @Test
    void missingActivityHasNoVisiblePlayers() {
        assertEquals(0, LobbyModePlayerCounter.countOwnedOnline(null,
                List.of(player(UUID.randomUUID(), true))));
    }

    private static PersistentActivity activityOwning(UUID... playerIds) {
        Set<UUID> owned = Set.of(playerIds);
        return new PersistentActivity() {
            @Override public String name() { return "Skyblock"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ActivityAdmissionResult enter(CookiePlayer player) {
                return ActivityAdmissionResult.admitted("");
            }
            @Override public boolean leave(CookiePlayer player, String reason) { return true; }
            @Override public boolean owns(UUID playerId) { return owned.contains(playerId); }
        };
    }

    private static Player player(UUID playerId, boolean online) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] { Player.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getUniqueId" -> playerId;
                        case "isOnline" -> online;
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }
}
