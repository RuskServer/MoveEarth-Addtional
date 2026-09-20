package com.ruskserver.moveearth_addtional.handler;

/** Converts usable land area into a bounded random-spawn pool capacity. */
public final class RandomSpawnCapacityPolicy {
    static final double LAND_BLOCKS_PER_POINT = 40_000.0D;
    static final int MINIMUM_TARGET = 64;
    static final int MAXIMUM_TARGET = 8_192;

    private RandomSpawnCapacityPolicy() { }

    public static int targetForLandArea(double usableLandBlocks) {
        if (!Double.isFinite(usableLandBlocks) || usableLandBlocks <= 0.0D) return 0;
        return Math.max(MINIMUM_TARGET, Math.min(MAXIMUM_TARGET,
                (int) Math.ceil(usableLandBlocks / LAND_BLOCKS_PER_POINT)));
    }
}
