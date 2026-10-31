package com.ruskserver.moveearth_addtional.compat.vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** One radius-sized grid cell per body; density scans visit nine cells, not the entire dimension. */
public final class SableBodyDensityIndex<K> {
    private record Cell(long x, long z) { }
    private final double radius;
    private final Map<Cell, Map<K, SableBodyDensityPolicy.Point>> cells = new HashMap<>();
    private final Map<K, Cell> locations = new HashMap<>();

    public SableBodyDensityIndex(double radius) {
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid radius");
        this.radius = radius;
    }

    private Cell cell(SableBodyDensityPolicy.Point point) {
        return new Cell((long) Math.floor(point.x() / radius), (long) Math.floor(point.z() / radius));
    }

    public void put(K id, SableBodyDensityPolicy.Point point) {
        remove(id);
        Cell cell = cell(point);
        cells.computeIfAbsent(cell, ignored -> new HashMap<>()).put(id, point);
        locations.put(id, cell);
    }

    public void remove(K id) {
        Cell cell = locations.remove(id);
        if (cell == null) return;
        var contents = cells.get(cell);
        contents.remove(id);
        if (contents.isEmpty()) cells.remove(cell);
    }

    public int count(SableBodyDensityPolicy.Point center, int stopAt) {
        Cell cell = cell(center);
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var contents = cells.get(new Cell(cell.x + dx, cell.z + dz));
                if (contents == null) continue;
                for (var point : contents.values()) {
                    if (center.within(point, radius) && ++count >= stopAt) return count;
                }
            }
        }
        return count;
    }

    public boolean anyWithin(SableBodyDensityPolicy.Point center, Predicate<SableBodyDensityPolicy.Point> predicate) {
        Cell cell = cell(center);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var contents = cells.get(new Cell(cell.x + dx, cell.z + dz));
                if (contents == null) continue;
                for (var point : contents.values()) {
                    if (center.within(point, radius) && predicate.test(point)) return true;
                }
            }
        }
        return false;
    }

    /** A body on a crowded area's edge may have few neighbors itself but still cause a nearby excess. */
    public int overcrowdedCountNear(SableBodyDensityPolicy.Point candidate, SableBodyDensityIndex<?> hotspots, int max) {
        return hotspots.anyWithin(candidate, center -> count(center, max + 1) > max) ? max + 1 : 0;
    }
}
