package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure real-time inactivity tiers for abandoned-nation protection. */
public final class LongAbsencePolicy {
    private LongAbsencePolicy() { }

    public static Tier tier(boolean anyMemberOnline, long offlineMillis,
                            long fullStrengthMillis, long halfStrengthMillis,
                            long quarterStrengthMillis, long disabledMillis) {
        if (anyMemberOnline) return Tier.FULL;
        long elapsed = Math.max(0L, offlineMillis);
        if (elapsed >= Math.max(0L, disabledMillis)) return Tier.DISABLED;
        if (elapsed >= Math.max(0L, quarterStrengthMillis)) return Tier.QUARTER;
        if (elapsed >= Math.max(0L, halfStrengthMillis)) return Tier.HALF;
        if (elapsed >= Math.max(0L, fullStrengthMillis)) return Tier.THREE_QUARTERS;
        return Tier.FULL;
    }

    public enum Tier {
        FULL(1, 1, true),
        THREE_QUARTERS(4, 3, true),
        HALF(2, 1, true),
        QUARTER(4, 1, true),
        DISABLED(4, 1, false);

        private final int damageNumerator;
        private final int damageDenominator;
        private final boolean reinforcementProtectionEnabled;

        Tier(int damageNumerator, int damageDenominator, boolean reinforcementProtectionEnabled) {
            this.damageNumerator = damageNumerator;
            this.damageDenominator = damageDenominator;
            this.reinforcementProtectionEnabled = reinforcementProtectionEnabled;
        }

        public int damageNumerator() { return damageNumerator; }
        public int damageDenominator() { return damageDenominator; }
        public boolean reinforcementProtectionEnabled() { return reinforcementProtectionEnabled; }
        public boolean weakened() { return this != FULL; }
    }
}
