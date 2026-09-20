package com.ruskserver.moveearth_addtional.compat.create;

/** Pure calculations used by the Create water-wheel balancing integration. */
final class WaterWheelBalanceMath {
    private WaterWheelBalanceMath() {
    }

    static double densityMultiplier(double activeUnits, double fullPowerUnits) {
        if (!(activeUnits > 0.0D)) return 1.0D;
        return clamp01(fullPowerUnits / activeUnits);
    }

    static double combinedMultiplier(double base, double source, double density, double sourcePenaltyStrength) {
        double sourceFactor = 1.0D - (1.0D - clamp01(source)) * clamp01(sourcePenaltyStrength);
        return Math.max(0.0D, base) * sourceFactor * clamp01(density);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
