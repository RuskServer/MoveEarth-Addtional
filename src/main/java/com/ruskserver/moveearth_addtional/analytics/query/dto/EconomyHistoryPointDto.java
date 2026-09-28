package com.ruskserver.moveearth_addtional.analytics.query.dto;

/** One stored money supply snapshot. */
public record EconomyHistoryPointDto(long recordedAt, long playerBalances, long nationBalances,
                                     long escrowBalances, int nations, int playersInNations, int onlinePlayers,
                                     int openOrders) {
}
