package com.cookiebuild.cookiedough.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.theme.decor.BlockMatcher;
import com.cookiebuild.cookiedough.theme.decor.BlockView;
import com.cookiebuild.cookiedough.theme.decor.DecorPlanner;
import com.cookiebuild.cookiedough.theme.decor.DecorRules;
import com.cookiebuild.cookiedough.theme.decor.FakeBlock;
import com.cookiebuild.cookiedough.theme.decor.WeightedBlocks;

class DecorPlannerTest {
    private static final Set<String> FULL = Set.of("STONE", "OAK_PLANKS", "GRASS_BLOCK", "OAK_LOG");

    /** A tiny world kept in a map; anything absent is air. */
    private static final class World implements BlockView {
        final Map<Long, String> data = new HashMap<>();

        World put(int x, int y, int z, String blockData) {
            data.put(FakeBlock.pack(x, y, z), blockData);
            return this;
        }

        @Override
        public String type(int x, int y, int z) {
            String value = data.get(FakeBlock.pack(x, y, z));
            if (value == null) return "AIR";
            String name = value.replace("minecraft:", "");
            int states = name.indexOf('[');
            return (states < 0 ? name : name.substring(0, states)).toUpperCase();
        }

        @Override
        public String data(int x, int y, int z) {
            return data.getOrDefault(FakeBlock.pack(x, y, z), "minecraft:air");
        }
    }

    private static DecorRules rules(String yaml) {
        return DecorRules.parse(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static List<FakeBlock> plan(DecorRules rules, World world, Set<Long> excluded) {
        DecorPlanner planner = new DecorPlanner(rules, FULL::contains, 42);
        return planner.plan(world, new DecorPlanner.Area(-8, 0, -8, 8, 12, 8, 0, 0), excluded);
    }

    private static FakeBlock at(List<FakeBlock> blocks, int x, int y, int z) {
        return blocks.stream().filter(b -> b.x() == x && b.y() == y && b.z() == z).findFirst().orElse(null);
    }

    @Test
    void swapsKeepTheBlockStatesAndRecordTheRealType() {
        World world = new World().put(1, 5, 1, "minecraft:birch_leaves[distance=7,persistent=true,waterlogged=false]");
        List<FakeBlock> blocks = plan(rules("""
                swaps:
                  - from: ["#leaves"]
                    to: "minecraft:pale_oak_leaves"
                """), world, Set.of());
        FakeBlock leaves = at(blocks, 1, 5, 1);
        assertNotNull(leaves);
        assertEquals("minecraft:pale_oak_leaves[distance=7,persistent=true,waterlogged=false]", leaves.data());
        assertEquals("BIRCH_LEAVES", leaves.expected());
    }

    @Test
    void toppersSitOnFencePostsAndFaceTheCentre() {
        World world = new World().put(5, 3, 0, "minecraft:oak_fence").put(0, 3, -6, "minecraft:spruce_fence");
        List<FakeBlock> blocks = plan(rules("""
                toppers:
                  over: ["#fences"]
                  chance: 1.0
                  blocks: {"minecraft:jack_o_lantern[facing=@center]": 1}
                """), world, Set.of());
        assertEquals("minecraft:jack_o_lantern[facing=west]", at(blocks, 5, 4, 0).data());
        assertEquals("minecraft:jack_o_lantern[facing=south]", at(blocks, 0, 4, -6).data());
        assertEquals(FakeBlock.AIR, at(blocks, 5, 4, 0).expected());
        assertEquals(2, blocks.size());
    }

    @Test
    void cobwebsOnlyFillCornersOutOfReachUnderACeiling() {
        World world = new World();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                world.put(x, 0, z, "minecraft:stone");
                world.put(x, 4, z, "minecraft:oak_planks");
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int i = -3; i <= 3; i++) {
                world.put(-3, y, i, "minecraft:stone").put(3, y, i, "minecraft:stone");
                world.put(i, y, -3, "minecraft:stone").put(i, y, 3, "minecraft:stone");
            }
        }
        List<FakeBlock> blocks = plan(rules("""
                ceiling-corners:
                  chance: 1.0
                  blocks: {"minecraft:cobweb": 1}
                """), world, Set.of());
        assertTrue(blocks.size() >= 4);
        for (FakeBlock block : blocks) {
            assertEquals(3, block.y(), "only at the top of a 3-high room");
            assertEquals(2, Math.abs(block.x()), "against a wall");
            assertEquals(2, Math.abs(block.z()), "in a corner");
        }
    }

    @Test
    void hangingMossNeedsTwoFreeBlocksBelowTheLeaves() {
        World world = new World().put(0, 6, 0, "minecraft:oak_leaves").put(2, 6, 0, "minecraft:oak_leaves")
                .put(2, 4, 0, "minecraft:stone");
        List<FakeBlock> blocks = plan(rules("""
                hanging:
                  under: ["#leaves"]
                  chance: 1.0
                  blocks: {"minecraft:pale_hanging_moss[tip=true]": 1}
                """), world, Set.of());
        assertNotNull(at(blocks, 0, 5, 0));
        assertNull(at(blocks, 2, 5, 0), "the moss would touch the ground");
    }

    @Test
    void planningIsDeterministicAndRespectsExclusionsAndChance() {
        World world = new World();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) world.put(x, 0, z, "minecraft:grass_block");
        }
        DecorRules ground = rules("""
                ground:
                  over: [GRASS_BLOCK]
                  chance: 0.25
                  blocks: {"minecraft:leaf_litter[segment_amount=4,facing=@random]": 3, "minecraft:dead_bush": 1}
                """);
        List<FakeBlock> first = plan(ground, world, Set.of());
        assertEquals(first, plan(ground, world, Set.of()));
        assertTrue(first.size() > 289 * 0.12 && first.size() < 289 * 0.4, "about a quarter: " + first.size());
        FakeBlock any = first.getFirst();
        List<FakeBlock> without = plan(ground, world, Set.of(any.packedPosition()));
        assertNull(at(without, any.x(), any.y(), any.z()));
        assertTrue(first.stream().noneMatch(block -> block.data().contains("@")));
    }

    @Test
    void groupsAndWeightsResolve() {
        BlockMatcher matcher = BlockMatcher.of(List.of("#leaves", "#fences", "#small_flowers", "#crops", "GREEN_CARPET"));
        assertTrue(matcher.matches("JUNGLE_LEAVES"));
        assertTrue(matcher.matches("DARK_OAK_FENCE"));
        assertTrue(matcher.matches("POPPY"));
        assertTrue(matcher.matches("POTATOES"));
        assertTrue(matcher.matches("GREEN_CARPET"));
        assertTrue(!matcher.matches("OAK_FENCE_GATE") && !matcher.matches("STONE"));
        WeightedBlocks weights = WeightedBlocks.of(Map.of("a", 3));
        assertEquals("a", weights.pick(0.99));
    }

    @Test
    void theHalloweenLobbyDecorParses() throws Exception {
        YamlConfiguration theme = SongParserTest.bundled("themes/halloween/theme.yml");
        DecorRules rules = DecorRules.parse(theme.getConfigurationSection("lobby.decor"));
        assertEquals(5, rules.swaps().size());
        assertNotNull(rules.toppers());
        assertNotNull(rules.ground());
        assertNotNull(rules.hanging());
        assertNotNull(rules.ceilingCorners());
        assertTrue(rules.toppers().on().matches("OAK_FENCE"));
        assertTrue(rules.ground().on().matches("LIME_TERRACOTTA"));
    }

    @Test
    void keepWeightsLeaveAShareOfTheMatchingBlocksReal() {
        World world = new World();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) world.put(x, 6, z, "minecraft:oak_leaves[distance=1,persistent=true]");
        }
        DecorRules rules = rules("""
                swaps:
                  - from: ["#leaves"]
                    keep-states: false
                    to: {keep: 9, "minecraft:shroomlight": 1}
                """);
        List<FakeBlock> blocks = plan(rules, world, Set.of());
        assertTrue(blocks.size() > 289 * 0.04 && blocks.size() < 289 * 0.2, "about a tenth: " + blocks.size());
        for (FakeBlock block : blocks) {
            assertEquals("minecraft:shroomlight", block.data(), "no states copied, never the keep marker");
            assertEquals("OAK_LEAVES", block.expected());
        }
        assertEquals(blocks, plan(rules, world, Set.of()), "the same leaves glow for everyone");
        assertTrue(plan(rules("""
                swaps:
                  - from: ["#leaves"]
                    to: {keep: 1}
                """), world, Set.of()).isEmpty(), "keep only: nothing changes");
        assertTrue(WeightedBlocks.isKeep(WeightedBlocks.of(Map.of("keep", 1)).pick(0.5)));
    }

    @Test
    void keepAlsoWorksForAdditions() {
        World world = new World();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) world.put(x, 0, z, "minecraft:grass_block");
        }
        List<FakeBlock> blocks = plan(rules("""
                ground:
                  over: [GRASS_BLOCK]
                  chance: 1.0
                  blocks: {keep: 1, "minecraft:snow[layers=1]": 1}
                """), world, Set.of());
        assertTrue(blocks.size() > 289 * 0.3 && blocks.size() < 289 * 0.7, "about half: " + blocks.size());
        assertTrue(blocks.stream().allMatch(block -> block.data().equals("minecraft:snow[layers=1]")));
    }

    @Test
    void theChristmasLobbyDecorStaysSafe() throws Exception {
        YamlConfiguration theme = SongParserTest.bundled("themes/christmas/theme.yml");
        DecorRules rules = DecorRules.parse(theme.getConfigurationSection("lobby.decor"));
        assertNull(rules.hanging());
        assertNull(rules.ceilingCorners(), "no cobwebs at Christmas");
        // Ground cover is a single snow layer: no collision box, so nobody stands on a fake.
        assertEquals(List.of("minecraft:snow[layers=1]"), rules.ground().blocks().data());
        assertTrue(rules.ground().on().matches("LIME_TERRACOTTA") && rules.ground().on().matches("BIRCH_LEAVES"));
        assertTrue(rules.toppers().on().matches("OAK_FENCE"));
        for (String topper : rules.toppers().blocks().data()) {
            assertTrue(topper.matches("minecraft:(lantern|red_candle\\[candles=3,lit=true]|green_candle\\[candles=3,lit=true])"),
                    topper);
        }
        for (DecorRules.Swap swap : rules.swaps()) {
            for (String block : swap.to().data()) {
                assertTrue(!block.contains("sweet_berry") && !block.contains("powder_snow"),
                        "client-side slowdown makes players rubber-band: " + block);
                assertTrue(!block.contains("_wool"), "wool is reserved for the hidden presents: " + block);
            }
            if (swap.from().matches("OAK_LEAVES")) {
                // Leaves are full collision blocks: baubles must be full blocks too.
                for (String block : swap.to().data()) {
                    assertTrue(WeightedBlocks.isKeep(block) || block.matches(
                            "minecraft:(shroomlight|pearlescent_froglight|redstone_lamp\\[lit=true])"), block);
                }
            }
            if (swap.from().matches("SHORT_GRASS")) {
                assertEquals(List.of("minecraft:snow[layers=1]"), swap.to().data(), "a plant becomes one snow layer");
                assertTrue(!swap.keepStates());
            }
        }

        // The planner on a tiny snowy garden: snow on top, never inside a block or on a hunt spot.
        World world = new World();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) world.put(x, 0, z, "minecraft:lime_terracotta");
        }
        world.put(2, 1, 2, "minecraft:short_grass");
        for (int x = -4; x <= 4; x++) world.put(x, 1, -4, "minecraft:oak_fence");
        List<FakeBlock> blocks = plan(rules, world, Set.of(FakeBlock.pack(0, 1, 0)));
        assertNull(at(blocks, 0, 1, 0), "excluded cell above a hunt spot");
        assertEquals("minecraft:snow[layers=1]", at(blocks, 2, 1, 2).data());
        assertEquals("SHORT_GRASS", at(blocks, 2, 1, 2).expected());
        assertTrue(blocks.stream().filter(block -> block.y() == 2 && block.z() == -4).count() >= 6,
                "lights on most fence posts");
        assertTrue(blocks.stream().allMatch(block -> block.y() >= 1 && block.y() <= 2));
    }
}
