package com.ruskserver.moveearth_addtional.analytics.model;

import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 5分バケットごとの空間セル滞在集計レコード
 */
public record SpatialActivityBucket(
        long bucketAtEpochSec,
        String dimension,
        int cellX,
        int cellZ,
        YBand yBand,
        @Nullable UUID groupOwnerUuid,
        GroupRelation relation,
        int activeSamples,
        int uniquePlayers
) {
    public SpatialActivityBucket {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(yBand, "yBand must not be null");
        Objects.requireNonNull(relation, "relation must not be null");
    }
}
