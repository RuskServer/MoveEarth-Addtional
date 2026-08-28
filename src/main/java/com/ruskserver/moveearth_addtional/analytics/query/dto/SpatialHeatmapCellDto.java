package com.ruskserver.moveearth_addtional.analytics.query.dto;

import java.util.UUID;

/**
 * 空間セル（32x32）のヒートマップ集計DTO
 */
public record SpatialHeatmapCellDto(
        String dimension,
        int cellX,
        int cellZ,
        String yBand,
        UUID groupOwnerUuid,
        String relation,
        int totalActiveSamples,
        int maxUniquePlayers
) {
}
