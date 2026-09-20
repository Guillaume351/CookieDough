package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import com.cookiebuild.cookiedough.CookieDough;

class CosmeticRefreshLifecycleTest {
    @Test
    void failedRefreshRemovesPreviouslyGrantedBadgeImmediately() {
        UUID id = UUID.randomUUID();
        var granted = badgeInventory(id);
        var service = mock(CosmeticService.class);
        when(service.inventory(id)).thenReturn(granted).thenThrow(new IllegalStateException("database unavailable"));
        exercise(service, (effects, player, async, sync) -> {
            effects.refresh(player);
            async.removeFirst().run(); sync.removeFirst().run();
            assertTrue(effects.hasActiveSelection(id, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE));
            effects.refresh(player);
            async.removeFirst().run(); sync.removeFirst().run();
            assertFalse(effects.hasActiveSelection(id, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE));
        }, id);
    }

    @Test
    void delayedOldConnectionCannotOverwriteNewConnectionsEntitlements() {
        UUID id = UUID.randomUUID();
        var service = mock(CosmeticService.class);
        var empty = new CosmeticService(new InMemoryCosmeticRepository()).inventory(id);
        when(service.inventory(id)).thenReturn(empty, badgeInventory(id));
        exercise(service, (effects, player, async, sync) -> {
            effects.refresh(player); async.removeFirst().run();
            Runnable oldCompletion = sync.removeFirst();
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(player);
            effects.onQuit(quit);
            effects.refresh(player); async.removeFirst().run(); sync.removeFirst().run();
            oldCompletion.run();
            assertTrue(effects.hasActiveSelection(id, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE));
        }, id);
    }

    private static CosmeticService.Inventory badgeInventory(UUID id) {
        var service = new CosmeticService(new InMemoryCosmeticRepository());
        service.grantPermanent(id, CosmeticCatalog.SUPPORTER_BADGE, "purchase:test");
        service.select(id, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE);
        return service.inventory(id);
    }

    private static void exercise(CosmeticService service, Scenario scenario, UUID id) {
        CookieDough plugin = mock(CookieDough.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Player");
        when(player.playerListName()).thenReturn(net.kyori.adventure.text.Component.text("Player"));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> async = new ArrayList<>();
        List<Runnable> sync = new ArrayList<>();
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class)))
                .thenAnswer(call -> { async.add(call.getArgument(1)); return null; });
        when(scheduler.runTask(eq(plugin), any(Runnable.class)))
                .thenAnswer(call -> { sync.add(call.getArgument(1)); return null; });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var effects = new CosmeticEffects(plugin, service);
            effects.start();
            scenario.run(effects, player, async, sync);
            effects.stop();
        }
    }

    private interface Scenario {
        void run(CosmeticEffects effects, Player player, List<Runnable> async, List<Runnable> sync);
    }
}
