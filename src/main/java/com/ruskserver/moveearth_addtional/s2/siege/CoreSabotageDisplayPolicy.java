package com.ruskserver.moveearth_addtional.s2.siege;

/** Timing shared by the sabotage boss bar and its local warning sound. */
public final class CoreSabotageDisplayPolicy {
    public static final int INSTALL_TICKS = 20 * 20;
    public static final int FUSE_TICKS = 40 * 20;
    public static final int DEFUSE_TICKS = 5 * 20;

    private CoreSabotageDisplayPolicy() { }

    public static int remainingSeconds(int elapsedTicks, int totalTicks) {
        return Math.max(0, (totalTicks - elapsedTicks + 19) / 20);
    }

    public static int warningInterval(boolean armed, int fuseTicks) {
        if (!armed) return 20;
        int remaining = FUSE_TICKS - fuseTicks;
        return remaining <= 3 * 20 ? 5 : remaining <= 10 * 20 ? 10 : 20;
    }
}
