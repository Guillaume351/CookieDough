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

    @Test
    void halloweenParticlesAreTheTwoWithNativeGeyserMappings() {
        // WITCH -> level event WITCH_SPELL, SOUL -> minecraft:soul_particle (Geyser mappings/particles.json).
        assertTrue(CosmeticParticles.BEDROCK_MAPPED.containsAll(Set.of(Particle.WITCH, Particle.SOUL)));
        assertEquals(16, CosmeticParticles.BEDROCK_MAPPED.size());
        assertEquals(Void.class, Particle.WITCH.getDataType());
        assertEquals(Void.class, Particle.SOUL.getDataType());
    }

    @Test
    void everyTrailLooksDifferentFromEveryOtherTrail() {
        java.util.Map<Set<String>, String> signatures = new java.util.HashMap<>();
        for (String trail : CosmeticParticles.trailIds()) {
            Set<String> signature = CosmeticParticles.trail(trail, 0).stream()
                    .map(CosmeticParticlesTest::signature).collect(Collectors.toSet());
            String previous = signatures.put(signature, trail);
            assertTrue(previous == null, trail + " looks like " + previous);
        }
        Particle soulFlame = CosmeticParticles.primaryTrailParticle(CosmeticCatalog.SOUL_FLAME_TRAIL);
        Particle wisp = CosmeticParticles.primaryTrailParticle(CosmeticCatalog.WILL_O_WISP_TRAIL);
        assertEquals(Particle.SOUL, wisp);
        assertNotEquals(soulFlame, wisp);
        // Will-o'-the-wisps float around the body; Blue Flames stay at the feet.
        assertTrue(CosmeticParticles.trail(CosmeticCatalog.WILL_O_WISP_TRAIL, 0).stream()
                .allMatch(burst -> burst.y() >= 1.0));
        assertTrue(CosmeticParticles.trail(CosmeticCatalog.SOUL_FLAME_TRAIL, 0).stream()
                .allMatch(burst -> burst.y() < 0.5));
        assertTrue(CosmeticParticles.trail(CosmeticCatalog.WILL_O_WISP_TRAIL, 0).stream()
                .anyMatch(burst -> burst.data() instanceof Particle.DustOptions dust
                        && dust.getColor().equals(org.bukkit.Color.fromRGB(120, 220, 255))));
        List<CosmeticParticles.Burst> pumpkin = CosmeticParticles.trail(CosmeticCatalog.PUMPKIN_TRAIL, 0);
        assertEquals(Particle.DUST, pumpkin.getFirst().particle());
        assertTrue(pumpkin.stream().anyMatch(burst -> burst.particle() == Particle.ITEM
                && burst.data() == Material.JACK_O_LANTERN));
        assertEquals(2, pumpkin.stream().filter(burst -> burst.particle() == Particle.DUST).count());
    }

    @Test
    void witchSpellVictoryIsAShortSwirlWithAWitchCackle() {
        List<CosmeticParticles.Frame> frames = CosmeticParticles.victory(CosmeticCatalog.WITCH_SPELL_VICTORY);
        assertTrue(frames.size() >= 4, "multi-frame animation");
        assertEquals("entity.witch.celebrate", frames.getFirst().sound());
        assertEquals(0, frames.getFirst().delayTicks());
        assertTrue(frames.stream().anyMatch(frame -> "block.enchantment_table.use".equals(frame.sound())));
        Set<Particle> particles = frames.stream().flatMap(frame -> frame.bursts().stream())
                .map(CosmeticParticles.Burst::particle).collect(Collectors.toSet());
        assertEquals(Set.of(Particle.WITCH, Particle.DUST, Particle.SOUL), particles);
        Set<org.bukkit.Color> colours = frames.stream().flatMap(frame -> frame.bursts().stream())
                .filter(burst -> burst.data() instanceof Particle.DustOptions)
                .map(burst -> ((Particle.DustOptions) burst.data()).getColor()).collect(Collectors.toSet());
        assertEquals(Set.of(org.bukkit.Color.fromRGB(130, 40, 200), org.bukkit.Color.fromRGB(60, 200, 60)), colours);
        assertTrue(CosmeticParticles.hasVictoryEffect(CosmeticCatalog.WITCH_SPELL_VICTORY));
    }

    @Test
    void christmasParticlesAreTheTwoWithNativeGeyserLevelEvents() {
        // SNOWFLAKE -> level event SNOWFLAKE, ITEM_SNOWBALL -> level event SNOWBALL_POOF (never geyseropt:).
        assertTrue(CosmeticParticles.BEDROCK_MAPPED.containsAll(Set.of(Particle.SNOWFLAKE, Particle.ITEM_SNOWBALL)));
        assertFalse(CosmeticParticles.BEDROCK_MAPPED.contains(Particle.WHITE_ASH));
        assertFalse(CosmeticParticles.BEDROCK_MAPPED.contains(Particle.ASH));
        assertEquals(Void.class, Particle.SNOWFLAKE.getDataType());
        assertEquals(Void.class, Particle.ITEM_SNOWBALL.getDataType());
    }

    @Test
    void snowflakeTrailDriftsSnowflakesFromAboveWithALittleWhitePowder() {
        List<CosmeticParticles.Burst> snow = CosmeticParticles.trail(CosmeticCatalog.SNOWFLAKE_TRAIL, 0);
        assertEquals(Particle.SNOWFLAKE, CosmeticParticles.primaryTrailParticle(CosmeticCatalog.SNOWFLAKE_TRAIL));
        assertTrue(snow.getFirst().y() >= 1.0, "snowflakes fall from above");
        assertTrue(snow.stream().anyMatch(burst -> burst.data() instanceof Particle.DustOptions dust
                && dust.getColor().getRed() > 230 && dust.getColor().getGreen() > 230
                && dust.getColor().getBlue() > 230));
        assertEquals(snow, CosmeticParticles.trail(CosmeticCatalog.SNOWFLAKE_TRAIL, 3));
    }

    @Test
    void candyCaneTrailIsARedAndWhiteTwistThatTurns() {
        List<CosmeticParticles.Burst> first = CosmeticParticles.trail(CosmeticCatalog.CANDY_CANE_TRAIL, 0);
        assertTrue(first.stream().allMatch(burst -> burst.particle() == Particle.DUST));
        Set<org.bukkit.Color> colours = first.stream().map(burst -> ((Particle.DustOptions) burst.data()).getColor())
                .collect(Collectors.toSet());
        assertEquals(Set.of(org.bukkit.Color.fromRGB(220, 20, 40), org.bukkit.Color.fromRGB(255, 255, 255)), colours);
        // The stripe turns from step to step and comes back after a full turn.
        assertNotEquals(first, CosmeticParticles.trail(CosmeticCatalog.CANDY_CANE_TRAIL, 1));
        assertEquals(first, CosmeticParticles.trail(CosmeticCatalog.CANDY_CANE_TRAIL, 8));
        assertEquals(first, CosmeticParticles.trail(CosmeticCatalog.CANDY_CANE_TRAIL, -8));
        // Red and white stay on opposite sides of the player at each level.
        for (long step = 0; step < 8; step++) {
            List<CosmeticParticles.Burst> bursts = CosmeticParticles.trail(CosmeticCatalog.CANDY_CANE_TRAIL, step);
            for (int index = 0; index < bursts.size(); index += 2) {
                assertEquals(-bursts.get(index).x(), bursts.get(index + 1).x(), 1e-9);
                assertEquals(-bursts.get(index).z(), bursts.get(index + 1).z(), 1e-9);
            }
        }
    }

    @Test
    void snowstormVictoryRingsABellInAWhirlOfFestiveSnow() {
        List<CosmeticParticles.Frame> frames = CosmeticParticles.victory(CosmeticCatalog.SNOWSTORM_VICTORY);
        assertTrue(frames.size() >= 4, "multi-frame animation");
        assertEquals(0, frames.getFirst().delayTicks());
        assertEquals("block.bell.use", frames.getFirst().sound());
        assertEquals("block.note_block.chime", frames.getLast().sound());
        Set<Particle> particles = frames.stream().flatMap(frame -> frame.bursts().stream())
                .map(CosmeticParticles.Burst::particle).collect(Collectors.toSet());
        assertEquals(Set.of(Particle.SNOWFLAKE, Particle.ITEM_SNOWBALL, Particle.DUST), particles);
        Set<org.bukkit.Color> colours = frames.stream().flatMap(frame -> frame.bursts().stream())
                .filter(burst -> burst.data() instanceof Particle.DustOptions)
                .map(burst -> ((Particle.DustOptions) burst.data()).getColor()).collect(Collectors.toSet());
        assertEquals(Set.of(org.bukkit.Color.fromRGB(220, 20, 40), org.bukkit.Color.fromRGB(20, 140, 60),
                org.bukkit.Color.fromRGB(240, 248, 255)), colours);
        assertTrue(CosmeticParticles.hasVictoryEffect(CosmeticCatalog.SNOWSTORM_VICTORY));
    }

    private static String signature(CosmeticParticles.Burst burst) {
        Object data = burst.data() instanceof Particle.DustOptions dust ? dust.getColor() : burst.data();
        return burst.particle() + ":" + data;
    }

    private static void assertRenderable(String cosmetic, CosmeticParticles.Burst burst) {
        assertTrue(CosmeticParticles.BEDROCK_MAPPED.contains(burst.particle()),
                cosmetic + " uses " + burst.particle() + " without a native Bedrock mapping");
        Class<?> expected = burst.particle().getDataType();
        if (burst.data() == null) {
            assertEquals(Void.class, expected, cosmetic + " " + burst.particle() + " needs data");
        } else if (burst.data() instanceof Material material) {
            assertEquals(ItemStack.class, expected, cosmetic);
            // Each item shown as ITEM particles is reviewed on Bedrock (Geyser item fragments) before it joins this set.
            assertTrue(Set.of(Material.COOKIE, Material.JACK_O_LANTERN).contains(material), cosmetic + " " + material);
        } else {
            assertTrue(expected.isInstance(burst.data()), cosmetic + " " + burst.particle() + " data type");
        }
    }
}
