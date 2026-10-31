package com.ruskserver.moveearth_addtional.client.loading;

import java.util.function.BiPredicate;

/** Checks received data beneath the player's whole footprint, without requiring surrounding render sections. */
public final class EarlyWorldEntryPolicy {
    private EarlyWorldEntryPolicy() { }

    public static boolean footprintReady(double minX, double minZ, double maxX, double maxZ,
                                         BiPredicate<Integer, Integer> receivedChunk) {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX >= maxX || minZ >= maxZ
                || Math.abs(minX) > 30_000_000 || Math.abs(maxX) > 30_000_000
                || Math.abs(minZ) > 30_000_000 || Math.abs(maxZ) > 30_000_000) return false;
        int firstX = ((int) Math.floor(minX)) >> 4;
        int firstZ = ((int) Math.floor(minZ)) >> 4;
        int lastX = ((int) Math.floor(Math.nextDown(maxX))) >> 4;
        int lastZ = ((int) Math.floor(Math.nextDown(maxZ))) >> 4;
        // A normal player needs at most four chunks at a corner. Oversized modded forms use vanilla readiness.
        if (lastX - firstX > 1 || lastZ - firstZ > 1) return false;
        for (int x = firstX; x <= lastX; x++) {
            for (int z = firstZ; z <= lastZ; z++) {
                if (!receivedChunk.test(x, z)) return false;
            }
        }
        return true;
    }
}
