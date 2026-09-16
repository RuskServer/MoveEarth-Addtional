package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;

/** Initial upkeep estimate, expressed in Lightman's Currency gold-coin units per cycle. */
public final class TerritoryUpkeepPolicy {
    public static final int CHUNKS_PER_COIN = 8;
    public static final long OUTPOST_BASE_COST = 8L;

    private TerritoryUpkeepPolicy() {
    }

    public static long calculate(int uniqueControlledChunks, int activeOutposts) {
        return calculate(uniqueControlledChunks, activeOutposts, CHUNKS_PER_COIN, OUTPOST_BASE_COST);
    }

    public static long calculateConfigured(int uniqueControlledChunks, int activeOutposts) {
        return calculateConfigured(uniqueControlledChunks, activeOutposts, 0);
    }

    public static long calculateConfigured(int uniqueControlledChunks, int activeOutposts, int vehicleCores) {
        return calculate(uniqueControlledChunks, activeOutposts, vehicleCores,
                S2TerritoryConfig.chunksPerCoin(), S2TerritoryConfig.outpostBaseCost(),
                S2TerritoryConfig.vehicleCoreCost());
    }

    static long calculate(int uniqueControlledChunks, int activeOutposts, int vehicleCores,
                          int chunksPerCoin, long outpostBaseCost, long vehicleCoreCost) {
        return calculate(uniqueControlledChunks, activeOutposts, chunksPerCoin, outpostBaseCost)
                + Math.max(0L, vehicleCores) * Math.max(0L, vehicleCoreCost);
    }

    static long calculate(int uniqueControlledChunks, int activeOutposts,
                          int chunksPerCoin, long outpostBaseCost) {
        int safeChunksPerCoin = Math.max(1, chunksPerCoin);
        long chunkCost = uniqueControlledChunks <= 0 ? 0L
                : (uniqueControlledChunks + safeChunksPerCoin - 1L) / safeChunksPerCoin;
        long outposts = Math.max(0, activeOutposts);
        long escalatingOutpostCost = Math.max(0L, outpostBaseCost) * outposts * (outposts + 1L) / 2L;
        return chunkCost + escalatingOutpostCost;
    }
}
