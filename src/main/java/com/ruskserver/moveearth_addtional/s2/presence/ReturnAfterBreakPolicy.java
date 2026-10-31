package com.ruskserver.moveearth_addtional.s2.presence;

/** When a login counts as coming back after a break. Pure, so it is unit tested. */
public final class ReturnAfterBreakPolicy {
    /** Two days away, in real time. */
    public static final long BREAK_MILLIS = 2L * 24L * 60L * 60L * 1000L;

    private ReturnAfterBreakPolicy() { }

    /** {@code lastSeenMillis} is 0 when the player has never been seen, which is not a return. */
    public static boolean returned(long lastSeenMillis, long nowMillis) {
        return lastSeenMillis > 0L && nowMillis - lastSeenMillis >= BREAK_MILLIS;
    }
}
