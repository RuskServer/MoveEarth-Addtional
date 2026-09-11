package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WeldingBrushServerState {
    private static final Map<UUID, Integer> RADII = new ConcurrentHashMap<>();

    private WeldingBrushServerState() {
    }

    public static int radius(UUID playerId) {
        return RADII.getOrDefault(playerId, 0);
    }

    public static void setRadius(UUID playerId, int radius) {
        RADII.put(playerId, ReinforcementBrushPattern.clamp(radius));
    }

    public static void clear() {
        RADII.clear();
    }
}
