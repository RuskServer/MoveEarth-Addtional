package com.ruskserver.moveearth_addtional.s2.combat;

/**
 * Per-player budget for the campfire scan: at most one scan per interval, whatever
 * happens to the rest progress in between. Ticks are the server tick count, which
 * unlike {@code Player.tickCount} does not restart when the player respawns.
 */
final class RestCampfireProbe {
    private final int intervalTicks;
    private boolean scanned;
    private long nextScanTick;

    RestCampfireProbe(int intervalTicks) {
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    /** True, and the budget is spent, when a scan may run at {@code now}. */
    boolean tryAcquire(long now) {
        // A clock that went backwards (another server in tests, a reload) must not stall scans.
        if (scanned && now >= nextScanTick - intervalTicks && now < nextScanTick) return false;
        scanned = true;
        nextScanTick = now + intervalTicks;
        return true;
    }
}
