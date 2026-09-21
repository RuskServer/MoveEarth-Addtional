package com.ruskserver.moveearth_addtional.compat.cbc;

/** CBC's firing-rate selector: zero is off, and positions 11–15 mean 120–300 RPM. */
public final class CbcAutocannonRatePolicy {
    private CbcAutocannonRatePolicy() { }

    public static int normalizeSelector(int selector) {
        if (selector <= 0) return 0;
        return Math.min(15, Math.max(11, selector));
    }
}
