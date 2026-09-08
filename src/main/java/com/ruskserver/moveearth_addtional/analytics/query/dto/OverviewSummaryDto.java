package com.ruskserver.moveearth_addtional.analytics.query.dto;

/**
 * サーバー全体の総合概況メトリクスを表現するDTO
 */
public record OverviewSummaryDto(
        int activeUniquePlayers,
        long totalActiveSeconds,
        long totalOnlineSeconds,
        long totalAfkSeconds,
        long totalBreaks,
        long totalPlaces,
        long totalCrafts,
        long totalPveKills,
        long totalPvpKills,
        long totalDeaths,
        double totalJobsXp,
        double totalDistanceBlocks
) {
    public static OverviewSummaryDto empty() {
        return new OverviewSummaryDto(0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0);
    }
}
