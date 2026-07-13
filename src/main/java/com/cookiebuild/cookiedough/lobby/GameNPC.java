package com.cookiebuild.cookiedough.lobby;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.listener.NPCReloadListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;

public class GameNPC {
    private static final String NPC_MARKER_KEY = "game_npc";
    private final String gameName;
    private static NPCReloadListener reloadListener;
    private final CookieDough plugin;
    private final Location location;
    private Zombie npc;
    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();

    public GameNPC(String gameName, Location location, CookieDough plugin) {
        this.gameName = gameName;
        this.plugin = plugin;
        this.location = location;

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
        this.npc = location.getWorld().spawn(location, Zombie.class);
        configureNpc(this.npc);
    }

    private void configureNpc(Zombie zombie) {
        zombie.teleport(location);
        zombie.setBaby(false);
        zombie.setAI(false);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setInvulnerable(true);
        zombie.setPersistent(true);
        zombie.getPersistentDataContainer().set(markerKey(), PersistentDataType.STRING, gameName);
        // Keep the legacy per-game marker until all persisted lobby data has been
        // through one reconciliation cycle.
        zombie.getPersistentDataContainer().set(legacyMarkerKey(), PersistentDataType.BYTE, (byte) 1);
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        updateNPCName();
    }

    public void reconcileNpc(Chunk chunk) {
        if (!isConfiguredChunk(chunk)) {
            return;
        }

        List<Zombie> markedNpcs = new ArrayList<>();
        List<NpcReconciliation.Candidate> candidates = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Zombie zombie) || !matches(zombie)) {
                continue;
            }
            markedNpcs.add(zombie);
            candidates.add(new NpcReconciliation.Candidate(zombie.getUniqueId(),
                    npc != null && npc.getUniqueId().equals(zombie.getUniqueId()),
                    zombie.getLocation().distanceSquared(location)));
        }

        UUID canonicalId = NpcReconciliation.selectCanonical(candidates);
        if (canonicalId == null) {
            spawnNPC();
        } else {
            this.npc = markedNpcs.stream()
                    .filter(candidate -> candidate.getUniqueId().equals(canonicalId))
                    .findFirst()
                    .orElseThrow();
            configureNpc(this.npc);
        }

        int removed = 0;
        for (Zombie candidate : markedNpcs) {
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
        if (!(entity instanceof Zombie zombie)) {
            return false;
        }
        String marker = zombie.getPersistentDataContainer().get(markerKey(), PersistentDataType.STRING);
        return gameName.equalsIgnoreCase(marker)
                || zombie.getPersistentDataContainer().has(legacyMarkerKey(), PersistentDataType.BYTE);
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(plugin, NPC_MARKER_KEY);
    }

    private NamespacedKey legacyMarkerKey() {
        return new NamespacedKey(plugin, gameName.toLowerCase(Locale.ROOT));
    }

    private void startNameRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isValid()) {
                    reconcileNpc(location.getChunk());
                } else {
                    updateNPCName();
                }
            }
        }.runTaskTimer(plugin, 0, 20); // Update every second
    }

    private void updateNPCName() {
        GameStatus game = GameManager.getGameByName(gameName);
        if (game != null) {
            int playerCount = game.getPlayerCount();
            int maxPlayers = game.getCapacity();
            String state = game.getState().toString();

            String nameFormat = ChatColor.GOLD + "" + ChatColor.BOLD + "%s " +
                    ChatColor.RESET + ChatColor.AQUA + "[" +
                    ChatColor.YELLOW + "%d" + ChatColor.GOLD + "/" + ChatColor.YELLOW + "%d" +
                    ChatColor.AQUA + "] " +
                    ChatColor.GREEN + "%s";

            String customName = String.format(nameFormat, gameName, playerCount, maxPlayers, state);
            npc.setCustomName(customName);
            npc.setCustomNameVisible(true);
        } else {
            npc.setCustomName(ChatColor.RED + gameName + " (Unavailable)");
            npc.setCustomNameVisible(true);
        }
    }

    public void interactWithPlayer(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastInteraction.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 500) {
            return;
        }
        FunnelTelemetry.record(player, FunnelTelemetry.Event.NPC_SELECTED, "game=" + gameName);
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        GameStatus game = GameManager.getGameByName(gameName);

        if (cookiePlayer == null) {
            player.sendMessage(ChatColor.YELLOW + "Your profile is still loading. Please try again.");
            return;
        }

        if (game != null && game.getState() == GameState.OPEN) {
            if (game.addPlayerToAvailableTeam(cookiePlayer)) {
                player.sendMessage(ChatColor.GREEN + "You've joined a " + gameName + " game!");
            } else {
                player.sendMessage(ChatColor.RED + "No available " + gameName + " games. Please wait.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Error: Game not found.");
        }
    }

    public Zombie getNPC() {
        return npc;
    }

    public String getGameName() {
        return gameName;
    }

    public Location getLocation() {
        return location;
    }
}
