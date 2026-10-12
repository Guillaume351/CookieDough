package com.cookiebuild.cookiedough.theme.music;

import java.util.Locale;

/**
 * Vanilla note block instruments. Each covers two octaves starting at
 * {@code lowestMidi}; a note plays at pitch {@code 2^((n - 12) / 12)} where
 * {@code n} is its distance in semitones from that lowest note (0..24).
 */
public enum Instrument {
    HARP("block.note_block.harp", 54),
    PLING("block.note_block.pling", 54),
    BIT("block.note_block.bit", 54),
    BANJO("block.note_block.banjo", 54),
    IRON_XYLOPHONE("block.note_block.iron_xylophone", 54),
    GUITAR("block.note_block.guitar", 42),
    BASS("block.note_block.bass", 30),
    DIDGERIDOO("block.note_block.didgeridoo", 30),
    FLUTE("block.note_block.flute", 66),
    COW_BELL("block.note_block.cow_bell", 66),
    BELL("block.note_block.bell", 78),
    CHIME("block.note_block.chime", 78),
    XYLOPHONE("block.note_block.xylophone", 78),
    BASEDRUM("block.note_block.basedrum", 54),
    SNARE("block.note_block.snare", 54),
    HAT("block.note_block.hat", 54);

    /** Note Block Studio instrument ids 0..15, in file order. */
    private static final Instrument[] NBS = {HARP, BASS, BASEDRUM, SNARE, HAT, GUITAR, FLUTE, BELL, CHIME, XYLOPHONE,
            IRON_XYLOPHONE, COW_BELL, DIDGERIDOO, BIT, BANJO, PLING};

    public static final int RANGE = 24;

    private final String soundKey;
    private final int lowestMidi;

    Instrument(String soundKey, int lowestMidi) {
        this.soundKey = soundKey;
        this.lowestMidi = lowestMidi;
    }

    public String soundKey() {
        return soundKey;
    }

    public int lowestMidi() {
        return lowestMidi;
    }

    public int highestMidi() {
        return lowestMidi + RANGE;
    }

    public boolean plays(int midi) {
        return midi >= lowestMidi && midi <= highestMidi();
    }

    /** Client pitch multiplier for a MIDI note this instrument can play. */
    public float pitch(int midi) {
        if (!plays(midi)) {
            throw new IllegalArgumentException(name() + " cannot play MIDI note " + midi);
        }
        return (float) Math.pow(2.0, (midi - lowestMidi - 12) / 12.0);
    }

    /** The vanilla instrument of a Note Block Studio instrument id, or null for a custom one. */
    public static Instrument fromNbs(int id) {
        return id >= 0 && id < NBS.length ? NBS[id] : null;
    }

    public static Instrument parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("An instrument is required");
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
