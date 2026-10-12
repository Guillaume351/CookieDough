package com.cookiebuild.cookiedough.theme.music;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Note Block Studio {@code .nbs} files (classic format and OpenNBS
 * versions 1 to 5), so operators can add a song by dropping a file in the
 * theme's music folder. Custom instruments are skipped; keys outside the
 * vanilla two octaves are folded back by octaves.
 */
public final class NbsParser {
    /** NBS key of the lowest vanilla note block note (F#3 on the harp). */
    private static final int LOWEST_VANILLA_KEY = 33;
    private static final int MAX_NOTES = 50_000;

    private NbsParser() {
    }

    public static Song parse(String id, byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int length = in.getShort() & 0xFFFF;
        int version = 0;
        if (length == 0) {
            version = in.get() & 0xFF;
            in.get(); // vanilla instrument count
            if (version >= 3) in.getShort(); // song length
        }
        int layerCount = in.getShort() & 0xFFFF;
        String name = string(in);
        String author = string(in);
        String originalAuthor = string(in);
        string(in); // description
        int tempo = in.getShort() & 0xFFFF; // ticks per second x 100
        if (tempo <= 0) throw new IllegalArgumentException("Song " + id + " has no tempo");
        in.get(); // auto-save
        in.get(); // auto-save minutes
        in.get(); // time signature
        in.getInt(); // minutes spent
        in.getInt(); // left clicks
        in.getInt(); // right clicks
        in.getInt(); // note blocks added
        in.getInt(); // note blocks removed
        string(in); // imported file name
        if (version >= 4) {
            in.get(); // loop
            in.get(); // max loop count
            in.getShort(); // loop start tick
        }

        record Raw(int tick, int layer, int instrument, int key, int velocity) {
        }
        List<Raw> raw = new ArrayList<>();
        int tick = -1;
        while (true) {
            int jumpTicks = in.getShort() & 0xFFFF;
            if (jumpTicks == 0) break;
            tick += jumpTicks;
            int layer = -1;
            while (true) {
                int jumpLayers = in.getShort() & 0xFFFF;
                if (jumpLayers == 0) break;
                layer += jumpLayers;
                int instrument = in.get() & 0xFF;
                int key = in.get() & 0xFF;
                int velocity = 100;
                if (version >= 4) {
                    velocity = in.get() & 0xFF;
                    in.get(); // panning
                    in.getShort(); // fine pitch
                }
                raw.add(new Raw(tick, layer, instrument, key, velocity));
                if (raw.size() > MAX_NOTES) throw new IllegalArgumentException("Song " + id + " is too long");
            }
        }
        Map<Integer, Integer> layerVolume = new HashMap<>();
        if (in.hasRemaining()) {
            for (int layer = 0; layer < layerCount && in.hasRemaining(); layer++) {
                string(in); // layer name
                if (version >= 4) in.get(); // lock
                layerVolume.put(layer, in.get() & 0xFF);
                if (version >= 2) in.get(); // stereo
            }
        }

        double gameTicksPerNbsTick = 20.0 / (tempo / 100.0);
        List<Song.Note> notes = new ArrayList<>();
        int last = 0;
        for (Raw note : raw) {
            Instrument instrument = Instrument.fromNbs(note.instrument());
            if (instrument == null) continue;
            int offset = note.key() - LOWEST_VANILLA_KEY;
            while (offset < 0) offset += 12;
            while (offset > Instrument.RANGE) offset -= 12;
            float volume = (float) (layerVolume.getOrDefault(note.layer(), 100) / 100.0 * Math.min(100, note.velocity()) / 100.0);
            if (volume <= 0.01f) continue;
            int at = (int) Math.round(note.tick() * gameTicksPerNbsTick);
            notes.add(new Song.Note(at, instrument, instrument.lowestMidi() + offset, Math.min(1f, volume)));
            last = Math.max(last, at);
        }
        String title = name.isBlank() ? id : name.trim();
        String composer = !originalAuthor.isBlank() ? originalAuthor.trim() : author.trim();
        return new Song(id, title, composer, notes, last + (int) Math.ceil(gameTicksPerNbsTick * 4));
    }

    private static String string(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > in.remaining()) throw new IllegalArgumentException("Corrupt NBS string");
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
