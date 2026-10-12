package com.cookiebuild.cookiedough.theme.music;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.lobby.LobbyEntityOwnership;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The theme's music box: a non-persistent musician NPC that loops a playlist
 * of note block songs. Players opt out with {@code /musique}; the choice lives
 * in their own player data.
 */
public final class MusicService implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey mutedKey;
    private final NamespacedKey musicianKey;
    private MusicBox box;
    private String messages;
    private BukkitTask ticker;
    private BukkitTask keeper;
    private Witch musician;

    public MusicService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mutedKey = new NamespacedKey(plugin, "theme_music_muted");
        this.musicianKey = new NamespacedKey(plugin, "theme_musician");
    }

    public void activate(MusicBoxPlan plan, List<Song> songs, String messagePrefix) {
        deactivate();
        World world = Bukkit.getWorld(plan.world());
        if (world == null || songs.isEmpty()) {
            plugin.getLogger().warning("Theme music disabled: world " + plan.world() + " or songs missing");
            return;
        }
        this.messages = messagePrefix;
        this.box = new MusicBox(plan, songs, player -> !isMuted(player));
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> box.tick(world), 40L, 1L);
        this.keeper = Bukkit.getScheduler().runTaskTimer(plugin, () -> keepMusician(world), 20L, 100L);
        plugin.getLogger().info("Theme music: " + songs.size() + " songs at " + plan.x() + "," + plan.y() + ","
                + plan.z());
    }

    public void deactivate() {
        if (ticker != null) ticker.cancel();
        if (keeper != null) keeper.cancel();
        ticker = null;
        keeper = null;
        box = null;
        if (musician != null && musician.isValid()) musician.remove();
        musician = null;
    }

    public boolean active() {
        return box != null;
    }

    public Optional<Song> nowPlaying() {
        return box == null ? Optional.empty() : box.current();
    }

    public void next() {
        if (box != null) box.next();
    }

    public boolean isMuted(Player player) {
        return player.getPersistentDataContainer().has(mutedKey, PersistentDataType.BYTE);
    }

    /** Flips the player's choice and returns true when the music is now on. */
    public boolean toggle(Player player) {
        if (isMuted(player)) {
            player.getPersistentDataContainer().remove(mutedKey);
            return true;
        }
        player.getPersistentDataContainer().set(mutedKey, PersistentDataType.BYTE, (byte) 1);
        player.stopSound(org.bukkit.SoundCategory.RECORDS);
        return false;
    }

    private void keepMusician(World world) {
        if (box == null) return;
        MusicBoxPlan plan = box.plan();
        Location at = new Location(world, plan.x(), plan.y(), plan.z(), plan.yaw(), 0f);
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        if (musician == null || !musician.isValid()) {
            musician = world.spawn(at, Witch.class, witch -> {
                witch.setPersistent(false);
                witch.setRemoveWhenFarAway(false);
                witch.setAI(false);
                witch.setSilent(true);
                witch.setInvulnerable(true);
                witch.setCollidable(false);
                witch.setCanPickupItems(false);
                witch.customName(Component.text(LocaleManager.getMessage(messages + ".music.witch", Locale.FRENCH),
                        NamedTextColor.DARK_PURPLE));
                witch.setCustomNameVisible(true);
                witch.getPersistentDataContainer().set(musicianKey, PersistentDataType.BYTE, (byte) 1);
                LobbyEntityOwnership.mark(plugin, witch, "theme_musician");
            });
        } else if (musician.getLocation().distanceSquared(at) > 1.0) {
            musician.teleport(at);
        }
        if (box.current().isPresent()) {
            world.spawnParticle(Particle.WITCH, at.clone().add(0, 2.2, 0), 4, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private boolean isMusician(Entity entity) {
        return entity instanceof Witch && entity.getPersistentDataContainer().has(musicianKey, PersistentDataType.BYTE);
    }

    private void talk(Player player) {
        if (box == null) return;
        Optional<Song> current = box.current();
        if (current.isPresent()) {
            player.sendMessage(LocaleManager.getMessage(messages + ".music.now", player.locale(),
                    current.get().title(), current.get().composer()));
        } else {
            player.sendMessage(LocaleManager.getMessage(messages + ".music.next", player.locale(),
                    box.upcoming().title()));
        }
        player.sendMessage(LocaleManager.getMessage("theme.music.hint", player.locale()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isMusician(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) talk(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!isMusician(event.getAttacked())) return;
        event.setCancelled(true);
        talk(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (isMusician(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCombust(EntityCombustEvent event) {
        if (isMusician(event.getEntity())) event.setCancelled(true);
    }
}
