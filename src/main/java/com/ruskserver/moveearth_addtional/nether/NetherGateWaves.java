package com.ruskserver.moveearth_addtional.nether;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The enemies of each wave. The next wave comes once the previous one is dead. */
public final class NetherGateWaves {
    public enum Kind { WITHER_SKELETON, BLAZE, PIGLIN_BRUTE }

    private static final List<Map<Kind, Integer>> BASE = List.of(
            Map.of(Kind.WITHER_SKELETON, 3, Kind.BLAZE, 2),
            Map.of(Kind.BLAZE, 3, Kind.PIGLIN_BRUTE, 2),
            Map.of(Kind.WITHER_SKELETON, 3, Kind.BLAZE, 3, Kind.PIGLIN_BRUTE, 2));

    private NetherGateWaves() { }

    public static int count() {
        return BASE.size();
    }

    /** Each kind scaled by {@code multiplier}, rounded, never below one. */
    public static Map<Kind, Integer> wave(int index, double multiplier) {
        Map<Kind, Integer> scaled = new EnumMap<>(Kind.class);
        BASE.get(index).forEach((kind, count) ->
                scaled.put(kind, Math.max(1, (int) Math.round(count * multiplier))));
        return scaled;
    }

    public static List<Kind> spawnOrder(int index, double multiplier) {
        List<Kind> order = new ArrayList<>();
        wave(index, multiplier).forEach((kind, count) -> {
            for (int i = 0; i < count; i++) order.add(kind);
        });
        return order;
    }
}
