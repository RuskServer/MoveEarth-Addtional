package com.ruskserver.moveearth_addtional.analytics.query.dto;

/** Per-detector activity summary for the analytics dashboard. */
public record DetectorSummaryDto(
        String detectorId,
        String detectorName,
        String dimension,
        double totalMemberMinutes,
        double totalVisitorMinutes,
        int totalIntrusionSessions,
        int maxDistinctMembers,
        int maxDistinctVisitors
) {
}
