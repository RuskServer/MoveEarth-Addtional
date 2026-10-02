package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleProtectionPolicyTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test void aVehicleIsDefendedByItsOwnerNotTheLand() {
        assertEquals(OWNER, VehicleProtectionPolicy.defendingNation(OWNER, OTHER));
        assertEquals(OWNER, VehicleProtectionPolicy.defendingNation(OWNER, null));
        assertEquals(OTHER, VehicleProtectionPolicy.defendingNation(null, OTHER));
        assertNull(VehicleProtectionPolicy.defendingNation(null, null));
    }

    @Test void offlineDefenseCoversOnlyAVehicleInsideItsOwnTerritory() {
        assertTrue(VehicleProtectionPolicy.offlineDefenseApplies(OWNER, OWNER));
        assertFalse(VehicleProtectionPolicy.offlineDefenseApplies(OWNER, OTHER));
        assertFalse(VehicleProtectionPolicy.offlineDefenseApplies(OWNER, null));
        assertFalse(VehicleProtectionPolicy.offlineDefenseApplies(null, null));
    }

    @Test void aFallenNationsTruceShieldsItsVehiclesOnlyAtHome() {
        assertTrue(VehicleProtectionPolicy.settlementTruceApplies(true, OWNER, OWNER));
        // Out raiding in someone else's land, or in the wilderness, the truce does not follow it.
        assertFalse(VehicleProtectionPolicy.settlementTruceApplies(true, OWNER, OTHER));
        assertFalse(VehicleProtectionPolicy.settlementTruceApplies(true, OWNER, null));
        assertFalse(VehicleProtectionPolicy.settlementTruceApplies(false, OWNER, OWNER));
    }

    @Test void divisorFollowsTheTerritoryIncludingItsSiegeSuppression() {
        assertEquals(3, VehicleProtectionPolicy.offlineDivisor(true, false, 3));
        // Suppressed by a rolling Siege started while defenders were online: the territory divisor is 1.
        assertEquals(1, VehicleProtectionPolicy.offlineDivisor(true, false, 1));
        assertEquals(1, VehicleProtectionPolicy.offlineDivisor(false, false, 3));
        assertEquals(1, VehicleProtectionPolicy.offlineDivisor(true, false, 0));
    }

    @Test void longAbsenceNeverMakesAVehicleWeakerThanFullStrength() {
        assertEquals(1, VehicleProtectionPolicy.offlineDivisor(true, true, 3));
    }

    @Test void ownAndAlliedFireIsFriendly() {
        assertTrue(VehicleProtectionPolicy.friendly(OWNER, OWNER, false));
        assertTrue(VehicleProtectionPolicy.friendly(OTHER, OWNER, true));
        assertFalse(VehicleProtectionPolicy.friendly(OTHER, OWNER, false));
        assertFalse(VehicleProtectionPolicy.friendly(null, OWNER, false));
    }

    @Test void hitNoticesAtMostEveryFiveMinutesPerVehicleAndAttacker() {
        assertTrue(VehicleProtectionPolicy.hitNoticeDue(0L, 1_000L));
        assertFalse(VehicleProtectionPolicy.hitNoticeDue(1_000L, 1_000L + 299_999L));
        assertTrue(VehicleProtectionPolicy.hitNoticeDue(1_000L, 1_000L + 300_000L));
    }
}
