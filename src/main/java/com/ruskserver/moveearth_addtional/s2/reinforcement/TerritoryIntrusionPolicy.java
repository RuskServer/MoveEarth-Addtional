package com.ruskserver.moveearth_addtional.s2.reinforcement;

/** Pure throttle for territory intrusion notices. */
public final class TerritoryIntrusionPolicy {
    public static final long NOTICE_INTERVAL_MILLIS = 300_000L;

    private TerritoryIntrusionPolicy() { }

    public static boolean shouldNotify(long lastNoticeMillis, long nowMillis) {
        return lastNoticeMillis <= 0L || nowMillis - lastNoticeMillis >= NOTICE_INTERVAL_MILLIS;
    }
}
