package com.ruskserver.moveearth_addtional.s2.presence;

/**
 * How long a player has stood idle, by the analytics activity tracker's last-active time, and which of the
 * light-hearted idle advancements that earns. Pure, so it is unit tested.
 */
public final class AfkMilestonePolicy {
    /** 「浮気中」: half an hour without moving. */
    public static final long STRAYED_MILLIS = 30L * 60L * 1000L;
    /** 「地蔵」: two hours. */
    public static final long STATUE_MILLIS = 2L * 60L * 60L * 1000L;

    private AfkMilestonePolicy() { }

    /** Idle time; 0 while the tracker has no record ({@code lastActiveMillis} of 0). */
    public static long idleMillis(long lastActiveMillis, long nowMillis) {
        return lastActiveMillis <= 0L ? 0L : Math.max(0L, nowMillis - lastActiveMillis);
    }

    /** Idle long enough to count as away, as the analytics session accounting judges it. */
    public static boolean away(long lastActiveMillis, long nowMillis, long afkThresholdMillis) {
        return lastActiveMillis > 0L && idleMillis(lastActiveMillis, nowMillis) >= afkThresholdMillis;
    }
}
