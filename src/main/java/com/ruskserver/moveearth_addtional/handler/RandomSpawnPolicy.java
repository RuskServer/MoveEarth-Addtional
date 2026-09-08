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

    static double score(double playerDistanceSqr, double lastSpawnDistanceSqr,
                        double tieBreaker, double distanceCapSqr) {
        return Math.min(playerDistanceSqr, distanceCapSqr)
                + Math.min(lastSpawnDistanceSqr, distanceCapSqr) * 0.35D
                + tieBreaker;
    }
}
