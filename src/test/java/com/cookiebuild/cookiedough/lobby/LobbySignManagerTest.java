package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class LobbySignManagerTest {
    @Test
    void unchangedSignDoesNotWriteToWorldOrScheduleClientUpdates() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        LobbySignManager manager = new LobbySignManager(plugin);
        Sign sign = mock(Sign.class);
        Block block = mock(Block.class);
        Chunk chunk = mock(Chunk.class);
        when(sign.getBlock()).thenReturn(block);
        when(block.getChunk()).thenReturn(chunk);
        when(chunk.isLoaded()).thenReturn(true);
        Game game = mock(Game.class);
        when(game.getGameName()).thenReturn("FatKing");
        when(game.getState()).thenReturn(GameState.OPEN);
        when(game.getPlayerCount()).thenReturn(2);
        when(game.getCapacity()).thenReturn(8);
        when(sign.getLine(0)).thenReturn(ChatColor.BLUE + "Game");
        when(sign.getLine(1)).thenReturn(ChatColor.GOLD + "FatKing");
        when(sign.getLine(2)).thenReturn(ChatColor.GREEN + "OPEN");
        when(sign.getLine(3)).thenReturn(ChatColor.YELLOW + "2/8 players");

        assertFalse(manager.updateSignContentIfChanged(sign, game));

        verify(sign, never()).setLine(anyInt(), anyString());
        verify(sign, never()).update(anyBoolean());
        verify(chunk, never()).load(anyBoolean());
        verifyNoInteractions(plugin);
    }

    @Test
    void clearingAnEmptySignDoesNotLoadItsChunkOrWriteToWorld() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        LobbySignManager manager = new LobbySignManager(plugin);
        Sign sign = mock(Sign.class);
        when(sign.getLine(anyInt())).thenReturn("");

        assertFalse(manager.clearSignContentIfChanged(sign));

        verify(sign, never()).getBlock();
        verify(sign, never()).setLine(anyInt(), anyString());
        verify(sign, never()).update(anyBoolean());
        verifyNoInteractions(plugin);
    }
}
