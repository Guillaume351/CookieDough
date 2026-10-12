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
    XYLOPHONE("block.note_block.xylophone", 78);

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

    public static Instrument parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("An instrument is required");
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
