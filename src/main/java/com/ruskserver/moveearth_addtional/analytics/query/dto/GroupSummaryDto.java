package com.ruskserver.moveearth_addtional.analytics.query.dto;

import java.util.UUID;

/**
 * 検知グループ（拠点）の活動統計サマリーDTO
 */
public record GroupSummaryDto(
        UUID groupOwnerUuid,
        String ownerName,
        int detectorCount,
        double totalMemberMinutes,
        double totalVisitorMinutes,
        int totalIntrusionSessions,
        int maxDistinctMembers,
        int maxDistinctVisitors
) {
    public static GroupSummaryDto empty(UUID groupOwnerUuid, String ownerName) {
        return new GroupSummaryDto(
                groupOwnerUuid,
                ownerName != null ? ownerName : "Unknown",
                0,
                0.0,
                0.0,
                0,
                0,
                0
        );
    }
}
