package com.ruskserver.moveearth_addtional.s2.territory;

/**
 * How long a core still being configured may hold land. A configuring core pays
 * no upkeep and cannot be besieged, so without a limit a free nation could hold
 * up to 81 chunks at a chokepoint indefinitely by never asking for validation.
 */
public final class ConfiguringReservationPolicy {
    /** One hour of server-open time, in ticks. */
    public static final long LIMIT_OPEN_TICKS = 60L * 60L * 20L;

    private ConfiguringReservationPolicy() { }

    /** Whether a reservation that began at {@code sinceOpenTicks} still holds at {@code nowOpenTicks}. */
    public static boolean live(long sinceOpenTicks, long nowOpenTicks) {
        return nowOpenTicks - sinceOpenTicks < LIMIT_OPEN_TICKS;
    }
}
