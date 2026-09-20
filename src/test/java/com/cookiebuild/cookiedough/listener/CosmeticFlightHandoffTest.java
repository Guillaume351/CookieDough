package com.cookiebuild.cookiedough.listener;

import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.cosmetics.CosmeticEffects;
import com.cookiebuild.cookiedough.cosmetics.CosmeticService;

class CosmeticFlightHandoffTest {
    @Test
    void cosmeticCleanupCannotRevokeTransportOwnedGraceAndTransportStillRevokesIt() throws Exception {
        CookieDough plugin = mock(CookieDough.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        var effects = new CosmeticEffects(plugin, mock(CosmeticService.class));
        when(plugin.getCosmeticEffects()).thenReturn(effects);
        Method enable = CosmeticEffects.class.getDeclaredMethod("enableManagedFlight", Player.class);
        enable.setAccessible(true);
        enable.invoke(effects, player);
        clearInvocations(player);

        try (var instance = mockStatic(CookieDough.class)) {
            instance.when(CookieDough::getInstance).thenReturn(plugin);
            var guard = new PlayerTransitionFlightGuard(plugin);
            guard.protectLoading(player, () -> { });
            verify(player).setAllowFlight(true);
            // Represents the next cosmetic tick or cross-world teleport listener.
            effects.disableLobbyFlightBeforeArena(player);
            verify(player, never()).setAllowFlight(false);
            guard.abandon(player);
            verify(player).setAllowFlight(false);
        }
    }
}
