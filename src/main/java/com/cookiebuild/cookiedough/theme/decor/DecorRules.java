package com.cookiebuild.cookiedough.theme.decor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Client-side decoration rules of a theme.
 *
 * <ul>
 *   <li>{@code swaps}: replace a real block by another of the same shape
 *       (leaves, carpets, plants), keeping its states when possible.</li>
 *   <li>{@code toppers}: put a block on the air above ({@code over}) a fence or wall post.</li>
 *   <li>{@code ground}: put a non-colliding plant on the air above ({@code over}) a block.</li>
 *   <li>{@code hanging}: hang a block under a matching block with air below.</li>
 *   <li>{@code ceiling-corners}: fill out-of-reach corners under overhangs.</li>
 * </ul>
 *
 * ({@code on} is avoided as a key: YAML 1.1 reads it as a boolean.)
 * Block data may use {@code facing=@center} (towards the decorated area's
 * centre) or {@code facing=@random}.
 */
public record DecorRules(List<Swap> swaps, Addition toppers, Addition ground, Addition hanging,
        Addition ceilingCorners) {
    public record Swap(BlockMatcher from, WeightedBlocks to, boolean keepStates) {
    }

    /** Blocks added in air next to a matching block, with a deterministic chance. */
    public record Addition(BlockMatcher on, double chance, WeightedBlocks blocks) {
        public Addition {
            if (chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be within [0, 1]");
        }
    }

    public DecorRules {
        swaps = List.copyOf(swaps);
    }

    public static DecorRules none() {
        return new DecorRules(List.of(), null, null, null, null);
    }

    public static DecorRules parse(ConfigurationSection section) {
        if (section == null) return none();
        List<Swap> swaps = new ArrayList<>();
        for (Map<?, ?> swap : section.getMapList("swaps")) {
            Object from = swap.get("from");
            List<String> names = from instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(from));
            Object keep = swap.get("keep-states");
            swaps.add(new Swap(BlockMatcher.of(names), WeightedBlocks.of(swap.get("to")),
                    keep == null || Boolean.parseBoolean(String.valueOf(keep))));
        }
        return new DecorRules(swaps, addition(section.getConfigurationSection("toppers"), "over"),
                addition(section.getConfigurationSection("ground"), "over"),
                addition(section.getConfigurationSection("hanging"), "under"),
                addition(section.getConfigurationSection("ceiling-corners"), null));
    }

    private static Addition addition(ConfigurationSection section, String matcherKey) {
        if (section == null) return null;
        BlockMatcher on = matcherKey == null ? BlockMatcher.of(List.of())
                : BlockMatcher.of(section.getStringList(matcherKey));
        Object blocks = section.get("blocks");
        return new Addition(on, section.getDouble("chance", 1.0), WeightedBlocks.of(blocks));
    }
}
