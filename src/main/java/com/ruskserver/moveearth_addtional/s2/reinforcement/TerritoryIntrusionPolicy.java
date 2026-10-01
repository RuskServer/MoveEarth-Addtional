package com.ruskserver.moveearth_addtional.s2.reinforcement;

/** Pure throttle for territory intrusion notices. */
public final class TerritoryIntrusionPolicy {
    public static final long NOTICE_INTERVAL_MILLIS = 300_000L;

    public enum Sweep { KEEP, FLUSH, PRUNE }

    private TerritoryIntrusionPolicy() { }

    public static boolean shouldNotify(long lastNoticeMillis, long nowMillis) {
        return lastNoticeMillis <= 0L || nowMillis - lastNoticeMillis >= NOTICE_INTERVAL_MILLIS;
    }

    /**
     * What the periodic sweep does with a breaker's entry: send breaks still unreported once the
     * throttle window has passed, and drop an entry with nothing unreported once it no longer
     * throttles anything (the next break would be reported at once either way).
     */
    public static Sweep sweep(long lastNoticeMillis, int unreported, long nowMillis) {
        if (!shouldNotify(lastNoticeMillis, nowMillis)) return Sweep.KEEP;
        return unreported > 0 ? Sweep.FLUSH : Sweep.PRUNE;
    }
}
