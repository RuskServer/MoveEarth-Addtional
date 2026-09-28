package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.time.OpenTimePolicy;

/**
 * Timing of a declared alliance termination. Durations are in server-opening ticks: the alliance,
 * with its Siege protection and territory grants, lasts until the notice has run out.
 */
public final class AllianceTerminationPolicy {
    /** Two hours of server-opening time. */
    public static final long NOTICE_OPEN_TICKS = 2L * 60L * 60L * 20L;

    private AllianceTerminationPolicy() { }

    public static long endsAt(long openNow) {
        return OpenTimePolicy.advance(openNow, NOTICE_OPEN_TICKS);
    }

    public static boolean pending(long endsAt) {
        return endsAt > 0L;
    }

    public static long remaining(long endsAt, long openNow) {
        return pending(endsAt) ? Math.max(0L, endsAt - Math.max(0L, openNow)) : 0L;
    }

    public static boolean expired(long endsAt, long openNow) {
        return pending(endsAt) && Math.max(0L, openNow) >= endsAt;
    }

    public enum Decision { ALLOWED, NOT_ALLIED, ALREADY_PENDING, NOT_PENDING, NOT_DECLARER }

    public static Decision declare(boolean allied, boolean alreadyPending) {
        if (!allied) return Decision.NOT_ALLIED;
        return alreadyPending ? Decision.ALREADY_PENDING : Decision.ALLOWED;
    }

    /** Only the nation that gave notice may withdraw it. */
    public static Decision cancel(boolean allied, boolean pending, boolean declaredByActor) {
        if (!allied) return Decision.NOT_ALLIED;
        if (!pending) return Decision.NOT_PENDING;
        return declaredByActor ? Decision.ALLOWED : Decision.NOT_DECLARER;
    }
}
