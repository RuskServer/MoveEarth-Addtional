package com.ruskserver.moveearth_addtional.client;

/**
 * When the welder asks the server for a fresh reinforcement scan.
 *
 * <p>Moving or changing dimension always asks at once: new ground needs new entries. The periodic
 * refresh is only a recovery net behind the server's deltas, so it backs off while scans keep coming
 * back with nothing new (the server does not even answer an unchanged scan) and returns to the base
 * interval as soon as anything changes.
 */
final class ReinforcementScanSchedule {
    static final int BASE_INTERVAL_TICKS = 200;
    static final int MAX_INTERVAL_TICKS = 800;

    private int interval = BASE_INTERVAL_TICKS;
    private int ticks;
    private long changeMark = Long.MIN_VALUE;

    /**
     * Advances one client tick.
     *
     * @param movedOrChangedDimension the player left the area the last scan covered
     * @param changeCount             {@link ReinforcementClientState#changeCount()} now
     * @return whether to request a scan this tick; call {@link #requested} when one is sent
     */
    boolean tick(boolean movedOrChangedDimension, long changeCount) {
        ticks++;
        if (movedOrChangedDimension) return true;
        if (ticks < interval) return false;
        interval = changeCount == changeMark
                ? Math.min(MAX_INTERVAL_TICKS, interval * 2)
                : BASE_INTERVAL_TICKS;
        return true;
    }

    void requested(long changeCount) {
        ticks = 0;
        changeMark = changeCount;
    }

    void reset() {
        ticks = 0;
        interval = BASE_INTERVAL_TICKS;
        changeMark = Long.MIN_VALUE;
    }

    int interval() {
        return interval;
    }
}
