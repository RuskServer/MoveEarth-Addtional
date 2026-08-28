package com.ruskserver.moveearth_addtional.analytics.model;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 5分バケットごとの検知ブロック活動集計レコード
 */
public record DetectorActivityBucket(
        long bucketAtEpochSec,
        String dimension,
        String detectorPosHash,
        @Nullable UUID groupOwnerUuid,
        double memberMinutes,
        double visitorMinutes,
        int intrusionSessions,
        int distinctMembers,
        int distinctVisitors
) {
    public DetectorActivityBucket {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(detectorPosHash, "detectorPosHash must not be null");
    }
}
