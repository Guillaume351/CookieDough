package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.PlayerState;

class CosmeticParticlesTest {
    @Test
    void everyTrailAndVictoryCosmeticHasARecipeAndNothingElseDoes() {
        Set<String> trails = CosmeticCatalog.items().stream()
                .filter(item -> item.slot() == CosmeticSlot.HUB_TRAIL)
                .map(CosmeticDefinition::id).collect(Collectors.toSet());
        Set<String> victories = CosmeticCatalog.items().stream()
                .filter(item -> item.slot() == CosmeticSlot.VICTORY_EFFECT)
                .map(CosmeticDefinition::id).collect(Collectors.toSet());
        assertEquals(trails, CosmeticParticles.trailIds());
        assertEquals(victories, CosmeticParticles.victoryIds());
        for (String trail : trails) {
            assertFalse(CosmeticParticles.trail(trail, 0).isEmpty(), trail);
            assertTrue(CosmeticEffects.trailParticle(trail) != null, trail);
        }
        assertTrue(CosmeticParticles.trail(CosmeticCatalog.GOLDEN_COOKIE_BURST, 0).isEmpty());
        assertTrue(CosmeticParticles.trail(null, 0).isEmpty());
        assertFalse(CosmeticParticles.hasVictoryEffect(CosmeticCatalog.HEART_TRAIL));
    }

    @Test
    void onlyParticlesWithANativeBedrockMappingAndMatchingDataAreUsed() {
        for (String trail : CosmeticParticles.trailIds()) {
            for (long step = 0; step < 8; step++) {
                CosmeticParticles.trail(trail, step).forEach(burst -> assertRenderable(trail, burst));
            }
        }
        for (String victory : CosmeticParticles.victoryIds()) {
            for (CosmeticParticles.Frame frame : CosmeticParticles.victory(victory)) {
                assertTrue(frame.delayTicks() >= 0 && frame.delayTicks() <= 40, victory);
                assertTrue(frame.sound() == null || frame.sound().matches("[a-z_]+(\\.[a-z_]+)+"), victory);
                frame.bursts().forEach(burst -> assertRenderable(victory, burst));
            }
        }
    }

    @Test
    void trailsStayLightAndVictoriesStayBounded() {
        for (String trail : CosmeticParticles.trailIds()) {
            int perStep = CosmeticParticles.trail(trail, 0).stream().mapToInt(CosmeticParticles.Burst::count).sum();
            assertTrue(perStep >= 1 && perStep <= 4, trail + " spawns " + perStep);
        }
        for (String victory : CosmeticParticles.victoryIds()) {
            int total = CosmeticParticles.victory(victory).stream()
                    .flatMap(frame -> frame.bursts().stream()).mapToInt(CosmeticParticles.Burst::count).sum();
            assertTrue(total >= 10 && total <= 120, victory + " spawns " + total);
        }
    }

    @Test
    void rainbowCyclesColoursWhileOtherTrailsAreStable() {
        List<CosmeticParticles.Burst> first = CosmeticParticles.trail(CosmeticCatalog.RAINBOW_TRAIL, 0);
        List<CosmeticParticles.Burst> next = CosmeticParticles.trail(CosmeticCatalog.RAINBOW_TRAIL, 1);
        assertNotEquals(((Particle.DustOptions) first.getFirst().data()).getColor(),
                ((Particle.DustOptions) next.getFirst().data()).getColor());
        assertEquals(((Particle.DustOptions) first.getFirst().data()).getColor(), ((Particle.DustOptions)
                CosmeticParticles.trail(CosmeticCatalog.RAINBOW_TRAIL, 7).getFirst().data()).getColor());
        assertEquals(CosmeticParticles.trail(CosmeticCatalog.HEART_TRAIL, 0),
                CosmeticParticles.trail(CosmeticCatalog.HEART_TRAIL, 5));
        assertEquals(CosmeticParticles.trail(CosmeticCatalog.RAINBOW_TRAIL, -1).size(), 1);
    }

    @Test
    void everyVictoryEffectPassesTheGuardAndTrailsDoNot() {
        for (String victory : CosmeticParticles.victoryIds()) {
            assertTrue(CosmeticEffectGuard.canUseVictoryEffect(victory, PlayerState.IN_GAME, 20_000, 0, 3_000));
        }
        assertFalse(CosmeticEffectGuard.canUseVictoryEffect(CosmeticCatalog.HEART_TRAIL,
                PlayerState.IN_GAME, 20_000, 0, 3_000));
        assertFalse(CosmeticEffectGuard.canUseVictoryEffect(null, PlayerState.IN_GAME, 20_000, 0, 3_000));
    }

    @Test
    void theFreeTrailIsDenserThanBeforeButStillTheSameParticle() {
        List<CosmeticParticles.Burst> sparkle = CosmeticParticles.trail(CosmeticCatalog.COOKIE_SPARKLE_TRAIL, 0);
        assertEquals(Particle.END_ROD, sparkle.getFirst().particle());
        assertEquals(2, sparkle.getFirst().count());
    }

    private static void assertRenderable(String cosmetic, CosmeticParticles.Burst burst) {
        assertTrue(CosmeticParticles.BEDROCK_MAPPED.contains(burst.particle()),
                cosmetic + " uses " + burst.particle() + " without a native Bedrock mapping");
        Class<?> expected = burst.particle().getDataType();
        if (burst.data() == null) {
            assertEquals(Void.class, expected, cosmetic + " " + burst.particle() + " needs data");
        } else if (burst.data() instanceof Material material) {
            assertEquals(ItemStack.class, expected, cosmetic);
            assertEquals(Material.COOKIE, material, cosmetic);
        } else {
            assertTrue(expected.isInstance(burst.data()), cosmetic + " " + burst.particle() + " data type");
        }
    }
}
