package com.ruskserver.moveearth_addtional.compat.sentry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentryTurretRulesTest {
    private static final double LIMIT = SentryTurretConfig.DEFAULT_MAX_DEPRESSION;
    private static final double MAX_DAMAGE = SentryTurretConfig.DEFAULT_MAX_GUN_DAMAGE;

    @Test
    void depressionIsMeasuredBelowHorizontal() {
        assertEquals(45.0, SentryTurretRules.depressionDegrees(0, 10, 0, 10, 0, 0), 1e-9);
        assertEquals(0.0, SentryTurretRules.depressionDegrees(0, 5, 0, 3, 5, 4), 1e-9);
        assertEquals(-45.0, SentryTurretRules.depressionDegrees(0, 0, 0, 0, 10, 10), 1e-9);
        assertEquals(90.0, SentryTurretRules.depressionDegrees(0, 10, 0, 0, 0, 0), 1e-9);
    }

    @Test
    void turretOnAWallCannotCoverTheWallsFoot() {
        // Muzzle 8 blocks up: someone 2 blocks out from the wall is out of reach,
        // someone 30 blocks out is not.
        assertTrue(SentryTurretRules.tooSteep(SentryTurretRules.depressionDegrees(0, 8, 0, 2, 0, 0), LIMIT));
        assertFalse(SentryTurretRules.tooSteep(SentryTurretRules.depressionDegrees(0, 8, 0, 30, 0, 0), LIMIT));
        assertFalse(SentryTurretRules.tooSteep(SentryTurretRules.depressionDegrees(0, 0, 0, 5, 20, 0), LIMIT));
    }

    @Test
    void semiAutomaticSidearmsAreAccepted() {
        assertTrue(SentryTurretRules.gunAllowed(true, 1, 6.0F, false, MAX_DAMAGE));   // Glock 17
        assertTrue(SentryTurretRules.gunAllowed(true, 1, 10.0F, false, MAX_DAMAGE));  // P320
    }

    @Test
    void heavierAutomaticSpreadAndExplosiveGunsAreRefused() {
        assertFalse(SentryTurretRules.gunAllowed(true, 1, 11.0F, false, MAX_DAMAGE));  // M1911
        assertFalse(SentryTurretRules.gunAllowed(false, 1, 8.0F, false, MAX_DAMAGE));  // minigun
        assertFalse(SentryTurretRules.gunAllowed(false, 1, 7.0F, false, MAX_DAMAGE));  // Vector, semi/burst/auto
        assertFalse(SentryTurretRules.gunAllowed(true, 8, 5.0F, false, MAX_DAMAGE));   // pellet shotgun
        assertFalse(SentryTurretRules.gunAllowed(true, 1, 10.0F, true, MAX_DAMAGE));   // M320
    }

    @Test
    void ammoModsAndOtherHitChangingAttachmentsAreCaught() {
        assertTrue(SentryTurretRules.attachmentChangesHits(java.util.List.of("weight", "ads", "damage")));   // HP
        assertTrue(SentryTurretRules.attachmentChangesHits(java.util.List.of("explosion", "rpm")));          // HE
        assertTrue(SentryTurretRules.attachmentChangesHits(java.util.List.of("ignite")));                    // incendiary
        assertFalse(SentryTurretRules.attachmentChangesHits(java.util.List.of("weight", "ads", "recoil")));  // a scope
    }
}
