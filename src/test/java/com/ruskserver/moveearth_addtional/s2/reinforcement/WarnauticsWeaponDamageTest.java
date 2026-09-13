package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarnauticsWeaponDamageTest {
    @Test
    void classifiesSourceLessBreachingChargeAndMineWithoutOptionalClasses() {
        assertEquals(WarnauticsWeaponDamage.Kind.C4,
                WarnauticsWeaponDamage.classify("MEDIUM", ""));
        assertEquals(WarnauticsWeaponDamage.Kind.LARGE_MINE,
                WarnauticsWeaponDamage.classify("LARGE", null));
    }

    @Test
    void sourceEntityDistinguishesBombsAndCruiseMissile() {
        assertEquals(WarnauticsWeaponDamage.Kind.LARGE_BOMB,
                WarnauticsWeaponDamage.classify("LARGE", "large_bomb"));
        assertEquals(WarnauticsWeaponDamage.Kind.CRUISE_MISSILE,
                WarnauticsWeaponDamage.classify("LARGE", "cruise_missile"));
    }

    @Test
    void minesNeverBecomeStructuralOrCoreWeapons() {
        assertTrue(WarnauticsWeaponDamage.isMine(WarnauticsWeaponDamage.Kind.LARGE_MINE));
        assertFalse(WarnauticsWeaponDamage.canDamageCore(WarnauticsWeaponDamage.Kind.LARGE_MINE));
        assertFalse(WarnauticsWeaponDamage.canDamageCore(WarnauticsWeaponDamage.Kind.CRUISE_MISSILE));
        assertFalse(WarnauticsWeaponDamage.canDamageCore(WarnauticsWeaponDamage.Kind.UNKNOWN));
    }

    @Test
    void coreFalloffStopsAtConfiguredRadius() {
        assertEquals(16, WarnauticsWeaponDamage.distanceScaledDamage(16, 0.0D, 4.0D));
        assertEquals(8, WarnauticsWeaponDamage.distanceScaledDamage(16, 2.0D, 4.0D));
        assertEquals(0, WarnauticsWeaponDamage.distanceScaledDamage(16, 4.0D, 4.0D));
        assertEquals(0, WarnauticsWeaponDamage.distanceScaledDamage(16, 5.0D, 4.0D));
    }
}
