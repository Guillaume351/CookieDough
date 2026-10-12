package com.cookiebuild.cookiedough.theme.decor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Block data strings with integer weights, picked deterministically per position. */
public record WeightedBlocks(List<String> data, List<Integer> weights, int total) {
    public WeightedBlocks {
        data = List.copyOf(data);
        weights = List.copyOf(weights);
        if (data.isEmpty() || data.size() != weights.size() || total <= 0) {
            throw new IllegalArgumentException("At least one weighted block is required");
        }
    }

    public static WeightedBlocks of(Object yaml) {
        Map<String, Integer> entries = new LinkedHashMap<>();
        if (yaml instanceof String single) {
            entries.put(single, 1);
        } else if (yaml instanceof Map<?, ?> map) {
            map.forEach((key, value) -> entries.put(String.valueOf(key),
                    value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value))));
        } else if (yaml instanceof org.bukkit.configuration.ConfigurationSection section) {
            for (String key : section.getKeys(false)) entries.put(key, section.getInt(key));
        } else {
            throw new IllegalArgumentException("Expected a block or a map of blocks to weights: " + yaml);
        }
        List<String> data = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (var entry : entries.entrySet()) {
            if (entry.getValue() <= 0) continue;
            data.add(entry.getKey().trim());
            weights.add(entry.getValue());
            total += entry.getValue();
        }
        return new WeightedBlocks(data, weights, total);
    }

    public String pick(double roll) {
        int target = (int) Math.floor(Math.max(0, Math.min(0.999999, roll)) * total);
        for (int i = 0; i < data.size(); i++) {
            target -= weights.get(i);
            if (target < 0) return data.get(i);
        }
        return data.getLast();
    }
}
