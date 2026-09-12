package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LongAbsencePolicyTest {
    private static final long DAY = 86_400_000L;

    @Test
    void advancesThroughConfiguredRealTimeTiers() {
        assertEquals(LongAbsencePolicy.Tier.FULL, tier(false, 7 * DAY - 1));
        assertEquals(LongAbsencePolicy.Tier.THREE_QUARTERS, tier(false, 7 * DAY));
        assertEquals(LongAbsencePolicy.Tier.HALF, tier(false, 14 * DAY));
        assertEquals(LongAbsencePolicy.Tier.QUARTER, tier(false, 21 * DAY));
        assertEquals(LongAbsencePolicy.Tier.DISABLED, tier(false, 30 * DAY));
    }

    @Test
    void anOnlineMemberImmediatelyRestoresFullStrength() {
        assertEquals(LongAbsencePolicy.Tier.FULL, tier(true, 90 * DAY));
    }

    @Test
    void tierRatiosMatchSeventyFiveHalfAndQuarterStrength() {
        assertEquals(40, OfflineDefensePolicy.applyRatio(30,
                LongAbsencePolicy.Tier.THREE_QUARTERS.damageNumerator(),
                LongAbsencePolicy.Tier.THREE_QUARTERS.damageDenominator(), 0).appliedDamage());
        assertEquals(60, OfflineDefensePolicy.applyRatio(30, 2, 1, 0).appliedDamage());
        assertEquals(120, OfflineDefensePolicy.applyRatio(30, 4, 1, 0).appliedDamage());
        assertFalse(LongAbsencePolicy.Tier.DISABLED.reinforcementProtectionEnabled());
    }

    private static LongAbsencePolicy.Tier tier(boolean online, long elapsed) {
        return LongAbsencePolicy.tier(online, elapsed, 7 * DAY, 14 * DAY, 21 * DAY, 30 * DAY);
    }
}
