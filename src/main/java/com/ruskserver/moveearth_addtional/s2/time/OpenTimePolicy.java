package com.ruskserver.moveearth_addtional.s2.time;

/** Pure arithmetic for the persistent server-opening clock. */
public final class OpenTimePolicy {
    private OpenTimePolicy() { }

    public static long advance(long current, long elapsed) {
        long safeCurrent = Math.max(0L, current);
        long amount = Math.max(0L, elapsed);
        return safeCurrent > Long.MAX_VALUE - amount ? Long.MAX_VALUE : safeCurrent + amount;
    }
}
