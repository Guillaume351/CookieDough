package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class SkyblockBillboardTest {
    @Test
    void packagesAnExactSingleMapSkyblockImage() throws IOException {
        try (var stream = SkyblockBillboardTest.class.getClassLoader()
                .getResourceAsStream(SkyblockBillboard.IMAGE_RESOURCE)) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(128, image.getWidth());
            assertEquals(128, image.getHeight());
        }
    }

    @Test
    void acceptsOnlyHorizontalCardinalFrameDirections() {
        assertEquals(BlockFace.NORTH, SkyblockBillboard.parseFacing("north"));
        assertEquals(BlockFace.SOUTH, SkyblockBillboard.parseFacing("SOUTH"));
        assertEquals(BlockFace.SOUTH, SkyblockBillboard.parseFacing("UP"));
        assertEquals(BlockFace.SOUTH, SkyblockBillboard.parseFacing("not-a-face"));
        assertEquals(BlockFace.SOUTH, SkyblockBillboard.parseFacing(null));
    }

    @Test
    void reusesOnlyAConfiguredMapIdAcrossRestarts() {
        Object persisted = new Object();
        assertEquals(persisted, SkyblockBillboard.findConfiguredMap(42,
                mapId -> mapId == 42 ? persisted : null));

        AtomicBoolean lookedUp = new AtomicBoolean();
        assertNull(SkyblockBillboard.findConfiguredMap(-1, mapId -> {
            lookedUp.set(true);
            return persisted;
        }));
        assertFalse(lookedUp.get());
    }
}
