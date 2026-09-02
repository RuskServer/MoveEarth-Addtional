package com.ruskserver.moveearth_addtional.tpa;

final class TpaPolicy {
    static final int TICKS_PER_HOUR = 20 * 60 * 60;

    private TpaPolicy() {
    }

    static Mode mode(int playTimeTicks, int beginnerUses, int maximumPlayTimeHours, int allowance) {
        if (isWithinBeginnerPlayTime(playTimeTicks, maximumPlayTimeHours)
                && allowance > 0
                && beginnerUses < allowance) {
            return Mode.BEGINNER;
        }
        return Mode.REGULAR;
    }

    static boolean isWithinBeginnerPlayTime(int playTimeTicks, int maximumPlayTimeHours) {
        return maximumPlayTimeHours > 0
                && playTimeTicks < maximumPlayTimeHours * TICKS_PER_HOUR;
    }

    static long remainingMillis(long cooldownUntilEpochMs, long nowEpochMs) {
        return Math.max(0L, cooldownUntilEpochMs - nowEpochMs);
    }

    enum Mode {
        BEGINNER,
        REGULAR
    }
}
