package com.cookiebuild.cookiedough.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cookiebuild.cookiedough.theme.music.Instrument;
import com.cookiebuild.cookiedough.theme.music.NbsParser;
import com.cookiebuild.cookiedough.theme.music.Song;

class NbsParserTest {
    /** Little-endian writer for the Note Block Studio layout. */
    private static final class Nbs {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Nbs u8(int value) {
            out.write(value);
            return this;
        }

        Nbs i16(int value) {
            out.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
            return this;
        }

        Nbs i32(int value) {
            out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
            return this;
        }

        Nbs str(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            i32(bytes.length);
            out.writeBytes(bytes);
            return this;
        }
    }

    private static byte[] openNbsV5() {
        Nbs nbs = new Nbs();
        nbs.i16(0).u8(5).u8(16).i16(9).i16(2); // new format, v5, 16 instruments, length, 2 layers
        nbs.str("Spooky").str("Arranger").str("Composer").str("");
        nbs.i16(1000); // 10 ticks per second: one NBS tick = 2 game ticks
        nbs.u8(0).u8(0).u8(4).i32(0).i32(0).i32(0).i32(0).i32(0).str("");
        nbs.u8(0).u8(0).i16(0); // loop settings
        // tick 0: harp F#4 (key 45) on layer 0, bass F#2 (key 33) on layer 1
        nbs.i16(1).i16(1).u8(0).u8(45).u8(100).u8(100).i16(0).i16(1).u8(1).u8(33).u8(50).u8(100).i16(0).i16(0);
        // tick 8: bell key 70 (above the vanilla range, folded down an octave), layer 0
        nbs.i16(8).i16(1).u8(7).u8(70).u8(100).u8(100).i16(0).i16(0);
        // custom instrument 20 is ignored
        nbs.i16(1).i16(1).u8(20).u8(45).u8(100).u8(100).i16(0).i16(0);
        nbs.i16(0);
        // layers: name, lock, volume, stereo
        nbs.str("melody").u8(0).u8(100).u8(100);
        nbs.str("bass").u8(0).u8(80).u8(100);
        nbs.u8(0); // no custom instruments
        return nbs.out.toByteArray();
    }

    @Test
    void readsOpenNbsNotesTempoAndVolumes() {
        Song song = NbsParser.parse("spooky", openNbsV5());
        assertEquals("Spooky", song.title());
        assertEquals("Composer", song.composer());
        List<Song.Note> notes = song.notes();
        assertEquals(3, notes.size());
        Song.Note harp = notes.stream().filter(n -> n.instrument() == Instrument.HARP).findFirst().orElseThrow();
        assertEquals(0, harp.tick());
        assertEquals(1.0f, harp.pitch(), 1e-6);
        Song.Note bass = notes.stream().filter(n -> n.instrument() == Instrument.BASS).findFirst().orElseThrow();
        assertEquals(0.4f, bass.volume(), 1e-6, "layer 80 % x velocity 50 %");
        assertEquals(0.5f, bass.pitch(), 1e-6, "key 33 is the lowest vanilla note");
        Song.Note bell = notes.stream().filter(n -> n.instrument() == Instrument.BELL).findFirst().orElseThrow();
        assertEquals(16, bell.tick(), "tick 8 at 10 t/s");
        assertTrue(bell.instrument().plays(bell.midi()));
        assertTrue(song.lengthTicks() > 16);
    }

    @Test
    void readsTheClassicFormat() {
        Nbs nbs = new Nbs();
        nbs.i16(4).i16(1).str("Old").str("A").str("").str("").i16(500);
        nbs.u8(0).u8(0).u8(4).i32(0).i32(0).i32(0).i32(0).i32(0).str("");
        nbs.i16(1).i16(1).u8(0).u8(57).i16(0).i16(0);
        Song song = NbsParser.parse("old", nbs.out.toByteArray());
        assertEquals("A", song.composer());
        assertEquals(1, song.notes().size());
        assertEquals(2.0f, song.notes().getFirst().pitch(), 1e-6, "key 57 is the highest vanilla note");
    }

    @Test
    void droppedSongFilesJoinTheRotation(@TempDir Path folder) throws Exception {
        Files.write(folder.resolve("this-is-halloween.nbs"), openNbsV5());
        Files.writeString(folder.resolve("extra.yml"), "title: x");
        Files.writeString(folder.resolve("notes.txt"), "ignored");
        var method = Class.forName("com.cookiebuild.cookiedough.theme.music.SongLibrary")
                .getDeclaredMethod("extraSongIds", java.io.File.class);
        method.setAccessible(true);
        assertEquals(List.of("extra", "this-is-halloween"), method.invoke(null, folder.toFile()));
    }
}
