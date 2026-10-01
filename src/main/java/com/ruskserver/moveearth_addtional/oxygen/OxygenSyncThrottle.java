package com.ruskserver.moveearth_addtional.oxygen;

/**
 * Decides when the oxygen HUD packet is worth sending. It used to go out twice a
 * second to every survival player whether anything had changed or not.
 *
 * <p>A change of zone, mask or activity is sent at once; gauges that merely move are
 * sent at most once per {@code gaugeIntervalTicks}; nothing is sent while idle.</p>
 */
final class OxygenSyncThrottle {
    /** Gauge resolution: a thousandth, finer than the HUD's whole percent. */
    static final int GAUGE_STEPS = 1000;

    private final int gaugeIntervalTicks;
    private Snapshot lastSent;
    private long lastSentTick;

    OxygenSyncThrottle(int gaugeIntervalTicks) {
        this.gaugeIntervalTicks = Math.max(1, gaugeIntervalTicks);
    }

    static int gauge(float fraction) {
        float clamped = Math.max(0.0F, Math.min(1.0F, fraction));
        return Math.round(clamped * GAUGE_STEPS);
    }

    /** True when {@code next} should be sent now; the caller then sends it. */
    boolean shouldSend(Snapshot next, long now) {
        boolean send;
        if (lastSent == null || !lastSent.sameFlags(next)) {
            send = true;
        } else if (lastSent.equals(next)) {
            send = false;
        } else {
            send = now - lastSentTick >= gaugeIntervalTicks || now < lastSentTick;
        }
        if (send) {
            lastSent = next;
            lastSentTick = now;
        }
        return send;
    }

    record Snapshot(int oxygenGauge, int filterGauge, boolean hasGasMask, boolean danger, boolean extreme,
                    float consumptionRate, boolean sprinting, boolean mining, boolean combat) {
        boolean sameFlags(Snapshot other) {
            return hasGasMask == other.hasGasMask && danger == other.danger && extreme == other.extreme
                    && Float.compare(consumptionRate, other.consumptionRate) == 0
                    && sprinting == other.sprinting && mining == other.mining && combat == other.combat
                    // Running out is a state change the HUD must show at once, not a gauge step.
                    && (oxygenGauge == 0) == (other.oxygenGauge == 0)
                    && (filterGauge == 0) == (other.filterGauge == 0);
        }
    }
}
