package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.ServerSchedule;
import java.time.Instant;

/** A failed attack exhausts this opening session, rather than a number of future open-time ticks. */
public final class SiegeReattackPolicy {
    private SiegeReattackPolicy() { }

    public static long nextOpening(long nowEpochSecond) {
        var now = Instant.ofEpochSecond(nowEpochSecond).atZone(ServerSchedule.ZONE);
        var opening = now.toLocalDate().atTime(ServerSchedule.OPEN_HOUR, 0).atZone(ServerSchedule.ZONE);
        return (now.isBefore(opening) ? opening : opening.plusDays(1)).toEpochSecond();
    }

    public static boolean blocked(long untilOpening, long nowEpochSecond) {
        return untilOpening > nowEpochSecond;
    }

    /** Legacy GUI field: remaining time in the current opening, not wall-clock hours overnight. */
    public static long remainingOpenTicks(long untilOpening, long nowEpochSecond) {
        if (!blocked(untilOpening, nowEpochSecond)) return 0L;
        var date = Instant.ofEpochSecond(nowEpochSecond).atZone(ServerSchedule.ZONE).toLocalDate();
        long open = date.atTime(ServerSchedule.OPEN_HOUR, 0).atZone(ServerSchedule.ZONE).toEpochSecond();
        long close = date.atTime(ServerSchedule.CLOSE_HOUR, 0).atZone(ServerSchedule.ZONE).toEpochSecond();
        // Keep the protection visible while closed. The deadline, not this presentation value, is authoritative.
        return Math.max(1L, Math.max(0L, Math.min(untilOpening, close) - Math.max(nowEpochSecond, open)) * 20L);
    }
}
