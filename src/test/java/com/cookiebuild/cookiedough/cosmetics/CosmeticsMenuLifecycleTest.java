package com.cookiebuild.cookiedough.cosmetics;

import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import com.cookiebuild.cookiedough.CookieDough;

class CosmeticsMenuLifecycleTest {
    @Test
    void nativeSelectionDoesNotSendJavaInventoryCloseButJavaSelectionDoes() throws Exception {
        CookieDough plugin = mock(CookieDough.class);
        when(plugin.namespace()).thenReturn("cookiedough");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var menu = new CosmeticsMenu(plugin, mock(CosmeticService.class), mock(CosmeticEffects.class));
            Method dispatch = CosmeticsMenu.class.getDeclaredMethod("dispatch", Player.class,
                    CosmeticMenuAction.class, boolean.class);
            dispatch.setAccessible(true);
            var action = CosmeticMenuAction.parse("deselect:BADGE").orElseThrow();
            dispatch.invoke(menu, player, action, true);
            verify(player, never()).closeInventory();
            dispatch.invoke(menu, player, action, false);
            verify(player).closeInventory();
            verify(scheduler, times(2)).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        }
    }
}
