package com.cookiebuild.cookiedough.theme.decor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.math.Position;

/**
 * Sends client-side blocks. Nothing here ever changes the server's world:
 * every fake is re-sent when a chunk reaches a player, re-checked against the
 * real block at that moment, and replaced by the real block on removal.
 */
public final class FakeBlockRenderer implements Listener {
    private final Plugin plugin;
    private final List<FakeBlockSource> sources = new ArrayList<>();
    private final Map<String, BlockData> parsed = new ConcurrentHashMap<>();
    /** Positions a player must see as real again (collected pumpkin, block under their feet). */
    private final Map<UUID, Set<Long>> suppressed = new HashMap<>();
    private final Set<UUID> pendingRefresh = new HashSet<>();

    public FakeBlockRenderer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void addSource(FakeBlockSource source) {
        sources.add(source);
    }

    /** Re-sends every fake block of a world to the players in it (after a layer changed). */
    public void refresh(World world) {
        for (Player player : world.getPlayers()) refresh(player);
    }

    public void refresh(Player player) {
        World world = player.getWorld();
        for (long key : List.copyOf(player.getSentChunkKeys())) {
            sendChunk(player, world, (int) key, (int) (key >> 32));
        }
    }

    /** Shows the real blocks again at these positions to every player of the world. */
    public void restore(World world, Collection<FakeBlock> blocks) {
        if (blocks.isEmpty()) return;
        for (Player player : world.getPlayers()) restore(player, blocks);
    }

    public void restore(Player player, Collection<FakeBlock> blocks) {
        World world = player.getWorld();
        Map<Position, BlockData> real = new HashMap<>();
        for (FakeBlock block : blocks) {
            if (!world.isChunkLoaded(block.chunkX(), block.chunkZ())) continue;
            if (!player.isChunkSent(FakeBlock.chunkKey(block.chunkX(), block.chunkZ()))) continue;
            real.put(Position.block(block.x(), block.y(), block.z()),
                    world.getBlockAt(block.x(), block.y(), block.z()).getBlockData());
        }
        if (!real.isEmpty()) player.sendMultiBlockChange(real);
    }

    /** Hides one fake from a player for the rest of their stay in the world. */
    public void suppress(Player player, FakeBlock block) {
        suppressed.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>()).add(block.packedPosition());
        restore(player, List.of(block));
    }

    public void sendChunk(Player player, World world, int chunkX, int chunkZ) {
        if (sources.isEmpty() || !world.isChunkLoaded(chunkX, chunkZ)) return;
        List<FakeBlock> blocks = new ArrayList<>();
        for (FakeBlockSource source : sources) {
            try {
                source.collect(player, world, chunkX, chunkZ, blocks::add);
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Theme decor source failed", error);
            }
        }
        if (blocks.isEmpty()) return;
        Set<Long> hidden = suppressed.getOrDefault(player.getUniqueId(), Set.of());
        Map<Position, BlockData> changes = new HashMap<>();
        for (FakeBlock block : blocks) {
            if (hidden.contains(block.packedPosition())) continue;
            Block real = world.getBlockAt(block.x(), block.y(), block.z());
            if (!matches(real, block.expected())) continue;
            BlockData data = parse(block.data());
            if (data != null) changes.put(Position.block(block.x(), block.y(), block.z()), data);
        }
        if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
    }

    static boolean matches(Block real, String expected) {
        String type = real.getType().name();
        return FakeBlock.AIR.equals(expected) ? BlockView.isAir(type) : type.equals(expected);
    }

    /** Parses block data once; a state that the target block lacks falls back to its default state. */
    public BlockData parse(String data) {
        BlockData cached = parsed.get(data);
        if (cached != null) return cached;
        BlockData result;
        try {
            result = Bukkit.createBlockData(data);
        } catch (IllegalArgumentException error) {
            int states = data.indexOf('[');
            try {
                result = states > 0 ? Bukkit.createBlockData(data.substring(0, states)) : null;
            } catch (IllegalArgumentException again) {
                result = null;
            }
            if (result == null) plugin.getLogger().warning("Theme decor ignores invalid block data " + data);
        }
        if (result != null) parsed.put(data, result);
        return result;
    }

    public FakeBlock at(Player player, World world, int x, int y, int z) {
        Set<Long> hidden = suppressed.getOrDefault(player.getUniqueId(), Set.of());
        if (hidden.contains(FakeBlock.pack(x, y, z))) return null;
        for (FakeBlockSource source : sources) {
            FakeBlock block = source.at(player, world, x, y, z);
            if (block != null) return block;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        // Next tick: the chunk packet itself must reach the client first.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getWorld().equals(world)
                    && player.isChunkSent(FakeBlock.chunkKey(chunkX, chunkZ))) {
                sendChunk(player, world, chunkX, chunkZ);
            }
        });
    }

    /**
     * The server answers any click on a block with the real block state, which
     * would erase a fake one: show the fakes of that chunk again a tick later.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        Block neighbor = block.getRelative(event.getBlockFace());
        if (at(player, block.getWorld(), block.getX(), block.getY(), block.getZ()) == null
                && at(player, neighbor.getWorld(), neighbor.getX(), neighbor.getY(), neighbor.getZ()) == null) {
            return;
        }
        if (!pendingRefresh.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRefresh.remove(player.getUniqueId());
            if (player.isOnline() && player.getWorld().equals(block.getWorld())) {
                sendChunk(player, block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
                if (neighbor.getX() >> 4 != block.getX() >> 4 || neighbor.getZ() >> 4 != block.getZ() >> 4) {
                    sendChunk(player, neighbor.getWorld(), neighbor.getX() >> 4, neighbor.getZ() >> 4);
                }
            }
        }, 2L);
    }

    /**
     * A fake block is never ground: a player standing on one would float for
     * the server and be kicked for flying, so the real block comes back under
     * their feet.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getBlockY() == from.getBlockY() && to.getBlockX() == from.getBlockX()
                && to.getBlockZ() == from.getBlockZ() && Math.abs(to.getY() - from.getY()) < 0.01) {
            return;
        }
        Player player = event.getPlayer();
        World world = to.getWorld();
        int y = (int) Math.floor(to.getY() - 0.05);
        for (double dx : new double[] {-0.3, 0.3}) {
            for (double dz : new double[] {-0.3, 0.3}) {
                int x = (int) Math.floor(to.getX() + dx);
                int z = (int) Math.floor(to.getZ() + dz);
                FakeBlock fake = at(player, world, x, y, z);
                if (fake == null || !FakeBlock.AIR.equals(fake.expected())) continue;
                BlockData data = parse(fake.data());
                if (data != null && data.getMaterial().isCollidable()) suppress(player, fake);
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        suppressed.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        suppressed.remove(event.getPlayer().getUniqueId());
        pendingRefresh.remove(event.getPlayer().getUniqueId());
    }
}
