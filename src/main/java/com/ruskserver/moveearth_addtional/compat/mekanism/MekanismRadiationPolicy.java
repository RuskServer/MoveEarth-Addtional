package com.ruskserver.moveearth_addtional.compat.mekanism;

/** Pure radiation rules for territory. Kept free of Minecraft classes for unit tests. */
public final class MekanismRadiationPolicy {
    private MekanismRadiationPolicy() {
    }

    /** Where a radiation source sits, for choosing its decay rate. */
    public enum Zone { NEAR_CORE, WILDERNESS, TERRITORY }

    /** Factor a source keeps per decay step; never slower than Mekanism's own rate. */
    public static double decayRate(double mekanismRate, Zone zone, double wildernessRate, double coreRate) {
        double rate = switch (zone) {
            case NEAR_CORE -> coreRate;
            case WILDERNESS -> wildernessRate;
            case TERRITORY -> mekanismRate;
        };
        return Math.min(mekanismRate, Math.max(0.0D, rate));
    }

    /**
     * Squared distance Mekanism should divide a source's magnitude by. Exposure
     * falls with this value, so dividing it by the multiplier scales exposure by
     * the multiplier; a zero multiplier blocks cross-border exposure entirely.
     */
    public static double exposureDistanceSqr(double distanceSqr, boolean crossBorder, double multiplier) {
        if (!crossBorder || multiplier >= 1.0D) return distanceSqr;
        if (!(multiplier > 0.0D)) return Double.POSITIVE_INFINITY;
        return Math.max(1.0D, distanceSqr) / multiplier;
    }

    /** Decay steps (about seconds) for a source to halve at {@code rate}. */
    public static double halfLifeSteps(double rate) {
        if (!(rate > 0.0D)) return 0.0D;
        if (rate >= 1.0D) return Double.POSITIVE_INFINITY;
        return Math.log(0.5D) / Math.log(rate);
    }
}
