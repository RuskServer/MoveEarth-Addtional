package com.ruskserver.moveearth_addtional.analytics.event;

import java.util.UUID;

/**
 * One row of the game event log. Player, nation and position are optional;
 * {@code value} carries the event's number (an amount, a step index) and
 * {@code detail} a short free-form tag (a reason, an item id, an outcome).
 */
public record GameEventRecord(long occurredAtEpochSec, String type, UUID playerUuid, UUID nationUuid,
                              String dimension, Integer x, Integer y, Integer z, long value, String detail) {
    public static final int MAX_DETAIL = 160;

    public GameEventRecord {
        if (detail != null && detail.length() > MAX_DETAIL) detail = detail.substring(0, MAX_DETAIL);
    }
}
