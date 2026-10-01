package com.ruskserver.moveearth_addtional.economy;

/** Pure rules for who may earn TC and how much a new account may send. */
public final class EarningPolicy {
    /** Smaller than any deliberate mouse movement, larger than float noise on a still view. */
    public static final float LOOK_THRESHOLD_DEGREES = 0.5F;

    public enum Refusal { NONE, NO_NATION, IDLE }

    /** Server-visible player actions that may or may not prove someone is at the keyboard. */
    public enum Activity { TURN_VIEW, CRAFT, SMELT, GUN_SMITH_CRAFT, BREAK_BLOCK, ATTACK }

    private EarningPolicy() { }

    /**
     * Only the client turns the player's view. Water, pistons and Create contraptions move a body
     * but never rotate it, so an idle mob-farm or autoclicker account never looks around.
     */
    public static boolean turned(float oldYaw, float oldPitch, float yaw, float pitch) {
        float yawDelta = Math.abs(wrapDegrees(yaw - oldYaw));
        return yawDelta >= LOOK_THRESHOLD_DEGREES || Math.abs(pitch - oldPitch) >= LOOK_THRESHOLD_DEGREES;
    }

    /**
     * Turning the view and deliberate GUI work (taking a crafted, smelted or gun smith table
     * result out) count as input, so a long crafting session keeps earning. Breaking blocks and
     * attacking do not: an autoclicker at a mob or crop farm does both without anyone present.
     */
    public static boolean countsAsInput(Activity activity) {
        return switch (activity) {
            case TURN_VIEW, CRAFT, SMELT, GUN_SMITH_CRAFT -> true;
            case BREAK_BLOCK, ATTACK -> false;
        };
    }

    /** The last input time after {@code activity} happened at {@code nowMillis}. */
    public static long lastInputAfter(long lastInputMillis, Activity activity, long nowMillis) {
        return countsAsInput(activity) ? Math.max(lastInputMillis, nowMillis) : lastInputMillis;
    }

    /** A player who has given no input since logging in is idle. */
    public static boolean idle(long lastInputMillis, long nowMillis, long idleMillis) {
        return lastInputMillis <= 0L || nowMillis - lastInputMillis >= idleMillis;
    }

    public static Refusal refusal(boolean inNation, boolean idle) {
        if (!inNation) return Refusal.NO_NATION;
        return idle ? Refusal.IDLE : Refusal.NONE;
    }

    /** Ticks of active play credited for each second online, checked once a second. */
    public static final int ACTIVE_STEP_TICKS = 20;

    /**
     * Active play time after one more second online: it grows only while the player is not
     * {@link #idle}, the same rule that decides earning, so an idle or AFK account never ages.
     */
    public static long activeTicksAfter(long activeTicks, boolean idle) {
        long current = Math.max(0L, activeTicks);
        if (idle) return current;
        return current > Long.MAX_VALUE - ACTIVE_STEP_TICKS ? Long.MAX_VALUE : current + ACTIVE_STEP_TICKS;
    }

    /** Active ticks still needed before the account is no longer new; 0 once past it or disabled. */
    public static long newAccountTicksLeft(long activeTicks, long newAccountTicks) {
        if (newAccountTicks <= 0L) return 0L;
        return Math.max(0L, newAccountTicks - Math.max(0L, activeTicks));
    }

    /** TC this sender may still send today; Long.MAX_VALUE for accounts past the new-account period. */
    public static long remainingTransfer(long activeTicks, long newAccountTicks, long sentToday, long dailyLimit) {
        if (newAccountTicksLeft(activeTicks, newAccountTicks) <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, dailyLimit - Math.max(0L, sentToday));
    }

    /**
     * Whether a new account may pay {@code amount} now. Player payments, treasury deposits,
     * market purchases and buy-order escrow all draw on the same allowance.
     */
    public static boolean allowsTransfer(long amount, long remaining) {
        return amount <= remaining;
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
