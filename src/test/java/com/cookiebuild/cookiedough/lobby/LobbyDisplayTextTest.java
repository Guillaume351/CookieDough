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
                "MicroBattles\n👥 13 • Lobby 0/8 • ▶",
                PLAIN_TEXT.serialize(LobbyDisplayText.gameNpc(
                        "MicroBattles", 13, 0, 8, false, GameState.RUNNING, 0)));
        assertEquals(
                "Pitchout\n👥 0 • Lobby 0/16 • ✔",
                PLAIN_TEXT.serialize(LobbyDisplayText.gameNpc(
                        "Pitchout", 0, 0, 16, true, GameState.OPEN, 0)));
    }

    @Test
    void addsAClearCountdownLineWhenTheQueueIsStarting() {
        assertEquals(
                "Pitchout\n👥 4 • Lobby 1/16\n⏳ 9s",
                PLAIN_TEXT.serialize(LobbyDisplayText.gameNpc(
                        "Pitchout", 4, 1, 16, true, GameState.OPEN, 9)));
    }

    @Test
    void marksAnOpenArenaWithClosedAdmissionsAsUnavailable() {
        assertEquals(
                "BedWars\n👥 2 • Lobby 0/8 • ✖",
                PLAIN_TEXT.serialize(LobbyDisplayText.gameNpc(
                        "BedWars", 2, 0, 8, false, GameState.OPEN, 0)));
    }

    @Test
    void keepsUnavailableNpcNamesCompact() {
        assertEquals(
                "SkyWars\n👥 0 • Lobby 0/- • ✖",
                PLAIN_TEXT.serialize(LobbyDisplayText.unavailableGameNpc("SkyWars", 0)));
    }

    @Test
    void makesPersistentActivitiesAnObviousLobbyDestination() {
        assertEquals(
                "Skyblock [BETA]\n👥 7 • ✔",
                PLAIN_TEXT.serialize(LobbyDisplayText.persistentActivityNpc("Skyblock [BETA]", 7)));
    }

    @Test
    void keepsTheChampionLabelOnTheSameVanillaEntity() {
        assertEquals("★ Alex • 1 ★",
                PLAIN_TEXT.serialize(LobbyDisplayText.championHead("Alex", 1)));
        assertEquals("★ Steve • 4 ★",
                PLAIN_TEXT.serialize(LobbyDisplayText.championHead("Steve", 4)));
    }

    @Test
    void formatsTheGlobalOnlineCounterWithCorrectPluralization() {
        assertEquals("👥 0\n🎮 0", PLAIN_TEXT.serialize(LobbyDisplayText.onlinePlayers(0, 0)));
        assertEquals("👥 1\n🎮 1", PLAIN_TEXT.serialize(LobbyDisplayText.onlinePlayers(1, 1)));
        assertEquals("👥 12\n🎮 8", PLAIN_TEXT.serialize(LobbyDisplayText.onlinePlayers(12, 8)));
    }
}
