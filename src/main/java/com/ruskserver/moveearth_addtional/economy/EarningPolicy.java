package com.ruskserver.moveearth_addtional.economy;

/** Pure rules for who may earn TC and how much a new account may send. */
public final class EarningPolicy {
    /** Smaller than any deliberate mouse movement, larger than float noise on a still view. */
    public static final float LOOK_THRESHOLD_DEGREES = 0.5F;

    public enum Refusal { NONE, NO_NATION, IDLE }

    private EarningPolicy() { }

    /**
     * Only the client turns the player's view. Water, pistons and Create contraptions move a body
     * but never rotate it, so an idle mob-farm or autoclicker account never looks around.
     */
    public static boolean turned(float oldYaw, float oldPitch, float yaw, float pitch) {
        float yawDelta = Math.abs(wrapDegrees(yaw - oldYaw));
        return yawDelta >= LOOK_THRESHOLD_DEGREES || Math.abs(pitch - oldPitch) >= LOOK_THRESHOLD_DEGREES;
    }

    /** A player who has never turned since logging in is idle. */
    public static boolean idle(long lastTurnMillis, long nowMillis, long idleMillis) {
        return lastTurnMillis <= 0L || nowMillis - lastTurnMillis >= idleMillis;
    }

    public static Refusal refusal(boolean inNation, boolean idle) {
        if (!inNation) return Refusal.NO_NATION;
        return idle ? Refusal.IDLE : Refusal.NONE;
    }

    /** TC this sender may still send today; Long.MAX_VALUE for accounts past the new-account period. */
    public static long remainingTransfer(int playTicks, int newAccountTicks, long sentToday, long dailyLimit) {
        if (newAccountTicks <= 0 || playTicks >= newAccountTicks) return Long.MAX_VALUE;
        return Math.max(0L, dailyLimit - Math.max(0L, sentToday));
    }

    /** Same wall-clock day as Jobs income, so one opening night falls inside one day. */
    public static long day(long nowMillis) { return Math.floorDiv(nowMillis, 86_400_000L); }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360F;
        if (wrapped >= 180F) wrapped -= 360F;
        if (wrapped < -180F) wrapped += 360F;
        return wrapped;
    }
}
