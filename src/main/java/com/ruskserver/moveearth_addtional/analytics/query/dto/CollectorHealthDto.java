package com.ruskserver.moveearth_addtional.analytics.query.dto;

/**
 * コレクターおよびストレージのヘルス状態サマリーDTO
 */
public record CollectorHealthDto(
        long recordedAtEpochSec,
        int queueDepth,
        long droppedEventsTotal,
        long lastFlushDurationMs,
        long databaseSizeBytes
) {
    public static CollectorHealthDto empty() {
        return new CollectorHealthDto(
                System.currentTimeMillis() / 1000L,
                0,
                0L,
                0L,
                0L
        );
    }
}
