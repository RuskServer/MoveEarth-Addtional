package com.ruskserver.moveearth_addtional.analytics.model;

/** Periodic, low-overhead estimate of the work associated with a ticking chunk. */
public record ChunkLoadSample(
        long recordedAtEpochSec,
        String dimension,
        int chunkX,
        int chunkZ,
        int entityCount,
        int blockEntityCount,
        double loadScore
) {
}
