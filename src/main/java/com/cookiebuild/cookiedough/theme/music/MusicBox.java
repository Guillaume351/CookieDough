package com.cookiebuild.cookiedough.theme.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Plays a playlist of note block songs at a point. Each listener hears every
 * note from a point two blocks away in the box's direction, at a gain that we
 * compute ourselves: vanilla attenuation would be either too loud near the
 * box or too short-ranged. Sounds use the "Jukebox/Note Blocks" category so
 * players can also lower them in their own settings.
 */
public final class MusicBox {
    private final MusicBoxPlan plan;
    private final List<Song> playlist;
    private final Predicate<Player> listening;
    private int songIndex;
    private int tick;
    private int cursor;
    private int pause;
    private int lastParticleTick = -100;

    public MusicBox(MusicBoxPlan plan, List<Song> playlist, Predicate<Player> listening) {
        if (playlist.isEmpty()) throw new IllegalArgumentException("A music box needs at least one song");
        this.plan = plan;
        this.playlist = List.copyOf(playlist);
        this.listening = listening;
        this.songIndex = ThreadLocalRandom.current().nextInt(playlist.size());
    }

    public MusicBoxPlan plan() {
        return plan;
    }

    public Optional<Song> current() {
        return pause > 0 ? Optional.empty() : Optional.of(playlist.get(songIndex));
    }

    public Song upcoming() {
        return pause > 0 ? playlist.get(songIndex) : playlist.get((songIndex + 1) % playlist.size());
    }

    /** Skips to the next song right away. */
    public void next() {
        if (pause == 0) songIndex = (songIndex + 1) % playlist.size();
        pause = 0;
        tick = 0;
        cursor = 0;
    }

    /** Advances one game tick and plays the notes that start now. */
    public void tick(World world) {
        if (pause > 0) {
            pause--;
            return;
        }
        Song song = playlist.get(songIndex);
        List<Song.Note> due = new ArrayList<>();
        while (cursor < song.notes().size() && song.notes().get(cursor).tick() <= tick) {
            due.add(song.notes().get(cursor++));
        }
        if (!due.isEmpty()) play(world, due);
        tick++;
        if (tick > song.lengthTicks()) {
            songIndex = (songIndex + 1) % playlist.size();
            tick = 0;
            cursor = 0;
            pause = plan.gapTicks();
        }
    }

    private void play(World world, List<Song.Note> notes) {
        Location box = new Location(world, plan.x(), plan.y(), plan.z());
        boolean showNote = tick - lastParticleTick >= 5;
        if (showNote) lastParticleTick = tick;
        for (Player player : world.getPlayers()) {
            if (!listening.test(player)) continue;
            Location ear = player.getEyeLocation();
            double distance = ear.distance(box);
            float gain = plan.gainAt(distance);
            if (gain <= 0.01f) continue;
            Location from = box;
            if (distance > 2.0) {
                Vector direction = box.toVector().subtract(ear.toVector()).normalize().multiply(2.0);
                from = ear.clone().add(direction);
            }
            for (Song.Note note : notes) {
                player.playSound(from, note.instrument().soundKey(), SoundCategory.RECORDS,
                        gain * note.volume(), note.pitch());
            }
            if (showNote && distance < 32) {
                Song.Note top = notes.getLast();
                double colour = ((top.midi() - 6) % 24) / 24.0;
                player.spawnParticle(Particle.NOTE, box.clone().add(
                        ThreadLocalRandom.current().nextDouble(-0.6, 0.6), 2.3,
                        ThreadLocalRandom.current().nextDouble(-0.6, 0.6)), 0, colour, 0, 0, 1);
            }
        }
    }
}
