package com.ruskserver.moveearth_addtional.client.compat.coldsweat;

/** Pure severity rules for the Cold Sweat HUD. */
public final class TemperatureHudPolicy {
    private TemperatureHudPolicy() { }

    public static ThermalStatus classify(double world, double body, double freezingPoint,
                                         double burningPoint) {
        if (!Double.isFinite(world) || !Double.isFinite(body)
                || !Double.isFinite(freezingPoint) || !Double.isFinite(burningPoint)
                || burningPoint - freezingPoint < 0.01D) return ThermalStatus.UNAVAILABLE;

        double midpoint = (freezingPoint + burningPoint) / 2.0D;
        double coldSpan = Math.max(0.01D, midpoint - freezingPoint);
        double heatSpan = Math.max(0.01D, burningPoint - midpoint);
        double coldSeverity = world < midpoint ? (midpoint - world) / coldSpan : 0.0D;
        double heatSeverity = world > midpoint ? (world - midpoint) / heatSpan : 0.0D;
        coldSeverity = Math.max(coldSeverity, Math.max(0.0D, -body / 100.0D));
        heatSeverity = Math.max(heatSeverity, Math.max(0.0D, body / 100.0D));

        if (coldSeverity >= heatSeverity) return coldStatus(coldSeverity);
        return hotStatus(heatSeverity);
    }

    private static ThermalStatus coldStatus(double severity) {
        if (severity < 0.50D) return ThermalStatus.COMFORTABLE;
        if (severity < 0.80D) return ThermalStatus.COLD;
        if (severity < 1.0D) return ThermalStatus.SEVERE_COLD;
        return ThermalStatus.EXTREME_COLD;
    }

    private static ThermalStatus hotStatus(double severity) {
        if (severity < 0.50D) return ThermalStatus.COMFORTABLE;
        if (severity < 0.80D) return ThermalStatus.WARM;
        if (severity < 1.0D) return ThermalStatus.HOT;
        return ThermalStatus.EXTREME_HEAT;
    }

    public enum ThermalStatus {
        UNAVAILABLE(0, Direction.NEUTRAL, 0.0F),
        COMFORTABLE(0, Direction.NEUTRAL, 0.36F),
        COLD(1, Direction.COLD, 0.68F),
        SEVERE_COLD(2, Direction.COLD, 0.86F),
        EXTREME_COLD(3, Direction.COLD, 1.0F),
        WARM(1, Direction.HOT, 0.68F),
        HOT(2, Direction.HOT, 0.86F),
        EXTREME_HEAT(3, Direction.HOT, 1.0F);

        private final int danger;
        private final Direction direction;
        private final float opacity;

        ThermalStatus(int danger, Direction direction, float opacity) {
            this.danger = danger;
            this.direction = direction;
            this.opacity = opacity;
        }

        public int danger() { return danger; }
        public Direction direction() { return direction; }
        public float opacity() { return opacity; }
        public boolean expanded() { return danger >= 2; }
    }

    public enum Direction { NEUTRAL, COLD, HOT }
}
