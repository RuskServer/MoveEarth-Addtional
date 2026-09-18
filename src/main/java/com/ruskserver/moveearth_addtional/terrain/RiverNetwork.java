package com.ruskserver.moveearth_addtional.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable spatial index of connected channel segments in tile-relative blocks. */
final class RiverNetwork {
    private static final int BIN_SIZE = 64;
    private final Map<Long, List<Segment>> bins;
    private final double reach;

    RiverNetwork(List<Segment> segments, RiverShape shape) {
        this(segments, requiredReach(segments, shape));
    }

    // Use one network-wide radius to preserve nearest-channel selection where
    // narrow and wide channels compete. The old raster clamp is unrelated.
    static double requiredReach(List<Segment> segments, RiverShape shape) {
        double width = 0;
        for (Segment s : segments) width = Math.max(width, Math.max(s.width0, s.width1));
        return Math.max(TerrainField.RIVER_BIOME_RADIUS, Math.max(shape.waterRadius(),
                width * 0.5 + RiverProfile.bank(width, shape)));
    }

    RiverNetwork(List<Segment> segments, double reach) {
        this.reach = reach;
        Map<Long, List<Segment>> index = new HashMap<>();
        for (Segment s : segments) {
            for (int z = bin(Math.min(s.z0, s.z1) - reach); z <= bin(Math.max(s.z0, s.z1) + reach); z++) {
                for (int x = bin(Math.min(s.x0, s.x1) - reach); x <= bin(Math.max(s.x0, s.x1) + reach); x++) {
                    index.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(s);
                }
            }
        }
        index.replaceAll((key, value) -> List.copyOf(value));
        bins = Map.copyOf(index);
    }

    Sample sample(double x, double z) {
        double best = reach * reach;
        double bestWidth = 0, bestWater = 0;
        for (Segment s : bins.getOrDefault(key(bin(x), bin(z)), List.of())) {
            double dx = s.x1 - s.x0, dz = s.z1 - s.z0;
            double length = dx * dx + dz * dz;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1,
                    ((x - s.x0) * dx + (z - s.z0) * dz) / length));
            double ex = x - s.x0 - t * dx, ez = z - s.z0 - t * dz;
            double distance = ex * ex + ez * ez;
            double width = s.width0 + t * (s.width1 - s.width0);
            if (distance < best || (distance == best && width > bestWidth)) {
                best = distance;
                bestWidth = width;
                bestWater = s.water0 + t * (s.water1 - s.water0);
            }
        }
        return new Sample(Math.sqrt(best), bestWidth, bestWater);
    }

    private static int bin(double coordinate) { return (int) Math.floor(coordinate / BIN_SIZE); }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    record Segment(double x0, double z0, double x1, double z1,
                   double width0, double width1, double water0, double water1) { }
    record Sample(double distance, double width, double water) { }
}
