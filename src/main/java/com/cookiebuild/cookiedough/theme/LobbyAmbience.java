package com.cookiebuild.cookiedough.theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.lobby.LobbyEntityOwnership;
import com.cookiebuild.cookiedough.theme.decor.FakeBlock;

/**
 * Lobby life: particles drifting from some decor blocks (souls at Halloween),
 * an optional gentle snowfall around each player, a few bats that never leave
 * the bowl and rare, quiet ambient sounds. Particles and sounds are sent to
 * each player only; bats are non-persistent entities.
 */
final class LobbyAmbience implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey batKey;
    private final List<Bat> bats = new ArrayList<>();
    private final Map<UUID, Long> nextSound = new HashMap<>();
    private ThemeDefinition.Lobby lobby;
    private List<FakeBlock> anchors = List.of();
    private Location spawn;
    private Location center;
    private BukkitTask particles;
    private BukkitTask keeper;

    LobbyAmbience(JavaPlugin plugin) {
        this.plugin = plugin;
        this.batKey = new NamespacedKey(plugin, "theme_bat");
    }

    void activate(ThemeDefinition.Lobby lobby, World world, List<FakeBlock> decor) {
        deactivate();
        this.lobby = lobby;
        this.center = world.getSpawnLocation().clone();
        this.spawn = center;
        // Decor blocks that some particle rule anchors to (lit pumpkins, hanging moss...).
        this.anchors = decor.stream().filter(block -> anchorRule(lobby, block) != null).toList();
        this.particles = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(world), 40L, 10L);
        this.keeper = Bukkit.getScheduler().runTaskTimer(plugin, () -> keepBats(world), 60L, 100L);
    }

    void deactivate() {
        if (particles != null) particles.cancel();
        if (keeper != null) keeper.cancel();
        particles = null;
        keeper = null;
        bats.forEach(bat -> {
            if (bat.isValid()) bat.remove();
        });
        bats.clear();
        nextSound.clear();
        lobby = null;
    }

    private void tick(World world) {
        if (lobby == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        ThemeDefinition.Snowfall snow = lobby.snowfall();
        double decorRadius = lobby.radius() + 8;
        for (Player player : world.getPlayers()) {
            Location eye = player.getLocation();
            if (!anchors.isEmpty()) {
                for (int i = 0; i < 2; i++) {
                    FakeBlock anchor = anchors.get(random.nextInt(anchors.size()));
                    ThemeDefinition.AnchorParticle rule = anchorRule(lobby, anchor);
                    if (rule == null) continue;
                    Location at = new Location(world, anchor.x() + 0.5, anchor.y() + rule.dy(), anchor.z() + 0.5);
                    if (at.distanceSquared(eye) > 24 * 24) continue;
                    player.spawnParticle(rule.particle(), at, 1, rule.spreadXZ(), rule.spreadY(), rule.spreadXZ(),
                            rule.speed());
                }
            }
            if (snow != null && Math.abs(eye.getX() - spawn.getX()) <= decorRadius
                    && Math.abs(eye.getZ() - spawn.getZ()) <= decorRadius) {
                // One packet: the client spreads the flakes in the box (Geyser sends one Bedrock event each).
                double halfHeight = (snow.maxHeight() - snow.minHeight()) / 2;
                Location sky = eye.clone().add(0, snow.minHeight() + halfHeight, 0);
                player.spawnParticle(snow.particle(), sky, snow.count(), snow.radius() / 2, halfHeight / 2,
                        snow.radius() / 2, 0.0);
            }
            if (!lobby.ambientSounds().isEmpty()) {
                long due = nextSound.computeIfAbsent(player.getUniqueId(), id -> now + delayMillis(random));
                if (now >= due) {
                    nextSound.put(player.getUniqueId(), now + delayMillis(random));
                    String sound = lobby.ambientSounds().get(random.nextInt(lobby.ambientSounds().size()));
                    Location from = eye.clone().add(random.nextDouble(-8, 8), random.nextDouble(0, 4),
                            random.nextDouble(-8, 8));
                    player.playSound(from, sound, SoundCategory.AMBIENT, 0.35f, (float) random.nextDouble(0.8, 1.1));
                }
            }
        }
        nextSound.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private static ThemeDefinition.AnchorParticle anchorRule(ThemeDefinition.Lobby lobby, FakeBlock block) {
        for (ThemeDefinition.AnchorParticle rule : lobby.anchorParticles()) {
            if (rule.anchors(block.data())) return rule;
        }
        return null;
    }

    private long delayMillis(ThreadLocalRandom random) {
        return 1000L * random.nextInt(lobby.ambientMinSeconds(), lobby.ambientMaxSeconds() + 1);
    }

    private void keepBats(World world) {
        if (lobby == null || center == null) return;
        bats.removeIf(bat -> !bat.isValid());
        double radius = Math.max(8, lobby.radius() - 12);
        for (Bat bat : bats) {
            Location at = bat.getLocation();
            double dx = at.getX() - center.getX();
            double dz = at.getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius || at.getY() < center.getY() || at.getY() > center.getY() + 22) {
                bat.teleport(randomSky(world, radius * 0.6));
            }
        }
        if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return;
        while (bats.size() < lobby.bats()) {
            bats.add(world.spawn(randomSky(world, radius * 0.6), Bat.class, bat -> {
                bat.setPersistent(false);
                bat.setRemoveWhenFarAway(false);
                bat.setInvulnerable(true);
                bat.setSilent(true);
                bat.setAwake(true);
                bat.getPersistentDataContainer().set(batKey, PersistentDataType.BYTE, (byte) 1);
                LobbyEntityOwnership.mark(plugin, bat, "theme_bat");
            }));
        }
    }

    private Location randomSky(World world, double radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0, Math.PI * 2);
        double distance = random.nextDouble(4, Math.max(5, radius));
        return new Location(world, center.getX() + Math.cos(angle) * distance,
                center.getY() + random.nextDouble(9, 16), center.getZ() + Math.sin(angle) * distance);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Bat bat && bat.getPersistentDataContainer().has(batKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
