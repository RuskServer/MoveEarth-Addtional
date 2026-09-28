package com.ruskserver.moveearth_addtional.analytics.query.dto;

/**
 * Game events grouped by one key: how many, the sum of their values and how many
 * distinct players took part.
 */
public record GameEventAggregateDto(String key, long count, long total, long players) {
}
