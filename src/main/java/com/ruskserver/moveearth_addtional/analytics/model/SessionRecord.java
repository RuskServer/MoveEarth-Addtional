package com.ruskserver.moveearth_addtional.analytics.model;

import java.util.Objects;
import java.util.UUID;

/**
 * プレイヤーセッション集計レコード
 */
public record SessionRecord(
        UUID sessionId,
        UUID playerUuid,
        String lastKnownName,
        long joinedAtEpochSec,
        long leftAtEpochSec,
        int onlineSeconds,
        int activeSeconds,
        int afkSeconds
) {
    public SessionRecord {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        Objects.requireNonNull(lastKnownName, "lastKnownName must not be null");
    }
}
