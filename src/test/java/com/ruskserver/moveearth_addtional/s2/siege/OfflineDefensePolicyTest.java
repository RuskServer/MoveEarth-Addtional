package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineDefensePolicyTest {
    @Test
    void activatesOnlyAfterGraceWhenNationIsEmpty() {
        long grace = 20L * 60_000L;
        assertEquals(1, OfflineDefensePolicy.divisor(true, grace * 2, grace, false, 3));
        assertEquals(1, OfflineDefensePolicy.divisor(false, grace - 1, grace, false, 3));
        assertEquals(3, OfflineDefensePolicy.divisor(false, grace, grace, false, 3));
    }

    @Test
    void rollingAndFallenSiegeSuppressOfflineDefense() {
        assertEquals(1, OfflineDefensePolicy.divisor(false, Long.MAX_VALUE, 0L, true, 3));
        assertTrue(OfflineDefensePolicy.siegeSuppresses(
                false, SiegeTimerPolicy.Phase.ROLLING, false));
        assertTrue(OfflineDefensePolicy.siegeSuppresses(
                true, SiegeTimerPolicy.Phase.INITIAL_LOCK, true));
        org.junit.jupiter.api.Assertions.assertFalse(OfflineDefensePolicy.siegeSuppresses(
                false, SiegeTimerPolicy.Phase.ROLLING, true));
        org.junit.jupiter.api.Assertions.assertFalse(OfflineDefensePolicy.siegeSuppresses(
                false, SiegeTimerPolicy.Phase.INITIAL_LOCK, false));
    }

    @Test
    void carriesSmallHitsSoThreeHitsApplyOneDamage() {
        var first = OfflineDefensePolicy.apply(1, 3, 0);
        var second = OfflineDefensePolicy.apply(1, 3, first.carriedUnits());
        var third = OfflineDefensePolicy.apply(1, 3, second.carriedUnits());
        assertEquals(0, first.appliedDamage());
        assertEquals(0, second.appliedDamage());
        assertEquals(1, third.appliedDamage());
        assertTrue(first.reduced());
    }

    @Test
    void dividesLargeSiegeHitsWithoutLosingRemainders() {
        var first = OfflineDefensePolicy.apply(40, 3, 0);
        var second = OfflineDefensePolicy.apply(40, 3, first.carriedUnits());
        var third = OfflineDefensePolicy.apply(40, 3, second.carriedUnits());
        assertEquals(13, first.appliedDamage());
        assertEquals(13, second.appliedDamage());
        assertEquals(14, third.appliedDamage());
    }
}
