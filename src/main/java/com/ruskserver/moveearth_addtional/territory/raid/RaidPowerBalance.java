package com.ruskserver.moveearth_addtional.territory.raid;

public final class RaidPowerBalance {
    private RaidPowerBalance() {
    }

    public static double strength(double validStress, double scale, double maximum) {
        if (!Double.isFinite(validStress) || validStress <= 0.0D
                || !Double.isFinite(scale) || scale <= 0.0D
                || !Double.isFinite(maximum) || maximum <= 0.0D) {
            return 0.0D;
        }
        return Math.min(maximum, scale * Math.sqrt(validStress));
    }
}
