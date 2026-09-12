package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure validation for player-entered peace terms. */
public final class PeaceTermsPolicy {
    public static final long MAX_GOLD_COMPENSATION = 1_000_000L;
    private PeaceTermsPolicy() { }

    public static boolean validCompensation(long gold) {
        return gold >= 0L && gold <= MAX_GOLD_COMPENSATION;
    }
}
