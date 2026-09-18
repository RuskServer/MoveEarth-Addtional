package com.ruskserver.moveearth_addtional.compat.vehicle;

/** Pure threshold and displacement rules for the Sable void failsafe. */
final class SableVoidFailsafePolicy {
    private SableVoidFailsafePolicy() { }

    static boolean shouldRescue(double minimumY, int minimumBuildHeight, int triggerDepth) {
        return Double.isFinite(minimumY) && minimumY < minimumBuildHeight - Math.max(0, triggerDepth);
    }

    static double verticalDisplacement(double currentMinimumY, int terrainTop, int clearance) {
        if (!Double.isFinite(currentMinimumY)) return 0.0D;
        return terrainTop + Math.max(1, clearance) - currentMinimumY;
    }
}
