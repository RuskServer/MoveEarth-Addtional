package com.ruskserver.moveearth_addtional.analytics.query.dto;

/** One game event for the web API, with the player's last known name joined in. */
public record GameEventDto(long occurredAt, String type, String playerUuid, String playerName, String nationUuid,
                           String dimension, Integer x, Integer y, Integer z, long value, String detail) {
}
