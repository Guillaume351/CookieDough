package com.cookiebuild.cookiedough.theme.decor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Material names and groups ({@code #leaves}, {@code #fences}, ...) of a decor rule. */
public record BlockMatcher(Set<String> names, Set<String> groups) {
    private static final Set<String> SMALL_FLOWERS = Set.of("POPPY", "DANDELION", "AZURE_BLUET", "BLUE_ORCHID",
            "ALLIUM", "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP", "PINK_TULIP", "OXEYE_DAISY", "CORNFLOWER",
            "LILY_OF_THE_VALLEY", "TORCHFLOWER", "OPEN_EYEBLOSSOM", "CLOSED_EYEBLOSSOM");
    private static final Set<String> CROPS = Set.of("WHEAT", "POTATOES", "CARROTS", "BEETROOTS");

    public BlockMatcher {
        names = Set.copyOf(names);
        groups = Set.copyOf(groups);
    }

    public static BlockMatcher of(List<String> entries) {
        java.util.Set<String> names = new java.util.HashSet<>();
        java.util.Set<String> groups = new java.util.HashSet<>();
        for (String entry : entries) {
            String value = entry.trim();
            if (value.startsWith("#")) groups.add(value.substring(1).toLowerCase(Locale.ROOT));
            else names.add(value.toUpperCase(Locale.ROOT));
        }
        for (String group : groups) {
            if (!Set.of("leaves", "fences", "walls", "small_flowers", "crops").contains(group)) {
                throw new IllegalArgumentException("Unknown block group #" + group);
            }
        }
        return new BlockMatcher(names, groups);
    }

    public boolean matches(String type) {
        if (type == null) return false;
        if (names.contains(type)) return true;
        for (String group : groups) {
            boolean hit = switch (group) {
                case "leaves" -> type.endsWith("_LEAVES");
                case "fences" -> type.endsWith("_FENCE");
                case "walls" -> type.endsWith("_WALL");
                case "small_flowers" -> SMALL_FLOWERS.contains(type);
                case "crops" -> CROPS.contains(type);
                default -> false;
            };
            if (hit) return true;
        }
        return false;
    }
}
