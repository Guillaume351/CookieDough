package com.cookiebuild.cookiedough.theme.music;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads the small song notation of {@code themes/<id>/music/*.yml}.
 *
 * <pre>
 * phrases:            # reusable fragments, referenced as $name
 *   a: "B3 C#4:2 r D4+F#4:4 | ..."
 * voices:
 *   - instrument: harp
 *     transpose: 24   # semitones, optional
 *     volume: 0.7     # optional, 0..1
 *     play: "@4 $a $a @3 $a"
 * </pre>
 *
 * Tokens: a note ({@code C#4}, {@code Bb3}) or a chord ({@code C4+E4+G4}),
 * optionally followed by {@code :units}; {@code r[:units]} is a rest;
 * {@code @n} sets the length of one unit in game ticks for what follows;
 * {@code |} is a bar line and is ignored. Voices are laid out independently
 * from tick 0, so each voice repeats the same tempo marks.
 */
public final class SongParser {
    private static final Pattern NOTE = Pattern.compile("([A-Ga-g])([#b]?)(-?\\d)");
    private static final Map<Character, Integer> STEPS = Map.of(
            'C', 0, 'D', 2, 'E', 4, 'F', 5, 'G', 7, 'A', 9, 'B', 11);
    private static final int MAX_EXPANSION = 20_000;

    private SongParser() {
    }

    public static Song parse(String id, Reader reader) {
        return parse(id, YamlConfiguration.loadConfiguration(reader));
    }

    public static Song parse(String id, ConfigurationSection yaml) {
        String title = yaml.getString("title");
        Map<String, String> phrases = new HashMap<>();
        ConfigurationSection phraseSection = yaml.getConfigurationSection("phrases");
        if (phraseSection != null) {
            for (String key : phraseSection.getKeys(false)) phrases.put(key, phraseSection.getString(key, ""));
        }
        List<Song.Note> notes = new ArrayList<>();
        int length = 0;
        List<Map<?, ?>> voices = yaml.getMapList("voices");
        if (voices.isEmpty()) throw new IllegalArgumentException("Song " + id + " has no voice");
        for (Map<?, ?> voice : voices) {
            Instrument instrument = Instrument.parse(String.valueOf(voice.get("instrument")));
            int transpose = voice.get("transpose") instanceof Number n ? n.intValue() : 0;
            float volume = voice.get("volume") instanceof Number n ? n.floatValue() : 1.0f;
            if (volume <= 0 || volume > 1) throw new IllegalArgumentException("Song " + id + ": volume must be in (0, 1]");
            String play = voice.get("play") == null ? "" : String.valueOf(voice.get("play"));
            length = Math.max(length, layout(id, expand(id, play, phrases), instrument, transpose, volume, notes));
        }
        return new Song(id, title, yaml.getString("composer", ""), notes, length);
    }

    static List<String> expand(String id, String play, Map<String, String> phrases) {
        List<String> tokens = new ArrayList<>();
        expandInto(id, play, phrases, tokens, 0);
        return tokens;
    }

    private static void expandInto(String id, String text, Map<String, String> phrases, List<String> out, int depth) {
        if (depth > 8) throw new IllegalArgumentException("Song " + id + ": phrases nest too deeply");
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty() || token.equals("|")) continue;
            if (token.startsWith("$")) {
                String phrase = phrases.get(token.substring(1));
                if (phrase == null) throw new IllegalArgumentException("Song " + id + ": unknown phrase " + token);
                expandInto(id, phrase, phrases, out, depth + 1);
            } else {
                out.add(token);
            }
            if (out.size() > MAX_EXPANSION) throw new IllegalArgumentException("Song " + id + " is too long");
        }
    }

    /** Places one voice's tokens on the timeline and returns where it ends, in ticks. */
    private static int layout(String id, List<String> tokens, Instrument instrument, int transpose, float volume,
            List<Song.Note> out) {
        int tick = 0;
        int unitTicks = 4;
        for (String token : tokens) {
            if (token.startsWith("@")) {
                unitTicks = Integer.parseInt(token.substring(1));
                if (unitTicks < 1 || unitTicks > 40) throw new IllegalArgumentException("Song " + id + ": bad tempo " + token);
                continue;
            }
            String body = token;
            int units = 1;
            int colon = token.lastIndexOf(':');
            if (colon > 0) {
                body = token.substring(0, colon);
                units = Integer.parseInt(token.substring(colon + 1));
                if (units < 1) throw new IllegalArgumentException("Song " + id + ": bad length " + token);
            }
            if (!body.equalsIgnoreCase("r") && !body.equals("-")) {
                for (String part : body.split("\\+")) {
                    int midi = midi(part) + transpose;
                    if (!instrument.plays(midi)) {
                        throw new IllegalArgumentException("Song " + id + ": " + instrument + " cannot play "
                                + part + (transpose == 0 ? "" : " transposed by " + transpose));
                    }
                    out.add(new Song.Note(tick, instrument, midi, volume));
                }
            }
            tick += units * unitTicks;
        }
        return tick;
    }

    /** Scientific pitch notation to MIDI number: C4 = 60, A4 = 69. */
    public static int midi(String note) {
        Matcher matcher = NOTE.matcher(note.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Not a note: " + note);
        char letter = matcher.group(1).toUpperCase(Locale.ROOT).charAt(0);
        int accidental = switch (matcher.group(2)) {
            case "#" -> 1;
            case "b" -> -1;
            default -> 0;
        };
        int octave = Integer.parseInt(matcher.group(3));
        return (octave + 1) * 12 + STEPS.get(letter) + accidental;
    }
}
