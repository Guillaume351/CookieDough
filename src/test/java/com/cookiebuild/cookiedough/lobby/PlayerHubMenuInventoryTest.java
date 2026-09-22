package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;

class PlayerHubMenuInventoryTest {
    @Test
    void recognizesOnlyItsOwnHolder() throws ClassNotFoundException {
        assertFalse(PlayerHubMenu.isMenuInventory(null));
        Inventory inventory = mock(Inventory.class);
        assertFalse(PlayerHubMenu.isMenuInventory(inventory));
        when(inventory.getHolder()).thenReturn(mock(InventoryHolder.class));
        assertFalse(PlayerHubMenu.isMenuInventory(inventory));
        Class<?> holderType = Class.forName(PlayerHubMenu.class.getName() + "$MenuHolder");
        when(inventory.getHolder()).thenReturn((InventoryHolder) mock(holderType));
        assertTrue(PlayerHubMenu.isMenuInventory(inventory));
    }
}
