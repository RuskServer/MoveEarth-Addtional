package com.ruskserver.moveearth_addtional.economy;

/** Pure balance validation used before every ledger mutation. */
public final class LedgerRules {
    private LedgerRules() { }

    public static Check check(long sourceBalance, long targetBalance, long amount,
                              boolean hasSource, boolean hasTarget) {
        if (amount <= 0 || !hasSource && !hasTarget) return Check.INVALID;
        if (hasSource && sourceBalance < amount) return Check.INSUFFICIENT_FUNDS;
        if (hasTarget && targetBalance > Long.MAX_VALUE - amount) return Check.OVERFLOW;
        return Check.ALLOWED;
    }

    public enum Check { ALLOWED, INVALID, INSUFFICIENT_FUNDS, OVERFLOW }
}
