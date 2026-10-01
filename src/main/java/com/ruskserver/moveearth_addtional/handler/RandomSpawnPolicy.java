package com.ruskserver.moveearth_addtional.handler;

final class RandomSpawnPolicy {
    private RandomSpawnPolicy() {
    }

    static boolean meetsDistanceRequirements(double playerDistanceSqr, double lastSpawnDistanceSqr,
                                             double minimumPlayerDistanceSqr,
                                             double minimumLastSpawnDistanceSqr) {
        return playerDistanceSqr >= minimumPlayerDistanceSqr
                && lastSpawnDistanceSqr >= minimumLastSpawnDistanceSqr;
    }

    /**
     * A search that ran out of time may settle for a spot near the player's last
     * spawn, but never for one near a hostile player: that rule is checked again,
     * against where everyone is now, before the fallback is used.
     */
    static boolean fallbackAllowed(double playerDistanceSqr, double minimumPlayerDistanceSqr) {
        return playerDistanceSqr >= minimumPlayerDistanceSqr;
    }

    static double score(double playerDistanceSqr, double lastSpawnDistanceSqr,
                        double tieBreaker, double distanceCapSqr) {
        return Math.min(playerDistanceSqr, distanceCapSqr)
                + Math.min(lastSpawnDistanceSqr, distanceCapSqr) * 0.35D
                + tieBreaker;
    }

    static boolean retryAllowed(long currentTick, long retryAfterTick) {
        return retryAfterTick <= 0L || currentTick >= retryAfterTick;
    }

    static boolean isStoredFullChunk(String status) {
        return "full".equals(status) || status != null && status.endsWith(":full");
    }
}
