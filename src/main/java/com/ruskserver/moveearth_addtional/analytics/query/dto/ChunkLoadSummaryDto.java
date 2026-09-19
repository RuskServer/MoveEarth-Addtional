package com.ruskserver.moveearth_addtional.analytics.query.dto;

/** Aggregated load estimate for a chunk over a selected time window. */
public record ChunkLoadSummaryDto(
        String dimension,
        int chunkX,
        int chunkZ,
        int samples,
        double averageLoadScore,
        double maximumLoadScore,
        double averageEntities,
        double averageBlockEntities,
        long lastRecordedAtEpochSec
) {
}
