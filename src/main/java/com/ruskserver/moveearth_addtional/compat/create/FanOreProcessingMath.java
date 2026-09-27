package com.ruskserver.moveearth_addtional.compat.create;

/** Pure calculations used by the fan ore-processing balance. */
final class FanOreProcessingMath {
    private FanOreProcessingMath() {
    }

    /** {@code ticks} scaled by {@code multiplier}, never shorter than {@code ticks} and never overflowing. */
    static int scaledTicks(int ticks, double multiplier) {
        if (ticks <= 0 || !(multiplier > 1.0D)) return ticks;
        return (int) Math.min(Integer.MAX_VALUE / 16, Math.round(ticks * multiplier));
    }
}
