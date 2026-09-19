package com.ruskserver.moveearth_addtional.analytics.model;

import java.util.UUID;

/** Aggregated sampled CPU time for one chunk during a bounded profiling session. */
public record ChunkProfileRecord(
        UUID sessionId,
        long startedAtEpochSec,
        long finishedAtEpochSec,
        String trigger,
        String dimension,
        int chunkX,
        int chunkZ,
        int sampledTicks,
        double averageTickMs,
        double maximumTickMs,
        double totalMs,
        double entityMs,
        double blockEntityMs,
        double scheduledTickMs,
        long entityCalls,
        long blockEntityCalls,
        long scheduledTickCalls
) {
}
