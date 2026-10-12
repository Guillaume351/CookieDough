package com.cookiebuild.cookiedough.theme;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.listener.WorldPolicy;
import com.cookiebuild.cookiedough.theme.decor.BlockView;
import com.cookiebuild.cookiedough.theme.decor.DecorPlanner;
import com.cookiebuild.cookiedough.theme.decor.FakeBlock;
import com.cookiebuild.cookiedough.theme.decor.FakeBlockRenderer;
import com.cookiebuild.cookiedough.theme.decor.FakeBlockSource;
import com.cookiebuild.cookiedough.theme.decor.PaletteLayer;
import com.cookiebuild.cookiedough.theme.decor.StaticDecorLayer;
import com.cookiebuild.cookiedough.theme.hunt.CollectibleRepository;
import com.cookiebuild.cookiedough.theme.hunt.HuntLayer;
import com.cookiebuild.cookiedough.theme.hunt.HuntPlan;
import com.cookiebuild.cookiedough.theme.hunt.HuntRewards;
import com.cookiebuild.cookiedough.theme.music.MusicService;
import com.cookiebuild.cookiedough.theme.music.Song;
import com.cookiebuild.cookiedough.theme.music.SongLibrary;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Turns the active theme on and off. The theme follows its dates
 * ({@code themes.mode: auto} in config.yml) unless an operator forces one or
 * switches themes off with {@code /theme set}; it is re-evaluated every 30 s.
 */
public final class ThemeController implements Listener {
    public static final String MODE_PATH = "themes.mode";

    private final JavaPlugin plugin;
    private final ThemeService service;
    private final FakeBlockRenderer renderer;
    private final StaticDecorLayer lobbyDecor = new StaticDecorLayer();
    private final HuntLayer hunt;
    private final MusicService music;
    private final LobbyAmbience ambience;
    private final ThemeTexts texts;
    private PaletteLayer palette;
    private List<ThemeDefinition> themes = List.of();
    private ThemeDefinition current;
    private int generation;

    public ThemeController(JavaPlugin plugin, ThemeService service, CollectibleRepository repository,
            HuntRewards rewards) {
        this.plugin = plugin;
        this.service = service;
        this.renderer = new FakeBlockRenderer(plugin);
        this.hunt = new HuntLayer(plugin, renderer, repository, rewards);
        this.music = new MusicService(plugin);
        this.ambience = new LobbyAmbience(plugin);
        this.texts = new ThemeTexts(plugin);
        renderer.addSource(lobbyDecor);
        renderer.addSource(hunt);
        renderer.addSource(new FakeBlockSource() {
            @Override
            public void collect(Player viewer, World world, int chunkX, int chunkZ, Consumer<FakeBlock> out) {
                if (palette != null) palette.collect(viewer, world, chunkX, chunkZ, out);
            }

            @Override
            public FakeBlock at(Player viewer, World world, int x, int y, int z) {
                return palette == null ? null : palette.at(viewer, world, x, y, z);
            }
        });
    }

    public void start() {
        var manager = plugin.getServer().getPluginManager();
        manager.registerEvents(renderer, plugin);
        manager.registerEvents(hunt, plugin);
        manager.registerEvents(music, plugin);
        manager.registerEvents(ambience, plugin);
        manager.registerEvents(texts, plugin);
        manager.registerEvents(this, plugin);
        themes = loadThemes();
        Bukkit.getScheduler().runTaskTimer(plugin, this::evaluate, 20L, 20L * 30L);
    }

    public void stop() {
        deactivate();
    }

    public List<ThemeDefinition> themes() {
        return themes;
    }

    public Optional<ThemeDefinition> current() {
        return Optional.ofNullable(current);
    }

    public String mode() {
        return plugin.getConfig().getString(MODE_PATH, ThemeSchedule.AUTO);
    }

    public MusicService music() {
        return music;
    }

    public int decorSize() {
        World world = current == null || current.lobby() == null ? null : Bukkit.getWorld(current.lobby().world());
        return world == null ? 0 : lobbyDecor.size(world);
    }

    /** Persists the operator's choice and applies it right away. */
    public boolean setMode(String mode) {
        if (!ThemeSchedule.isValidMode(mode, themes)) return false;
        plugin.getConfig().set(MODE_PATH, mode.trim().toLowerCase(Locale.ROOT));
        plugin.saveConfig();
        evaluate();
        return true;
    }

    /** Reloads theme files and restarts the active theme from scratch. */
    public void reload() {
        deactivate();
        themes = loadThemes();
        evaluate();
    }

    public void evaluate() {
        Optional<ThemeDefinition> wanted = ThemeSchedule.resolve(mode(), themes, Instant.now());
        if (wanted.map(ThemeDefinition::id).equals(Optional.ofNullable(current).map(ThemeDefinition::id))) return;
        deactivate();
        wanted.ifPresent(this::activate);
    }

    private void activate(ThemeDefinition theme) {
        current = theme;
        int run = ++generation;
        service.setActive(theme);
        plugin.getLogger().info("Seasonal theme " + theme.id() + " is on until " + theme.end());
        if (theme.games() != null && !theme.games().palette().isEmpty()) {
            Map<Material, String> swaps = new HashMap<>();
            theme.games().palette().forEach((from, to) -> {
                Material material = Material.matchMaterial(from);
                if (material != null && material.isBlock()) swaps.put(material, to);
            });
            palette = new PaletteLayer(swaps, world -> appliesToGame(theme, world)
                    && theme.games().appliesPalette(world.getKey().getNamespace()));
            plugin.getServer().getPluginManager().registerEvents(palette, plugin);
        }
        if (theme.hunt() != null) hunt.activate(theme.hunt(), theme.messagePrefix());
        if (theme.music() != null) {
            List<Song> songs = SongLibrary.load(plugin, theme.id(), theme.music().playlist());
            music.activate(theme.music(), songs, theme.messagePrefix());
        }
        texts.activate(theme, this::scoreboardLine);
        if (theme.lobby() != null) planLobby(theme, run);
        for (Player player : Bukkit.getOnlinePlayers()) applyTime(player);
        for (World world : Bukkit.getWorlds()) {
            if (palette != null && appliesToGame(theme, world)) renderer.refresh(world);
        }
    }

    private void deactivate() {
        ThemeDefinition previous = current;
        if (previous == null) return;
        generation++;
        current = null;
        service.setActive(null);
        texts.deactivate();
        music.deactivate();
        ambience.deactivate();
        hunt.deactivate();
        for (World world : Bukkit.getWorlds()) {
            renderer.restore(world, lobbyDecor.clear(world));
            if (palette != null) renderer.restore(world, palette.clear(world));
        }
        if (palette != null) {
            org.bukkit.event.HandlerList.unregisterAll(palette);
            palette = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) player.resetPlayerTime();
        plugin.getLogger().info("Seasonal theme " + previous.id() + " is off");
    }

    private Component scoreboardLine(Player player) {
        ThemeDefinition theme = current;
        if (theme == null) return null;
        return hunt.progress(player).map(progress -> {
            boolean done = progress[0] >= progress[1];
            return Component.text(LocaleManager.getMessage(theme.messagePrefix() + ".scoreboard.hunt",
                    player.locale(), progress[0], progress[1]), done ? NamedTextColor.GREEN : NamedTextColor.GOLD);
        }).orElse(null);
    }

    /** Plans the lobby decor from chunk snapshots off the main thread, then shows it. */
    private void planLobby(ThemeDefinition theme, int run) {
        ThemeDefinition.Lobby lobby = theme.lobby();
        World world = Bukkit.getWorld(lobby.world());
        if (world == null) {
            plugin.getLogger().warning("Theme " + theme.id() + ": lobby world " + lobby.world() + " is not loaded");
            return;
        }
        Location spawn = world.getSpawnLocation();
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();
        int minY = Math.max(world.getMinHeight(), lobby.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, lobby.maxY());
        DecorPlanner.Area area = new DecorPlanner.Area(centerX - lobby.radius(), minY, centerZ - lobby.radius(),
                centerX + lobby.radius(), maxY, centerZ + lobby.radius(), centerX, centerZ);
        List<CompletableFuture<org.bukkit.Chunk>> loads = new ArrayList<>();
        for (int chunkX = (area.minX() - 1) >> 4; chunkX <= (area.maxX() + 1) >> 4; chunkX++) {
            for (int chunkZ = (area.minZ() - 1) >> 4; chunkZ <= (area.maxZ() + 1) >> 4; chunkZ++) {
                loads.add(world.getChunkAtAsync(chunkX, chunkZ, false));
            }
        }
        Set<Long> excluded = new HashSet<>();
        if (theme.hunt() != null) {
            for (HuntPlan.Spot spot : theme.hunt().spots()) excluded.add(FakeBlock.pack(spot.x(), spot.y(), spot.z()));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (run != generation) return;
                    Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
                    for (CompletableFuture<org.bukkit.Chunk> load : loads) {
                        org.bukkit.Chunk chunk = load.getNow(null);
                        if (chunk != null) {
                            snapshots.put(FakeBlock.chunkKey(chunk.getX(), chunk.getZ()),
                                    chunk.getChunkSnapshot(false, false, false));
                        }
                    }
                    int worldMin = world.getMinHeight();
                    int worldMax = world.getMaxHeight();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        List<FakeBlock> planned;
                        try {
                            BlockView view = new SnapshotView(snapshots, worldMin, worldMax);
                            DecorPlanner planner = new DecorPlanner(lobby.decor(), ThemeController::fullCube,
                                    theme.id().hashCode());
                            planned = planner.plan(view, area, excluded);
                        } catch (RuntimeException failure) {
                            plugin.getLogger().log(Level.WARNING, "Theme " + theme.id() + ": lobby decor failed", failure);
                            return;
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (run != generation) return;
                            lobbyDecor.set(world, planned);
                            renderer.refresh(world);
                            ambience.activate(lobby, world, planned);
                            plugin.getLogger().info("Theme " + theme.id() + ": " + planned.size()
                                    + " client-side decorations in " + lobby.world());
                        });
                    });
                }));
    }

    static boolean fullCube(String type) {
        if (BlockView.isAir(type)) return false;
        Material material = Material.getMaterial(type);
        return material != null && material.isOccluding();
    }

    private static boolean appliesToGame(ThemeDefinition theme, World world) {
        if (theme.games() == null || world == null) return false;
        if (WorldPolicy.isPersistent(world.getName()) || "lobby".equalsIgnoreCase(world.getName())) return false;
        return theme.games().applies(world.getKey().getNamespace());
    }

    private void applyTime(Player player) {
        ThemeDefinition theme = current;
        if (theme == null) {
            player.resetPlayerTime();
            return;
        }
        World world = player.getWorld();
        if (theme.lobby() != null && world.getName().equalsIgnoreCase(theme.lobby().world())
                && theme.lobby().playerTime() >= 0) {
            player.setPlayerTime(theme.lobby().playerTime(), false);
        } else if (appliesToGame(theme, world) && theme.games().playerTime() >= 0) {
            player.setPlayerTime(theme.games().playerTime(), false);
        } else {
            player.resetPlayerTime();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            applyTime(player);
            texts.showTab(player);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applyTime(event.getPlayer());
    }

    private List<ThemeDefinition> loadThemes() {
        List<ThemeDefinition> loaded = new ArrayList<>();
        for (String id : themeIds()) {
            String path = "themes/" + id + "/theme.yml";
            File override = new File(plugin.getDataFolder(), path);
            try (Reader reader = override.isFile()
                    ? Files.newBufferedReader(override.toPath(), StandardCharsets.UTF_8) : bundled(path)) {
                if (reader == null) {
                    plugin.getLogger().warning("Theme " + id + " has no theme.yml");
                    continue;
                }
                ThemeDefinition theme = ThemeDefinition.parse(YamlConfiguration.loadConfiguration(reader));
                if (!theme.id().equals(id)) throw new IllegalArgumentException("theme.yml id " + theme.id());
                loaded.add(theme);
            } catch (Exception error) {
                plugin.getLogger().log(Level.WARNING, "Theme " + id + " could not be loaded", error);
            }
        }
        plugin.getLogger().info("Seasonal themes available: " + loaded.stream().map(theme -> theme.id() + " "
                + theme.start().toLocalDate() + "→" + theme.end().toLocalDate()).toList());
        return List.copyOf(loaded);
    }

    private List<String> themeIds() {
        try (Reader reader = bundled("themes/index.yml")) {
            if (reader == null) return List.of();
            return YamlConfiguration.loadConfiguration(reader).getStringList("themes");
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "Theme index could not be read", error);
            return List.of();
        }
    }

    private Reader bundled(String path) {
        InputStream stream = plugin.getResource(path);
        return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    /** Reads several chunk snapshots as one world; anything outside is air. */
    private record SnapshotView(Map<Long, ChunkSnapshot> snapshots, int minY, int maxY) implements BlockView {
        @Override
        public String type(int x, int y, int z) {
            if (y < minY || y >= maxY) return "AIR";
            ChunkSnapshot snapshot = snapshots.get(FakeBlock.chunkKey(x >> 4, z >> 4));
            return snapshot == null ? "AIR" : snapshot.getBlockType(x & 15, y, z & 15).name();
        }

        @Override
        public String data(int x, int y, int z) {
            if (y < minY || y >= maxY) return "minecraft:air";
            ChunkSnapshot snapshot = snapshots.get(FakeBlock.chunkKey(x >> 4, z >> 4));
            return snapshot == null ? "minecraft:air" : snapshot.getBlockData(x & 15, y, z & 15).getAsString();
        }
    }
}
