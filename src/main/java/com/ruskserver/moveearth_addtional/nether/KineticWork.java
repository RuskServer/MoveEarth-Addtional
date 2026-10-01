package com.ruskserver.moveearth_addtional.nether;

/**
 * Work measured in RPM-ticks: a machine turning at {@code n} RPM adds {@code n}
 * each tick. A job therefore takes the same stress × time at any speed; faster
 * shafts finish sooner only by drawing proportionally more stress, and running
 * machines in parallel does not make a single job cheaper.
 */
public final class KineticWork {
    public static final float MAX_RPM = 256.0F;
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private KineticWork() { }

    /** Work for a job that takes {@code minutes} at 256 RPM. */
    public static long required(double minutes) {
        return Math.max(1L, Math.round(MAX_RPM * minutes * TICKS_PER_MINUTE));
    }

    /** Work added this tick; nothing below the minimum speed. */
    public static long step(float speed, float minimumRpm) {
        float rpm = Math.min(MAX_RPM, Math.abs(speed));
        return rpm >= minimumRpm && rpm > 0.0F ? Math.round(rpm) : 0L;
    }

    /** Minutes one job takes at {@code rpm}. */
    public static double minutesAt(float rpm, long required) {
        return rpm <= 0.0F ? Double.POSITIVE_INFINITY : required / (double) (Math.round(rpm) * TICKS_PER_MINUTE);
    }
}
