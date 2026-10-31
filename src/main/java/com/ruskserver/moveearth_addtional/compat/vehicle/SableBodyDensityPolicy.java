package com.ruskserver.moveearth_addtional.compat.vehicle;

import java.util.List;

/** Horizontal world-space density, independent of Minecraft and Sable initialization. */
public final class SableBodyDensityPolicy {
    private SableBodyDensityPolicy() { }

    public record Point(double x, double z) {
        public boolean within(Point other, double radius) {
            double dx = x - other.x, dz = z - other.z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    /** Checks the complete batch before moving any blocks, including all siblings in a split. */
    public static int projectedCount(List<Point> existing, List<Point> proposed, double radius) {
        return projectedCount(existing, proposed, radius, Integer.MAX_VALUE);
    }

    /** Returns a lower bound once over budget, avoiding quadratic scans of a huge fragment burst. */
    public static int projectedCount(List<Point> existing, List<Point> proposed, double radius, int budget) {
        var index = new SableBodyDensityIndex<Integer>(radius);
        var additions = new SableBodyDensityIndex<Integer>(radius);
        int id = 0;
        for (Point point : existing) index.put(id++, point);
        for (Point point : proposed) {
            additions.put(id, point);
            index.put(id++, point);
        }
        int highest = 0;
        for (Point center : proposed) {
            int count = index.count(center, budget == Integer.MAX_VALUE ? budget : budget + 1);
            if (count > budget) return count;
            highest = Math.max(highest, count);
        }
        // Adding a body also increases counts around existing anchors, even when the new anchor
        // itself sees only a few neighbors (for example, joining the edge of a crowded area).
        for (Point center : existing) {
            if (additions.count(center, 1) == 0) continue;
            int count = index.count(center, budget == Integer.MAX_VALUE ? budget : budget + 1);
            if (count > budget) return count;
            highest = Math.max(highest, count);
        }
        return highest;
    }
}
