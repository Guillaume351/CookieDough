package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class LobbyDisplayTextTest {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    @Test
    void anEmptyQueueEncouragesInsteadOfShowingZeros() {
        assertEquals("Build Battle\nMode vedette\nDémarre dès 2 joueurs",
                plain(LobbyDisplayText.gameNpc("Build Battle", true, 0, 0, 8, 2, true, GameState.OPEN, 0,
                        Locale.FRENCH)));
        assertEquals("Pitchout\nStarts with 2 players",
                plain(LobbyDisplayText.gameNpc("Pitchout", false, 0, 0, 16, 2, true, GameState.OPEN, 0,
                        Locale.ENGLISH)));
    }

    @Test
    void showsHowManyPlayersAreStillMissingAndOtherArenasInPlay() {
        assertEquals("Micro Battles\n1 en file · plus que 3 pour démarrer · 5 en jeu",
                plain(LobbyDisplayText.gameNpc("Micro Battles", false, 6, 1, 12, 4, true, GameState.OPEN, 0,
                        Locale.FRENCH)));
    }

    @Test
    void addsAClearCountdownLineWhenTheQueueIsStarting() {
        assertEquals("Pitchout\nStarting in 9 s · 1/16",
                plain(LobbyDisplayText.gameNpc("Pitchout", false, 4, 1, 16, 2, true, GameState.OPEN, 9,
                        Locale.ENGLISH)));
    }

    @Test
    void closedRunningAndMissingArenasUseWordsNotSymbols() {
        assertEquals("Bed Wars [BETA]\nUnavailable right now · 2 playing",
                plain(LobbyDisplayText.gameNpc("Bed Wars [BETA]", false, 2, 0, 8, 2, false, GameState.OPEN, 0,
                        Locale.ENGLISH)));
        assertEquals("Sky Wars\nMatch in progress · 13 playing",
                plain(LobbyDisplayText.gameNpc("Sky Wars", false, 13, 0, 8, 2, false, GameState.RUNNING, 0,
                        Locale.ENGLISH)));
        assertEquals("Sky Wars\nPreparing the arena",
                plain(LobbyDisplayText.unavailableGameNpc("Sky Wars", false, 0, Locale.ENGLISH)));
    }

    @Test
    void makesPersistentActivitiesAnObviousSoloDestination() {
        assertEquals("Skyblock [BÊTA]\nJouable seul · 7 en ligne",
                plain(LobbyDisplayText.persistentActivityNpc("Skyblock [BÊTA]", 7, Locale.FRENCH)));
        assertEquals("Skyblock\nPlayable solo",
                plain(LobbyDisplayText.persistentActivityNpc("Skyblock", 0, Locale.ENGLISH)));
    }

    @Test
    void nameplatesNeverUseEmojiThatBedrockRendersAsBoxes() {
        List<Component> plates = List.of(
                LobbyDisplayText.gameNpc("Build Battle", true, 3, 1, 8, 2, true, GameState.OPEN, 0, Locale.FRENCH),
                LobbyDisplayText.gameNpc("Build Battle", true, 3, 2, 8, 2, true, GameState.OPEN, 4, Locale.FRENCH),
                LobbyDisplayText.unavailableGameNpc("Fat King", false, 1, Locale.FRENCH),
                LobbyDisplayText.persistentActivityNpc("Skyblock", 1, Locale.FRENCH),
                LobbyDisplayText.onlinePlayers(3, 2, Locale.FRENCH));
        for (Component plate : plates) {
            String text = plain(plate);
            assertFalse(text.codePoints().anyMatch(point -> point > 0xFFFF || (point >= 0x2300 && point <= 0x27BF)),
                    text);
        }
    }

    @Test
    void keepsTheChampionLabelOnTheSameVanillaEntity() {
        assertEquals("★ Alex • 1 ★", plain(LobbyDisplayText.championHead("Alex", 1)));
        assertEquals("★ Steve • 4 ★", plain(LobbyDisplayText.championHead("Steve", 4)));
    }

    @Test
    void formatsTheGlobalOnlineCounterWithWords() {
        assertEquals("1 en ligne", plain(LobbyDisplayText.onlinePlayers(1, 0, Locale.FRENCH)));
        assertEquals("12 online\n8 playing", plain(LobbyDisplayText.onlinePlayers(12, 8, Locale.ENGLISH)));
    }

    private static String plain(Component component) {
        return PLAIN_TEXT.serialize(component);
    }
}
