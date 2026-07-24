package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.GameState;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class LobbyDisplayTextTest {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    @Test
    void splitsNpcNameAndStatusAcrossTwoCompactLines() {
        assertEquals(
                "MicroBattles\n3/8 • IN GAME",
                PLAIN_TEXT.serialize(LobbyDisplayText.gameNpc("MicroBattles", 3, 8, GameState.RUNNING)));
        assertEquals(
                "Pitchout\n0/4 • JOIN",
                PLAIN_TEXT.serialize(LobbyDisplayText.gameNpc("Pitchout", 0, 4, GameState.OPEN)));
    }

    @Test
    void keepsUnavailableNpcNamesCompact() {
        assertEquals(
                "SkyWars\nUnavailable",
                PLAIN_TEXT.serialize(LobbyDisplayText.unavailableGameNpc("SkyWars")));
    }

    @Test
    void formatsTheGlobalOnlineCounterWithCorrectPluralization() {
        assertEquals("0 players online\n0 in games", PLAIN_TEXT.serialize(LobbyDisplayText.onlinePlayers(0, 0)));
        assertEquals("1 player online\n1 in game", PLAIN_TEXT.serialize(LobbyDisplayText.onlinePlayers(1, 1)));
        assertEquals("12 players online\n8 in games", PLAIN_TEXT.serialize(LobbyDisplayText.onlinePlayers(12, 8)));
    }
}
