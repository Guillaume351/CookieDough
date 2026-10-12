package com.cookiebuild.cookiedough.theme.music;

import java.util.Comparator;
import java.util.List;

/** A compiled song: every note with its start tick, sorted by time. */
public record Song(String id, String title, String composer, List<Note> notes, int lengthTicks) {
    /** One note block sound at {@code tick} after the start of the song. */
    public record Note(int tick, Instrument instrument, int midi, float volume) {
        public float pitch() {
            return instrument.pitch(midi);
        }
    }

    public Song {
        if (id == null || id.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("A song needs an id and a title");
        }
        composer = composer == null ? "" : composer;
        notes = notes.stream().sorted(Comparator.comparingInt(Note::tick)).toList();
        if (notes.isEmpty() || lengthTicks <= 0) throw new IllegalArgumentException("Song " + id + " is empty");
    }

    public double seconds() {
        return lengthTicks / 20.0;
    }
}
