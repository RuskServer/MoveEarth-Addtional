package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.ArrayList;
import java.util.List;

/** Pure face-aligned square brush geometry. Radius 0/1/2 means 1x1/3x3/5x5. */
public final class ReinforcementBrushPattern {
    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 2;

    private ReinforcementBrushPattern() {
    }

    public static int clamp(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    public static int size(int radius) {
        return clamp(radius) * 2 + 1;
    }

    public static List<Offset> offsets(Axis normalAxis, int radius) {
        int safeRadius = clamp(radius);
        List<Offset> result = new ArrayList<>(size(safeRadius) * size(safeRadius));
        for (int first = -safeRadius; first <= safeRadius; first++) {
            for (int second = -safeRadius; second <= safeRadius; second++) {
                result.add(switch (normalAxis) {
                    case X -> new Offset(0, first, second);
                    case Y -> new Offset(first, 0, second);
                    case Z -> new Offset(first, second, 0);
                });
            }
        }
        return List.copyOf(result);
    }

    public enum Axis { X, Y, Z }
    public record Offset(int x, int y, int z) { }
}
