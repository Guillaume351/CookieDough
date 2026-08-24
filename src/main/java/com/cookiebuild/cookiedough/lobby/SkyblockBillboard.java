package com.cookiebuild.cookiedough.lobby;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A single, static vanilla map beside the Skyblock NPC.
 *
 * <p>The server renders the pixels into a regular filled map, which gives Java
 * and Geyser one protocol-native source of truth. It deliberately avoids Java
 * display entities, custom item models and multi-map walls. The map id is
 * persisted in CookieDough's config so restarts do not continuously allocate
 * world map data.</p>
 */
final class SkyblockBillboard {
    static final String IMAGE_RESOURCE = "lobby/skyblock-map.png";
    private static final int MAP_SIZE = 128;
    private static final String MARKER_VALUE = "skyblock";

    private final ItemFrame frame;
    private final int mapId;

    private SkyblockBillboard(ItemFrame frame, int mapId) {
        this.frame = frame;
        this.mapId = mapId;
    }

    static SkyblockBillboard create(JavaPlugin plugin, Location location, String facingName,
            int configuredMapId) {
        BufferedImage image;
        try (InputStream stream = plugin.getResource(IMAGE_RESOURCE)) {
            if (stream == null) {
                plugin.getLogger().warning("Skipping the Skyblock billboard: missing " + IMAGE_RESOURCE);
                return null;
            }
            image = ImageIO.read(stream);
            if (image == null) {
                plugin.getLogger().warning("Skipping the Skyblock billboard: unreadable " + IMAGE_RESOURCE);
                return null;
            }
        } catch (IOException error) {
            plugin.getLogger().warning("Skipping the Skyblock billboard: " + error.getMessage());
            return null;
        }

        MapView map = reuseOrCreateMap(location, configuredMapId);
        prepareMap(map, scaleToMap(image));
        ItemFrame frame = reconcileFrame(plugin, location, parseFacing(facingName));
        frame.setItem(mapItem(map), false);
        return new SkyblockBillboard(frame, map.getId());
    }

    int mapId() {
        return mapId;
    }

    void shutdown() {
        if (frame.isValid()) {
            frame.remove();
        }
    }

    private static MapView reuseOrCreateMap(Location location, int configuredMapId) {
        MapView existing = findConfiguredMap(configuredMapId, Bukkit::getMap);
        return existing != null ? existing : Bukkit.createMap(location.getWorld());
    }

    static <T> T findConfiguredMap(int configuredMapId, IntFunction<T> lookup) {
        return configuredMapId >= 0 ? lookup.apply(configuredMapId) : null;
    }

    private static void prepareMap(MapView map, BufferedImage image) {
        for (MapRenderer renderer : new ArrayList<>(map.getRenderers())) {
            map.removeRenderer(renderer);
        }
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        map.setLocked(true);
        map.addRenderer(new StaticImageRenderer(image));
    }

    private static ItemStack mapItem(MapView map) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(map);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemFrame reconcileFrame(JavaPlugin plugin, Location location, BlockFace facing) {
        NamespacedKey marker = new NamespacedKey(plugin, "lobby_billboard");
        List<ItemFrame> candidates = new ArrayList<>();
        List<NpcReconciliation.Candidate> policyCandidates = new ArrayList<>();
        for (Entity entity : location.getChunk().getEntities()) {
            String value = entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
            if (!MARKER_VALUE.equals(value)) {
                continue;
            }
            if (entity instanceof ItemFrame itemFrame) {
                candidates.add(itemFrame);
                policyCandidates.add(new NpcReconciliation.Candidate(itemFrame.getUniqueId(), false,
                        itemFrame.getLocation().distanceSquared(location)));
            } else {
                entity.remove();
            }
        }

        UUID canonicalId = NpcReconciliation.selectCanonical(policyCandidates);
        ItemFrame frame = candidates.stream()
                .filter(candidate -> candidate.getUniqueId().equals(canonicalId))
                .findFirst()
                .orElseGet(() -> location.getWorld().spawn(location, ItemFrame.class));
        for (ItemFrame candidate : candidates) {
            if (!candidate.getUniqueId().equals(frame.getUniqueId())) {
                candidate.remove();
            }
        }
        if (candidates.size() > 1) {
            plugin.getLogger().warning("Removed " + (candidates.size() - 1)
                    + " duplicate Skyblock billboard frame(s)");
        }

        frame.teleport(location);
        frame.setFacingDirection(facing, true);
        frame.setRotation(Rotation.NONE);
        frame.setFixed(true);
        frame.setVisible(true);
        frame.setInvulnerable(true);
        frame.setPersistent(true);
        frame.setItemDropChance(0.0f);
        frame.getPersistentDataContainer().set(marker, PersistentDataType.STRING, MARKER_VALUE);
        LobbyEntityOwnership.mark(plugin, frame, "skyblock_billboard");
        return frame;
    }

    static BlockFace parseFacing(String value) {
        if (value == null) {
            return BlockFace.SOUTH;
        }
        try {
            BlockFace face = BlockFace.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return face.isCartesian() && face.getModY() == 0 ? face : BlockFace.SOUTH;
        } catch (IllegalArgumentException ignored) {
            return BlockFace.SOUTH;
        }
    }

    private static BufferedImage scaleToMap(BufferedImage source) {
        BufferedImage target = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(new Color(24, 19, 16));
            graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min((double) MAP_SIZE / source.getWidth(), (double) MAP_SIZE / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            graphics.drawImage(source, (MAP_SIZE - width) / 2, (MAP_SIZE - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static final class StaticImageRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean rendered;

        private StaticImageRenderer(BufferedImage image) {
            super(false);
            this.image = image;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, org.bukkit.entity.Player player) {
            if (!rendered) {
                canvas.drawImage(0, 0, image);
                rendered = true;
            }
        }
    }
}
