package com.cookiebuild.cookiedough.theme.decor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Computes the fake blocks of a decorated area from a read-only view of the
 * world. Pure and deterministic: the same world always gets the same decor,
 * so every player sees the same pumpkins at the same places.
 */
public final class DecorPlanner {
    /** Inclusive box to decorate and the point that {@code facing=@center} looks at. */
    public record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int centerX, int centerZ) {
        public Area {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Empty area");
        }
    }

    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    private final DecorRules rules;
    private final Predicate<String> fullCube;
    private final long salt;

    /**
     * @param fullCube whether a material is a full, solid cube (ceiling corners
     *        only form against such blocks)
     */
    public DecorPlanner(DecorRules rules, Predicate<String> fullCube, long salt) {
        this.rules = rules;
        this.fullCube = fullCube;
        this.salt = salt;
    }

    public List<FakeBlock> plan(BlockView view, Area area, Set<Long> excluded) {
        List<FakeBlock> out = new ArrayList<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    if (excluded.contains(FakeBlock.pack(x, y, z))) continue;
                    String type = view.type(x, y, z);
                    FakeBlock fake = BlockView.isAir(type) ? addition(view, area, x, y, z) : swap(view, area, x, y, z, type);
                    if (fake != null) out.add(fake);
                }
            }
        }
        return out;
    }

    private FakeBlock swap(BlockView view, Area area, int x, int y, int z, String type) {
        for (DecorRules.Swap swap : rules.swaps()) {
            if (!swap.from().matches(type)) continue;
            String target = resolve(swap.to().pick(roll(x, y, z, 1)), area, x, y, z);
            if (swap.keepStates() && target.indexOf('[') < 0) {
                String source = view.data(x, y, z);
                int states = source == null ? -1 : source.indexOf('[');
                if (states > 0) target = target + source.substring(states);
            }
            if (target.equalsIgnoreCase(view.data(x, y, z))) return null;
            return new FakeBlock(x, y, z, target, type);
        }
        return null;
    }

    private FakeBlock addition(BlockView view, Area area, int x, int y, int z) {
        String below = view.type(x, y - 1, z);
        if (rules.toppers() != null && rules.toppers().on().matches(below)) {
            return place(rules.toppers(), area, x, y, z, 2);
        }
        if (rules.ground() != null && rules.ground().on().matches(below)) {
            return place(rules.ground(), area, x, y, z, 3);
        }
        String above = view.type(x, y + 1, z);
        if (rules.hanging() != null && rules.hanging().on().matches(above)
                && BlockView.isAir(below) && BlockView.isAir(view.type(x, y - 2, z))) {
            return place(rules.hanging(), area, x, y, z, 4);
        }
        if (rules.ceilingCorners() != null && fullCube.test(above) && BlockView.isAir(below)
                && BlockView.isAir(view.type(x, y - 2, z))
                && (fullCube.test(view.type(x + 1, y, z)) || fullCube.test(view.type(x - 1, y, z)))
                && (fullCube.test(view.type(x, y, z + 1)) || fullCube.test(view.type(x, y, z - 1)))) {
            return place(rules.ceilingCorners(), area, x, y, z, 5);
        }
        return null;
    }

    private FakeBlock place(DecorRules.Addition addition, Area area, int x, int y, int z, int stream) {
        if (roll(x, y, z, stream) >= addition.chance()) return null;
        String data = resolve(addition.blocks().pick(roll(x, y, z, stream + 10)), area, x, y, z);
        return new FakeBlock(x, y, z, data, FakeBlock.AIR);
    }

    private String resolve(String data, Area area, int x, int y, int z) {
        if (data.contains("@center")) data = data.replace("@center", towards(area.centerX() - x, area.centerZ() - z));
        if (data.contains("@random")) data = data.replace("@random", HORIZONTAL[(int) (roll(x, y, z, 7) * 4)]);
        return data;
    }

    static String towards(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "east" : "west";
        return dz > 0 ? "south" : "north";
    }

    /** Deterministic pseudo-random number in [0, 1) for a position and a decision stream. */
    double roll(int x, int y, int z, int stream) {
        long h = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L)
                ^ (stream * 0xD6E8FEB86659FD93L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
