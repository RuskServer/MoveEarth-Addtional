package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * The nation tax: a share of each citizen's Jobs income, taken out of the award itself and paid into the
 * treasury, so no currency is created by it. Pure, so it is tested without a server.
 *
 * <p>Jobs pay a currency at a time, usually one, so a percentage of a single award is mostly a fraction. The
 * fraction is carried per player in hundredths and paid once it adds up to a whole currency.
 */
public final class NationTaxPolicy {
    public static final int MAX_PERCENT = 30;
    /** A raise waits this long after the last one; a cut never waits. */
    public static final long RAISE_COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L;

    private NationTaxPolicy() {
    }

    /**
     * @param tax      currency to the treasury now
     * @param net      currency to the player now
     * @param carry    hundredths of a currency owed, carried to the next award
     */
    public record Split(int tax, int net, int carry) { }

    public static int clamp(int percent) {
        return Math.max(0, Math.min(MAX_PERCENT, percent));
    }

    public static Split split(int award, int percent, int carry) {
        if (award <= 0) return new Split(0, Math.max(0, award), clampCarry(carry));
        int rate = clamp(percent);
        if (rate == 0) return new Split(0, award, clampCarry(carry));
        long owed = (long) award * rate + clampCarry(carry);
        // At most 30 % of the award plus under one currency of carry: never more than the award itself.
        int tax = (int) Math.min(award, owed / 100L);
        return new Split(tax, award - tax, (int) (owed - tax * 100L));
    }

    /** Whether the rate may go from {@code current} to {@code requested} now. */
    public static boolean mayChange(int current, int requested, long lastRaisedAt, long now) {
        if (requested < 0 || requested > MAX_PERCENT || requested == current) return false;
        return requested < current || lastRaisedAt <= 0L || now - lastRaisedAt >= RAISE_COOLDOWN_MILLIS;
    }

    /** Minutes, rounded up, until a raise is allowed; 0 when it already is. */
    public static long raiseWaitMinutes(long lastRaisedAt, long now) {
        if (lastRaisedAt <= 0L) return 0L;
        long left = lastRaisedAt + RAISE_COOLDOWN_MILLIS - now;
        return left <= 0L ? 0L : (left + 59_999L) / 60_000L;
    }

    private static int clampCarry(int carry) {
        return Math.max(0, Math.min(99, carry));
    }
}
