package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure countdown rules shared by escorted and imprisoned captives. */
public final class CaptivityPolicy {
    private CaptivityPolicy() { }

    public static long advance(long remainingTicks, long elapsedOpenTicks) {
        if (remainingTicks <= 0L) return 0L;
        return Math.max(0L, remainingTicks - Math.max(0L, elapsedOpenTicks));
    }
}
