package com.cookiebuild.cookiedough.theme.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.theme.decor.FakeBlock;
import com.cookiebuild.cookiedough.theme.decor.FakeBlockRenderer;
import com.cookiebuild.cookiedough.theme.decor.FakeBlockSource;
import com.cookiebuild.cookiedough.ui.PlatformText;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * Hidden collectibles: each spot is a real block of the decor that players who
 * have not found it yet see as a pumpkin. A click (or a tap on Bedrock) on it
 * records the find, pays the reward and shows that player the real block again.
 */
public final class HuntLayer implements FakeBlockSource, Listener {
    private final JavaPlugin plugin;
    private final FakeBlockRenderer renderer;
    private final CollectibleRepository repository;
    private final HuntRewards rewards;
    private final Map<UUID, Set<String>> found = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private HuntPlan plan;
    private String messages;
    private UUID worldId;
    private final Map<Long, HuntPlan.Spot> spotsByPosition = new HashMap<>();
    private final Map<Long, FakeBlock> fakesByPosition = new HashMap<>();
    private final Map<Long, List<FakeBlock>> fakesByChunk = new HashMap<>();
    private volatile boolean storageAvailable = true;

    public HuntLayer(JavaPlugin plugin, FakeBlockRenderer renderer, CollectibleRepository repository,
            HuntRewards rewards) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.repository = repository;
        this.rewards = rewards;
    }

    /** Validates the spots against the real world and starts showing them. */
    public void activate(HuntPlan plan, String messagePrefix) {
        deactivate();
        World world = Bukkit.getWorld(plan.world());
        if (world == null) {
            plugin.getLogger().warning("Theme hunt " + plan.id() + ": world " + plan.world() + " is not loaded");
            return;
        }
        List<HuntPlan.Spot> valid = new ArrayList<>();
        for (HuntPlan.Spot spot : plan.spots()) {
            Block real = world.getBlockAt(spot.x(), spot.y(), spot.z());
            if (!real.getType().name().equals(spot.expected())) {
                plugin.getLogger().warning("Theme hunt " + plan.id() + ": spot " + spot.id() + " expected "
                        + spot.expected() + " but found " + real.getType() + "; skipped");
                continue;
            }
            valid.add(spot);
        }
        this.plan = new HuntPlan(plan.id(), plan.world(), valid, plan.coinsPerFind(), plan.completionCoins(),
                plan.rewardCosmetic());
        this.messages = messagePrefix;
        this.worldId = world.getUID();
        for (HuntPlan.Spot spot : valid) {
            FakeBlock fake = new FakeBlock(spot.x(), spot.y(), spot.z(), spot.block(), spot.expected());
            long position = fake.packedPosition();
            spotsByPosition.put(position, spot);
            fakesByPosition.put(position, fake);
            fakesByChunk.computeIfAbsent(FakeBlock.chunkKey(fake.chunkX(), fake.chunkZ()), key -> new ArrayList<>())
                    .add(fake);
        }
        plugin.getLogger().info("Theme hunt " + plan.id() + ": " + valid.size() + "/" + plan.spots().size()
                + " spots ready");
        for (Player player : Bukkit.getOnlinePlayers()) load(player);
    }

    public void deactivate() {
        if (plan == null) return;
        World world = Bukkit.getWorld(worldId);
        List<FakeBlock> shown = List.copyOf(fakesByPosition.values());
        plan = null;
        spotsByPosition.clear();
        fakesByPosition.clear();
        fakesByChunk.clear();
        found.clear();
        if (world != null) renderer.restore(world, shown);
    }

    public boolean active() {
        return plan != null;
    }

    /** Found and total for the scoreboard, once the player's progress is loaded. */
    public Optional<int[]> progress(Player player) {
        HuntPlan current = plan;
        if (current == null || current.spots().isEmpty()) return Optional.empty();
        Set<String> mine = found.get(player.getUniqueId());
        if (mine == null) return Optional.empty();
        return Optional.of(new int[] {Math.min(mine.size(), current.spots().size()), current.spots().size()});
    }

    @Override
    public void collect(Player viewer, World world, int chunkX, int chunkZ, Consumer<FakeBlock> out) {
        if (plan == null || !world.getUID().equals(worldId)) return;
        Set<String> mine = found.get(viewer.getUniqueId());
        if (mine == null) return;
        List<FakeBlock> fakes = fakesByChunk.get(FakeBlock.chunkKey(chunkX, chunkZ));
        if (fakes == null) return;
        for (FakeBlock fake : fakes) {
            HuntPlan.Spot spot = spotsByPosition.get(fake.packedPosition());
            if (spot != null && !mine.contains(spot.id())) out.accept(fake);
        }
    }

    @Override
    public FakeBlock at(Player viewer, World world, int x, int y, int z) {
        if (plan == null || !world.getUID().equals(worldId)) return null;
        long position = FakeBlock.pack(x, y, z);
        HuntPlan.Spot spot = spotsByPosition.get(position);
        Set<String> mine = found.get(viewer.getUniqueId());
        if (spot == null || mine == null || mine.contains(spot.id())) return null;
        return fakesByPosition.get(position);
    }

    private void load(Player player) {
        HuntPlan current = plan;
        if (current == null) return;
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> loaded;
            try {
                loaded = storageAvailable ? repository.found(playerId, current.id()) : Set.of();
            } catch (RuntimeException error) {
                storageAvailable = false;
                plugin.getLogger().log(Level.WARNING, "Theme hunt progress is unavailable; finds stay in memory", error);
                loaded = Set.of();
            }
            Set<String> progress = ConcurrentHashMap.newKeySet();
            progress.addAll(loaded);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plan != current || !player.isOnline()) return;
                found.put(playerId, progress);
                if (player.getWorld().getUID().equals(worldId)) renderer.refresh(player);
            });
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        found.remove(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (plan == null || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !block.getWorld().getUID().equals(worldId)) return;
        Player player = event.getPlayer();
        FakeBlock fake = at(player, block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (fake == null) return;
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 400) return;
        collectSpot(player, spotsByPosition.get(fake.packedPosition()), fake);
    }

    private void collectSpot(Player player, HuntPlan.Spot spot, FakeBlock fake) {
        HuntPlan current = plan;
        Set<String> mine = found.get(player.getUniqueId());
        if (current == null || spot == null || mine == null) return;
        HuntPlan.Outcome outcome = HuntPlan.outcome(mine, spot.id(), current.spots().size());
        if (!outcome.newFind()) return;
        mine.add(spot.id());
        renderer.suppress(player, fake);
        celebrate(player, fake, outcome);
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean recorded = !storageAvailable || repository.record(playerId, current.id(), spot.id());
                if (recorded && storageAvailable && current.coinsPerFind() > 0) {
                    rewards.coins(playerId, current.id() + ":" + spot.id(), current.coinsPerFind());
                }
                if (outcome.completed() && storageAvailable) {
                    if (current.completionCoins() > 0) {
                        rewards.coins(playerId, current.id() + ":complete", current.completionCoins());
                    }
                    if (current.rewardCosmetic() != null && !current.rewardCosmetic().isBlank()) {
                        rewards.cosmetic(playerId, current.rewardCosmetic(), current.id() + ":pumpkins");
                    }
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Could not save a hunt find for " + playerId, error);
            }
        });
    }

    private void celebrate(Player player, FakeBlock fake, HuntPlan.Outcome outcome) {
        Location center = new Location(player.getWorld(), fake.x() + 0.5, fake.y() + 0.5, fake.z() + 0.5);
        player.spawnParticle(Particle.ITEM, center, 14, 0.3, 0.3, 0.3, 0.08, new ItemStack(Material.JACK_O_LANTERN));
        player.spawnParticle(Particle.SOUL, center, 8, 0.4, 0.4, 0.4, 0.02);
        player.spawnParticle(Particle.HAPPY_VILLAGER, center, 6, 0.4, 0.4, 0.4, 0.0);
        player.playSound(center, "block.note_block.chime", SoundCategory.PLAYERS, 0.8f, 1.6f);
        player.playSound(center, "entity.witch.celebrate", SoundCategory.PLAYERS, 0.35f, 1.4f);
        int coins = plan == null ? 0 : plan.coinsPerFind();
        player.sendMessage(LocaleManager.getMessage(messages + ".hunt.found", player.locale(),
                outcome.found(), outcome.total(), coins));
        if (outcome.completed()) {
            player.playSound(player.getLocation(), "ui.toast.challenge_complete", SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.showTitle(Title.title(
                    Component.text(LocaleManager.getMessage(messages + ".hunt.complete.title", player.locale())),
                    Component.text(LocaleManager.getMessage(messages + ".hunt.complete.subtitle", player.locale()))));
            player.sendMessage(PlatformText.message(player, messages + ".hunt.complete",
                    outcome.total(), plan == null ? 0 : plan.completionCoins()));
        }
    }
}
