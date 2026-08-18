package com.ruskserver.moveearth_addtional.territory.create;

public final class CreateStressBalance {
    public static final int SCAN_RADIUS_BLOCKS = 128;
    public static final int REFRESH_INTERVAL_TICKS = 200;
    public static final double MAX_COUNTED_STRESS = 262_144.0D;
    public static final double SCORE_SCALE = 0.5D;

    private CreateStressBalance() {
    }

    public static double utilization(double networkStress, double networkCapacity) {
        if (!Double.isFinite(networkStress) || !Double.isFinite(networkCapacity)
                || networkStress <= 0.0D || networkCapacity <= 0.0D
                || networkStress > networkCapacity) {
            return 0.0D;
        }
        return Math.min(1.0D, networkStress / networkCapacity);
    }

    public static double industrialScore(double usedStress) {
        return industrialScore(usedStress, MAX_COUNTED_STRESS, SCORE_SCALE);
    }

    public static double industrialScore(double usedStress, double maxCountedStress, double scoreScale) {
        if (!Double.isFinite(usedStress) || usedStress <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(maxCountedStress) || maxCountedStress <= 0.0D
                || !Double.isFinite(scoreScale) || scoreScale < 0.0D) {
            throw new IllegalArgumentException("balance values must be finite and non-negative");
        }
        return scoreScale * Math.sqrt(Math.min(usedStress, maxCountedStress));
    }
}
