package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class LobbyDisplayTextTest {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    @Test
    void anEmptyLobbyStillShowsBothCountersAndTheFeaturedTag() {
        assertEquals("Build Battle · Vedette\nLobby 0/8 · ouvert\n0 en jeu",
                plain(LobbyDisplayText.gameNpc("Build Battle", true, open(0, 8, 2, 0, -1, 0), Locale.FRENCH)));
        assertEquals("Pitchout\nLobby 0/16 · open\n3 playing",
                plain(LobbyDisplayText.gameNpc("Pitchout", false, open(0, 16, 2, 0, -1, 3), Locale.ENGLISH)));
    }

    @Test
    void aSoloStartModeShowsWhenTheLonePlayersMatchStartsInsteadOfAMissingPlayer() {
        String plate = plain(LobbyDisplayText.gameNpc("Build Battle", true, open(1, 8, 2, 0, 24, 0),
                Locale.FRENCH));
        assertEquals("Build Battle · Vedette\nLobby 1/8 · départ dans 24 s\n0 en jeu", plate);
        assertFalse(plate.contains("manque"), plate);
        assertEquals("Build Battle · Featured\nLobby 1/8 · starts in 24s\n2 playing",
                plain(LobbyDisplayText.gameNpc("Build Battle", true, open(1, 8, 2, 0, 24, 2), Locale.ENGLISH)));
    }

    @Test
    void otherModesKeepTheMinimumPlayersHint() {
        assertEquals("Micro Battles\nLobby 1/12 · il manque 3\n5 en jeu",
                plain(LobbyDisplayText.gameNpc("Micro Battles", false, open(1, 12, 4, 0, -1, 5), Locale.FRENCH)));
        assertEquals("Sky Wars\nLobby 2/8 · starting soon\n0 playing",
                plain(LobbyDisplayText.gameNpc("Sky Wars", false, open(2, 8, 2, 0, -1, 0), Locale.ENGLISH)));
    }

    @Test
    void theCountdownReplacesTheStateOnTheLobbyLine() {
        assertEquals("Pitchout\nLobby 3/16 · starts in 9s\n4 playing",
                plain(LobbyDisplayText.gameNpc("Pitchout", false, open(3, 16, 2, 9, -1, 4), Locale.ENGLISH)));
        assertEquals("Build Battle · Vedette\nLobby 1/8 · départ dans 7 s\n0 en jeu",
                plain(LobbyDisplayText.gameNpc("Build Battle", true, open(1, 8, 2, 7, -1, 0), Locale.FRENCH)));
    }

    @Test
    void closedAndPreparingLobbiesUseWordsNotSymbols() {
        assertEquals("Bed Wars [BETA]\nLobby 0/8 · closed\n2 playing",
                plain(LobbyDisplayText.gameNpc("Bed Wars [BETA]", false,
                        new LobbyDisplayText.QueueSnapshot(4, 8, 2, false, GameState.OPEN, 0, -1, 2),
                        Locale.ENGLISH)));
        assertEquals("Sky Wars\nLobby 0/8 · en préparation\n13 en jeu",
                plain(LobbyDisplayText.gameNpc("Sky Wars", false,
                        new LobbyDisplayText.QueueSnapshot(0, 8, 2, false, GameState.RUNNING, 0, -1, 13),
                        Locale.FRENCH)));
        assertEquals("Sky Wars\nLobby being prepared\n0 playing",
                plain(LobbyDisplayText.unavailableGameNpc("Sky Wars", false, 0, Locale.ENGLISH)));
    }

    @Test
    void persistentActivitiesKeepThePlayingCounterVisible() {
        assertEquals("Skyblock [BÊTA]\nJouable seul\n7 en jeu",
                plain(LobbyDisplayText.persistentActivityNpc("Skyblock [BÊTA]", 7, Locale.FRENCH)));
        assertEquals("Skyblock\nPlayable solo\n0 playing",
                plain(LobbyDisplayText.persistentActivityNpc("Skyblock", 0, Locale.ENGLISH)));
    }

    @Test
    void everyLocaleRendersTheSameThreeLineShape() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN,
                Locale.of("bg"), Locale.of("es"), Locale.of("hi"), Locale.of("pt", "BR"))) {
            String plate = plain(LobbyDisplayText.gameNpc("Build Battle", true, open(1, 8, 2, 0, 12, 3), locale));
            String[] lines = plate.split("\n");
            assertEquals(3, lines.length, plate);
            assertTrue(lines[1].contains("1/8") && lines[1].contains("12"), plate);
            assertTrue(lines[2].contains("3"), plate);
            assertFalse(plate.contains("lobby.npc."), "missing translation in " + locale + ": " + plate);
        }
    }

    @Test
    void nameplatesNeverUseEmojiThatBedrockRendersAsBoxes() {
        List<Component> plates = List.of(
                LobbyDisplayText.gameNpc("Build Battle", true, open(1, 8, 2, 0, 20, 3), Locale.FRENCH),
                LobbyDisplayText.gameNpc("Build Battle", true, open(2, 8, 2, 4, -1, 3), Locale.FRENCH),
                LobbyDisplayText.gameNpc("Pitchout", false, open(1, 16, 2, 0, -1, 0), Locale.FRENCH),
                LobbyDisplayText.gameNpc("Pitchout", false,
                        new LobbyDisplayText.QueueSnapshot(0, 16, 2, false, GameState.OPEN, 0, -1, 0), Locale.FRENCH),
                LobbyDisplayText.unavailableGameNpc("Fat King", false, 1, Locale.FRENCH),
                LobbyDisplayText.persistentActivityNpc("Skyblock", 1, Locale.FRENCH),
                LobbyDisplayText.onlinePlayers(3, 2, Locale.FRENCH));
        for (Component plate : plates) {
            String text = plain(plate);
            // Latin-1 only (letters, accents, U+00B7 separator): no emoji, dingbats,
            // typographic apostrophes or ellipses that a Bedrock font may lack.
            assertFalse(text.codePoints().anyMatch(point -> point > 0xFF), text);
            for (String line : text.split("\n")) {
                assertTrue(line.length() <= 30, "nameplate line too wide for Bedrock: " + line);
            }
        }
    }

    @Test
    void keepsTheChampionLabelOnTheSameVanillaEntity() {
        assertEquals("★ Alex • 1 ★", plain(LobbyDisplayText.championHead("Alex", 1)));
        assertEquals("★ Steve • 4 ★", plain(LobbyDisplayText.championHead("Steve", 4)));
    }

    @Test
    void theGlobalCounterAlwaysShowsBothLines() {
        assertEquals("1 en ligne\n0 en jeu", plain(LobbyDisplayText.onlinePlayers(1, 0, Locale.FRENCH)));
        assertEquals("12 online\n8 playing", plain(LobbyDisplayText.onlinePlayers(12, 8, Locale.ENGLISH)));
    }

    private static LobbyDisplayText.QueueSnapshot open(int queued, int capacity, int minimumPlayers,
            int countdownSeconds, int soloStartSeconds, int playing) {
        return new LobbyDisplayText.QueueSnapshot(queued, capacity, minimumPlayers, true, GameState.OPEN,
                countdownSeconds, soloStartSeconds, playing);
    }

    private static String plain(Component component) {
        return PLAIN_TEXT.serialize(component);
    }
}
