package com.ruskserver.moveearth_addtional.compat.create;

/** Pure calculations used by the load-proportional steam boiler fuel integration. */
final class SteamFuelMath {
    private SteamFuelMath() {
    }

    /**
     * Share of a kinetic network's capacity in use. A network with no capacity
     * counts as fully loaded when anything still asks for stress, and idle otherwise.
     */
    static double load(float stress, float capacity) {
        if (!(capacity > 0.0F)) return stress > 0.0F ? 1.0D : 0.0D;
        return clamp01(stress / (double) capacity);
    }

    /** Burn ticks per game tick, where 1 is Create's constant rate. */
    static double burnRate(double load, double idleRate, double fullLoadRate) {
        double idle = Math.max(0.0D, idleRate);
        double full = Math.max(idle, fullLoadRate);
        return idle + (full - idle) * clamp01(load);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
