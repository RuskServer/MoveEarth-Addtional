package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure short-term offline-defense timing and fractional damage rules. */
public final class OfflineDefensePolicy {
    private OfflineDefensePolicy() { }

    public static int divisor(boolean anyMemberOnline, long offlineMillis, long graceMillis,
                              boolean siegeSuppressed, int configuredDivisor) {
        if (anyMemberOnline || siegeSuppressed || offlineMillis < Math.max(0L, graceMillis)) return 1;
        return Math.max(1, configuredDivisor);
    }

    public static DamageResult apply(int rawDamage, int divisor, int carriedUnits) {
        return applyRatio(rawDamage, 1, divisor, carriedUnits);
    }

    public static DamageResult applyRatio(int rawDamage, int numerator, int denominator, int carriedUnits) {
        int safeNumerator = Math.max(0, numerator);
        int safeDenominator = Math.max(1, denominator);
        if (rawDamage <= 0 || safeNumerator == 0) {
            return new DamageResult(0, safeNumerator, safeDenominator, Math.max(0, carriedUnits));
        }
        long units = (long) rawDamage * safeNumerator + Math.max(0, carriedUnits);
        return new DamageResult((int) Math.min(Integer.MAX_VALUE, units / safeDenominator),
                safeNumerator, safeDenominator, (int) (units % safeDenominator));
    }

    public static boolean siegeSuppresses(boolean fallen, SiegeTimerPolicy.Phase phase,
                                          boolean offlineDefenseAllowedAtStart) {
        return fallen || (phase == SiegeTimerPolicy.Phase.ROLLING && !offlineDefenseAllowedAtStart);
    }

    public record DamageResult(int appliedDamage, int numerator, int divisor, int carriedUnits) {
        public boolean reduced() { return numerator < divisor; }
    }
}
