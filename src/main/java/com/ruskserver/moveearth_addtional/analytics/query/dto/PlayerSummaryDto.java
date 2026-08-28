package com.ruskserver.moveearth_addtional.analytics.query.dto;

import java.util.UUID;

/**
 * プレイヤーの活動統計サマリーDTO
 */
public record PlayerSummaryDto(
        UUID playerUuid,
        String lastKnownName,
        long firstSeenAtEpochSec,
        long lastSeenAtEpochSec,
        int sessionCount,
        long totalOnlineSeconds,
        long totalActiveSeconds,
        long totalAfkSeconds,
        long avgSessionDurationSeconds,
        int activeDays,
        long totalBreaks,
        long totalPlaces,
        long totalCrafts,
        long totalPveKills,
        long totalPvpKills,
        long totalDeaths,
        double totalJobsXp,
        int totalTpaSuccesses,
        double totalDistanceBlocks,
        String primaryDimension,
        UUID primaryGroupOwnerUuid
) {
    public static PlayerSummaryDto empty(UUID playerUuid, String name) {
        return new PlayerSummaryDto(
                playerUuid,
                name != null ? name : "Unknown",
                0L,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0,
                0,
                0.0,
                "minecraft:overworld",
                null
        );
    }
}
