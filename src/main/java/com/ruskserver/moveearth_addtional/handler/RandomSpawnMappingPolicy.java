package com.ruskserver.moveearth_addtional.handler;

/** Deterministic, evenly distributed candidate generation for spawn pre-mapping. */
public final class RandomSpawnMappingPolicy {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

    private RandomSpawnMappingPolicy() { }

    public static Column column(int index, int centerX, int centerZ, int minimumRadius, int maximumRadius) {
        int safeIndex = Math.max(0, index);
        double fraction = ((safeIndex * 0.6180339887498949D) % 1.0D + 1.0D) % 1.0D;
        double minimumSquared = (double) minimumRadius * minimumRadius;
        double maximumSquared = (double) maximumRadius * maximumRadius;
        double radius = Math.sqrt(minimumSquared + fraction * Math.max(0.0D, maximumSquared - minimumSquared));
        double angle = safeIndex * GOLDEN_ANGLE;
        int x = centerX + (int) Math.round(Math.cos(angle) * radius);
        int z = centerZ + (int) Math.round(Math.sin(angle) * radius);
        // Sample away from chunk edges, where structures and neighboring chunk state are less predictable.
        x = (x & ~15) + 8;
        z = (z & ~15) + 8;
        return new Column(x, z);
    }

    public static boolean exhausted(int checked, int target) {
        return checked >= Math.max(256, target * 16);
    }

    public record Column(int x, int z) { }
}
