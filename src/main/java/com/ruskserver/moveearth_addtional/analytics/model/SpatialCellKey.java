package com.ruskserver.moveearth_addtional.analytics.model;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 空間活動集計におけるセル集約単位のキー
 */
public record SpatialCellKey(
        String dimension,
        int cellX,
        int cellZ,
        YBand yBand,
        @Nullable UUID groupOwnerUuid,
        GroupRelation relation
) {
    public SpatialCellKey {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(yBand, "yBand must not be null");
        Objects.requireNonNull(relation, "relation must not be null");
    }

    /**
     * ブロック座標を32ブロックセル座標へ変換
     */
    public static int toCellCoordinate(double blockCoord) {
        int blockInt = (int) Math.floor(blockCoord);
        return Math.floorDiv(blockInt, AnalyticsConfig.CELL_SIZE_BLOCKS);
    }

    /**
     * 座標値からSpatialCellKeyを生成
     */
    public static SpatialCellKey of(
            String dimension,
            double x,
            double y,
            double z,
            @Nullable UUID groupOwnerUuid,
            GroupRelation relation
    ) {
        return new SpatialCellKey(
                dimension,
                toCellCoordinate(x),
                toCellCoordinate(z),
                YBand.fromY(y),
                groupOwnerUuid,
                relation
        );
    }
}
