package com.cookiebuild.cookiedough.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.theme.music.Instrument;
import com.cookiebuild.cookiedough.theme.music.Song;
import com.cookiebuild.cookiedough.theme.music.SongParser;

class SongParserTest {
    @Test
    void scientificPitchNotation() {
        assertEquals(60, SongParser.midi("C4"));
        assertEquals(69, SongParser.midi("A4"));
        assertEquals(66, SongParser.midi("F#4"));
        assertEquals(58, SongParser.midi("Bb3"));
        assertEquals(58, SongParser.midi("A#3"));
        assertEquals(30, SongParser.midi("F#1"));
    }

    @Test
    void instrumentsCoverTwoOctavesWithVanillaPitches() {
        assertEquals(0.5f, Instrument.HARP.pitch(54), 1e-6);
        assertEquals(1.0f, Instrument.HARP.pitch(66), 1e-6);
        assertEquals(2.0f, Instrument.HARP.pitch(78), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> Instrument.HARP.pitch(79));
        assertEquals(1.0f, Instrument.BELL.pitch(90), 1e-6);
    }

    @Test
    void phrasesTemposChordsAndRestsAreLaidOutOnTheTimeline() {
        String yaml = """
                title: Test
                phrases:
                  a: "C4 D4:2 | r E4+G4"
                voices:
                  - instrument: harp
                    play: "@2 $a @3 $a"
                """;
        Song song = SongParser.parse("test", new StringReader(yaml));
        // @2: C4@0 D4@2 r@6 E4+G4@8 -> ends 10; @3: C4@10 D4@13 r@19 E4+G4@22 -> ends 25
        assertEquals(25, song.lengthTicks());
        assertEquals(8, song.notes().size());
        assertEquals(0, song.notes().get(0).tick());
        assertEquals(8, song.notes().get(2).tick());
        assertEquals(8, song.notes().get(3).tick());
        assertEquals(22, song.notes().getLast().tick());
    }

    @Test
    void voicesOfEveryBundledSongEndTogether() throws Exception {
        YamlConfiguration theme = bundled("themes/halloween/theme.yml");
        for (String id : theme.getStringList("music.playlist")) {
            YamlConfiguration song = bundled("themes/halloween/music/" + id + ".yml");
            java.util.Set<Integer> ends = new java.util.HashSet<>();
            for (var voice : song.getMapList("voices")) {
                YamlConfiguration single = new YamlConfiguration();
                single.set("title", "voice");
                single.set("phrases", song.getConfigurationSection("phrases"));
                single.set("voices", java.util.List.of(voice));
                ends.add(SongParser.parse(id, single).lengthTicks());
            }
            assertEquals(1, ends.size(), id + " voices end at " + ends);
        }
    }

    @Test
    void notesOutsideTheInstrumentRangeAreRejectedAtLoadTime() {
        String yaml = """
                title: Too high
                voices:
                  - instrument: harp
                    play: "C6"
                """;
        assertThrows(IllegalArgumentException.class, () -> SongParser.parse("high", new StringReader(yaml)));
    }

    @Test
    void everyBundledHalloweenSongLoadsInRangeAndFitsTheLobby() throws Exception {
        YamlConfiguration theme = bundled("themes/halloween/theme.yml");
        var playlist = theme.getStringList("music.playlist");
        assertEquals(8, playlist.size());
        for (String id : playlist) {
            try (Reader reader = reader("themes/halloween/music/" + id + ".yml")) {
                Song song = SongParser.parse(id, reader);
                assertFalse(song.title().isBlank(), id);
                assertFalse(song.composer().isBlank(), id);
                assertTrue(song.seconds() >= 8 && song.seconds() <= 150, id + " lasts " + song.seconds() + " s");
                for (Song.Note note : song.notes()) {
                    assertTrue(note.instrument().plays(note.midi()), id + " " + note);
                    assertTrue(note.volume() > 0 && note.volume() <= 1, id);
                }
            }
        }
    }

    static YamlConfiguration bundled(String path) throws Exception {
        try (Reader reader = reader(path)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    static Reader reader(String path) {
        return new InputStreamReader(Objects.requireNonNull(
                SongParserTest.class.getClassLoader().getResourceAsStream(path), path), StandardCharsets.UTF_8);
    }
}
