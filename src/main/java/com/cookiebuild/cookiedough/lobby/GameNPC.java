package com.cookiebuild.cookiedough.lobby;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.listener.NPCReloadListener;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class GameNPC {
    private static final String NPC_MARKER_KEY = "game_npc";
    private final String gameName;
    private static NPCReloadListener reloadListener;
    private final CookieDough plugin;
    private final Location location;
    private final GamePresentation presentation;
    private Mob npc;
    private BukkitTask nameRefreshTask;
    private long lastQueueSignalAtMillis = -1L;
    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();

    public GameNPC(String gameName, Location location, CookieDough plugin) {
        this.gameName = gameName;
        this.plugin = plugin;
        this.location = location;
        this.presentation = GamePresentation.forGame(gameName);

        if (reloadListener != null) {
            reloadListener.registerNPC(this);
        }

        reconcileNpc(location.getChunk());
        startNameRefreshTask();
    }

    public static void setReloadListener(NPCReloadListener listener) {
        reloadListener = listener;
    }

    private void spawnNPC() {
        Entity spawned = location.getWorld().spawnEntity(location, presentation.npcType());
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            throw new IllegalStateException("Configured lobby selector is not a mob: " + presentation.npcType());
        }
        this.npc = mob;
        configureNpc(this.npc);
    }

    private void configureNpc(Mob mob) {
        mob.teleport(location);
        mob.setAI(false);
        mob.setSilent(true);
        mob.setCanPickupItems(false);
        mob.setRemoveWhenFarAway(false);
        mob.setInvulnerable(true);
        mob.setPersistent(true);
        mob.setCollidable(false);
        // Persistent activities are the principal long-form destinations in the
        // lobby. A vanilla glow is translated by Geyser and makes the selector
        // stand out on both Java and Bedrock without edition-specific entity
        // models or fragile map walls.
        mob.setGlowing(presentation.persistent());
        mob.setFireTicks(0);
        mob.getPersistentDataContainer().set(markerKey(), PersistentDataType.STRING, gameName);
        LobbyEntityOwnership.mark(plugin, mob, "game_npc");
        // Keep the legacy per-game marker until all persisted lobby data has been
        // through one reconciliation cycle.
        mob.getPersistentDataContainer().set(legacyMarkerKey(), PersistentDataType.BYTE, (byte) 1);
        if (mob.getEquipment() != null) {
            mob.getEquipment().clear();
        }
        if (mob instanceof Slime slime) {
            slime.setSize(2);
        } else if (mob instanceof Sheep sheep) {
            sheep.setColor(DyeColor.LIME);
            sheep.setSheared(false);
        } else if (mob instanceof Villager villager) {
            villager.setProfession(Villager.Profession.MASON);
        }
        updateNPCName();
    }

    public void reconcileNpc(Chunk chunk) {
        if (!isConfiguredChunk(chunk)) {
            return;
        }

        List<Entity> markedNpcs = new ArrayList<>();
        List<Mob> compatibleNpcs = new ArrayList<>();
        List<NpcReconciliation.Candidate> candidates = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (!matches(entity)) {
                continue;
            }
            markedNpcs.add(entity);
            if (entity instanceof Mob mob && entity.getType() == presentation.npcType()) {
                compatibleNpcs.add(mob);
                candidates.add(new NpcReconciliation.Candidate(mob.getUniqueId(),
                        npc != null && npc.getUniqueId().equals(mob.getUniqueId()),
                        mob.getLocation().distanceSquared(location)));
            }
        }

        UUID canonicalId = NpcReconciliation.selectCanonical(candidates);
        if (canonicalId == null) {
            spawnNPC();
        } else {
            this.npc = compatibleNpcs.stream()
                    .filter(candidate -> candidate.getUniqueId().equals(canonicalId))
                    .findFirst()
                    .orElseThrow();
            configureNpc(this.npc);
        }

        int removed = 0;
        for (Entity candidate : markedNpcs) {
            if (!candidate.getUniqueId().equals(this.npc.getUniqueId())) {
                candidate.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed + " duplicate " + gameName + " lobby NPC(s)");
        }
    }

    private boolean isConfiguredChunk(Chunk chunk) {
        return location.getWorld() != null && chunk.getWorld().getUID().equals(location.getWorld().getUID())
                && chunk.getX() == location.getBlockX() >> 4
                && chunk.getZ() == location.getBlockZ() >> 4;
    }

    public boolean matches(Entity entity) {
        String marker = entity.getPersistentDataContainer().get(markerKey(), PersistentDataType.STRING);
        return gameName.equalsIgnoreCase(marker)
                || entity.getPersistentDataContainer().has(legacyMarkerKey(), PersistentDataType.BYTE);
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(plugin, NPC_MARKER_KEY);
    }

    private NamespacedKey legacyMarkerKey() {
        return new NamespacedKey(plugin, gameName.toLowerCase(Locale.ROOT));
    }

    private void startNameRefreshTask() {
        nameRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isValid()) {
                    reconcileNpc(location.getChunk());
                } else {
                    npc.setFireTicks(0);
                    updateNPCName();
                }
            }
        }.runTaskTimer(plugin, 0, 20); // Update every second
    }

    private void updateNPCName() {
        if (presentation.persistent()) {
            npc.customName(LobbyDisplayText.persistentActivityNpc(
                    presentation.gameName(),
                    LobbyModePlayerCounter.forPersistentActivity(gameName)));
            npc.setCustomNameVisible(true);
            return;
        }
        GameStatus game = GameManager.getGameByName(gameName);
        int totalPlayerCount = LobbyModePlayerCounter.forMinigame(gameName);
        if (game != null) {
            boolean queueOpen = game.isAdmissionsOpen();
            int queuePlayerCount = game.getQueuePlayerCount();
            npc.customName(LobbyDisplayText.gameNpc(
                    presentation.gameName(),
                    totalPlayerCount,
                    queuePlayerCount,
                    game.getCapacity(),
                    queueOpen,
                    game.getState(),
                    queueOpen ? game.getCountdownSeconds() : 0));
            npc.setCustomNameVisible(true);
            playLocalQueueSignal(queueOpen, queuePlayerCount);
        } else {
            npc.customName(LobbyDisplayText.unavailableGameNpc(
                    presentation.gameName(), totalPlayerCount));
            npc.setCustomNameVisible(true);
        }
    }

    private void playLocalQueueSignal(boolean queueOpen, int queuePlayerCount) {
        long nowMillis = System.currentTimeMillis();
        if (!QueueSignalPolicy.shouldPlay(queueOpen, queuePlayerCount, nowMillis, lastQueueSignalAtMillis)
                || location.getWorld() == null) {
            return;
        }
        boolean played = false;
        for (Player player : location.getWorld().getPlayers()) {
            if (!QueueSignalPolicy.isNearby(player.getLocation().distanceSquared(location))) {
                continue;
            }
            player.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.4f);
            played = true;
        }
        if (played) {
            lastQueueSignalAtMillis = nowMillis;
        }
    }

    public void shutdown() {
        if (nameRefreshTask != null) {
            nameRefreshTask.cancel();
            nameRefreshTask = null;
        }
        lastInteraction.clear();
    }

    public void interactWithPlayer(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastInteraction.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 500) {
            return;
        }
        FunnelTelemetry.record(player, FunnelTelemetry.Event.NPC_SELECTED, "game=" + gameName);
        LobbyManager lobbyManager = LobbyManager.getInstance();
        if (lobbyManager == null) {
            player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                    "lobby.game.unavailable", player.locale(), presentation.displayName(player.locale())));
            return;
        }
        lobbyManager.requestSelectedActivity(player, gameName);
    }

    public Mob getNPC() {
        return npc;
    }

    public String getGameName() {
        return gameName;
    }

    public Location getLocation() {
        return location;
    }
}

/** Pure cadence gate: the Bukkit-facing NPC only performs the targeted playback. */
final class QueueSignalPolicy {
    static final long INTERVAL_MILLIS = 9_000L;
    static final double RADIUS_SQUARED = 36.0;

    private QueueSignalPolicy() {
    }

    static boolean shouldPlay(boolean queueOpen, int queuePlayerCount, long nowMillis, long lastPlayedAtMillis) {
        if (!queueOpen || queuePlayerCount <= 0) {
            return false;
        }
        if (lastPlayedAtMillis < 0L) {
            return true;
        }
        return nowMillis >= lastPlayedAtMillis && nowMillis - lastPlayedAtMillis >= INTERVAL_MILLIS;
    }

    static boolean isNearby(double distanceSquared) {
        return distanceSquared >= 0.0 && distanceSquared <= RADIUS_SQUARED;
    }
}
