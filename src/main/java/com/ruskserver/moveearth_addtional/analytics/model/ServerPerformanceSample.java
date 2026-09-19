package com.ruskserver.moveearth_addtional.analytics.model;

/** One minute server performance sample stored by the analytics collector. */
public record ServerPerformanceSample(
        long recordedAtEpochSec,
        double tps,
        double mspt,
        int loadedChunks,
        int onlinePlayers
) {
}
