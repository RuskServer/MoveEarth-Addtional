package com.ruskserver.moveearth_addtional.analytics.model;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 5分バケットごとのプレイヤー活動集計レコード
 */
public record PlayerActivityBucket(
        long bucketAtEpochSec,
        UUID playerUuid,
        String dimension,
        @Nullable UUID groupOwnerUuid,
        int activeSeconds,
        double distanceBlocks,
        int breaks,
        int places,
        int crafts,
        int pveKills,
        int pvpKills,
        int deaths,
        double jobsXp,
        int tpaSuccesses
) {
    public PlayerActivityBucket {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
    }
}
