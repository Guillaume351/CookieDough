package com.cookiebuild.cookiedough.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

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
        for (String path : bundledSongs()) {
            YamlConfiguration song = bundled(path);
            java.util.Set<Integer> ends = new java.util.HashSet<>();
            for (var voice : song.getMapList("voices")) {
                YamlConfiguration single = new YamlConfiguration();
                single.set("title", "voice");
                single.set("phrases", song.getConfigurationSection("phrases"));
                single.set("voices", java.util.List.of(voice));
                ends.add(SongParser.parse(path, single).lengthTicks());
            }
            assertEquals(1, ends.size(), path + " voices end at " + ends);
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
    void everyBundledSongLoadsInRangeAndFitsTheLobby() throws Exception {
        List<String> songs = bundledSongs();
        assertTrue(songs.size() >= 17, "bundled songs: " + songs);
        for (String path : songs) {
            String text;
            try (Reader reader = reader(path)) {
                text = new java.io.BufferedReader(reader).lines()
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
            String header = text.lines().takeWhile(line -> line.startsWith("#"))
                    .collect(java.util.stream.Collectors.joining(" ")).toLowerCase(java.util.Locale.ROOT);
            assertTrue(header.contains("domaine public"), path + " must say it is in the public domain");
            Song song = SongParser.parse(path, new StringReader(text));
            assertFalse(song.title().isBlank(), path);
            assertFalse(song.composer().isBlank(), path);
            assertTrue(song.seconds() >= 8 && song.seconds() <= 150, path + " lasts " + song.seconds() + " s");
            for (Song.Note note : song.notes()) {
                assertTrue(note.instrument().plays(note.midi()), path + " " + note);
                assertTrue(note.volume() > 0 && note.volume() <= 1, path);
            }
        }
    }

    @Test
    void everyPlaylistSongIsBundled() throws Exception {
        for (String theme : themes()) {
            if (SongParserTest.class.getClassLoader().getResource("themes/" + theme + "/theme.yml") == null) continue;
            for (String id : bundled("themes/" + theme + "/theme.yml").getStringList("music.playlist")) {
                assertTrue(bundledSongs().contains("themes/" + theme + "/music/" + id + ".yml"),
                        theme + " playlist song " + id + " is not bundled");
            }
        }
        assertEquals(8, bundled("themes/halloween/theme.yml").getStringList("music.playlist").size());
    }

    @Test
    void christmasHasTheNineSongsOfTheReleaseAtAPleasantLength() throws Exception {
        Set<String> expected = Set.of("jingle-bells", "douce-nuit", "mon-beau-sapin", "deck-the-halls", "we-wish-you",
                "il-est-ne", "fee-dragee", "carol-of-the-bells", "les-anges");
        Set<String> found = new TreeSet<>();
        for (String path : bundledSongs()) {
            if (!path.startsWith("themes/christmas/music/")) continue;
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".yml".length());
            found.add(id);
            try (Reader reader = reader(path)) {
                Song song = SongParser.parse(id, reader);
                assertTrue(song.seconds() >= 30 && song.seconds() <= 75, id + " lasts " + song.seconds() + " s");
            }
        }
        assertEquals(new TreeSet<>(expected), found);
    }

    /** Every theme folder bundled under {@code themes/}. */
    static List<String> themes() throws Exception {
        Path root = Path.of(Objects.requireNonNull(
                SongParserTest.class.getClassLoader().getResource("themes"), "themes").toURI());
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory).map(child -> child.getFileName().toString()).sorted().toList();
        }
    }

    /** Resource paths of every bundled {@code themes/<theme>/music/*.yml} song. */
    static List<String> bundledSongs() throws Exception {
        List<String> songs = new ArrayList<>();
        for (String theme : themes()) {
            Path music = Path.of(Objects.requireNonNull(
                    SongParserTest.class.getClassLoader().getResource("themes/" + theme), theme).toURI()).resolve("music");
            if (!Files.isDirectory(music)) continue;
            try (Stream<Path> files = Files.list(music)) {
                files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".yml")).sorted()
                        .forEach(name -> songs.add("themes/" + theme + "/music/" + name));
            }
        }
        return songs;
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
