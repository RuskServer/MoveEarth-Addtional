package com.ruskserver.moveearth_addtional.s2.dispatch;

/** Pure state and billing rules for dispatch contracts. */
public final class DispatchContractPolicy {
    private DispatchContractPolicy() { }

    public static long maximumCost(long pricePerOpenMinute, long maximumOpenTicks, int participants) {
        if (pricePerOpenMinute <= 0L || maximumOpenTicks <= 0L || participants <= 0) return 0L;
        long minutes = (maximumOpenTicks - 1L) / 1_200L + 1L;
        return saturatedMultiply(saturatedMultiply(pricePerOpenMinute, minutes), participants);
    }

    /** What the employer puts in escrow: the flat fee plus the most the per-minute rate can come to. */
    public static long escrow(long flatFee, long pricePerOpenMinute, long maximumOpenTicks, int participants) {
        long flat = Math.max(0L, flatFee);
        long timed = maximumCost(pricePerOpenMinute, maximumOpenTicks, participants);
        return flat > Long.MAX_VALUE - timed ? Long.MAX_VALUE : flat + timed;
    }

    /**
     * Whether a contract that ended for {@code reason} earns its flat fee. Only one that went into battle, and
     * ended without the provider's side walking out: the battle ending, its time running out, the employer
     * calling it off or ceasing to exist. A provider cancelling, a hired player leaving, or staff ending it
     * hands the fee back.
     */
    public static boolean flatFeeEarned(boolean activated, String reason) {
        if (!activated || reason == null) return false;
        return switch (reason) {
            case "siege_ended", "time_limit", "cancelled:employer", "employer_gone" -> true;
            default -> false;
        };
    }

    public static long earned(long pricePerOpenMinute, long billedTicks) {
        if (pricePerOpenMinute <= 0L || billedTicks <= 0L) return 0L;
        return saturatedMultiply(pricePerOpenMinute, billedTicks) / 1_200L;
    }

    public static boolean mayFund(boolean employerApproved, boolean providerApproved,
                                  int participantCount, int consentCount) {
        return employerApproved && providerApproved && participantCount > 0
                && consentCount == participantCount;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
