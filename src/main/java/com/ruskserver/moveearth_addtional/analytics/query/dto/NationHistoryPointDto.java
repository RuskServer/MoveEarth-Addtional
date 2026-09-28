package com.ruskserver.moveearth_addtional.analytics.query.dto;

/** One stored nation snapshot, for charting a nation over time. */
public record NationHistoryPointDto(long recordedAt, String nationUuid, String name, int members, int online,
                                    long treasury, int chunks, int cores, int vehicles) {
}
