package com.ruskserver.moveearth_addtional.s2.dispatch;

/** Pure state and billing rules for dispatch contracts. */
public final class DispatchContractPolicy {
    private DispatchContractPolicy() { }

    public static long maximumCost(long pricePerOpenMinute, long maximumOpenTicks, int participants) {
        if (pricePerOpenMinute <= 0L || maximumOpenTicks <= 0L || participants <= 0) return 0L;
        long minutes = (maximumOpenTicks - 1L) / 1_200L + 1L;
        return saturatedMultiply(saturatedMultiply(pricePerOpenMinute, minutes), participants);
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
