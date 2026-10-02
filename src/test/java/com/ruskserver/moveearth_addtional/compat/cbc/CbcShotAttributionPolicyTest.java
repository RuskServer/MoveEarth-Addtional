package com.ruskserver.moveearth_addtional.compat.cbc;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbcShotAttributionPolicyTest {
    private static final UUID PASSENGER = UUID.randomUUID();
    private static final UUID PLACER = UUID.randomUUID();
    private static final UUID VEHICLE_NATION = UUID.randomUUID();
    private static final UUID TERRITORY_NATION = UUID.randomUUID();

    private static final Supplier<UUID> NEVER = () -> {
        throw new AssertionError("a lower-priority source must not be consulted");
    };

    @Test
    void controllingPlayerAnswersFirst() {
        var decision = CbcShotAttributionPolicy.decide(PASSENGER, PLACER, NEVER, NEVER);
        assertEquals(CbcShotAttributionPolicy.Basis.PASSENGER, decision.basis());
        assertEquals(PASSENGER, decision.actorId());
        assertNull(decision.nationId());
        assertTrue(decision.attributed());
    }

    @Test
    void placerAnswersForAnUnmannedCannon() {
        var decision = CbcShotAttributionPolicy.decide(null, PLACER, NEVER, NEVER);
        assertEquals(CbcShotAttributionPolicy.Basis.PLACER, decision.basis());
        assertEquals(PLACER, decision.actorId());
        assertTrue(decision.attributed());
    }

    @Test
    void vehicleNationAnswersWhenNoPlayerDoes() {
        var decision = CbcShotAttributionPolicy.decide(null, null, () -> VEHICLE_NATION, NEVER);
        assertEquals(CbcShotAttributionPolicy.Basis.VEHICLE_NATION, decision.basis());
        assertNull(decision.actorId());
        assertEquals(VEHICLE_NATION, decision.nationId());
        assertTrue(decision.attributed());
    }

    @Test
    void territoryNationIsTheLastResort() {
        var decision = CbcShotAttributionPolicy.decide(null, null, () -> null, () -> TERRITORY_NATION);
        assertEquals(CbcShotAttributionPolicy.Basis.TERRITORY_NATION, decision.basis());
        assertNull(decision.actorId());
        assertEquals(TERRITORY_NATION, decision.nationId());
        assertTrue(decision.attributed());
    }

    @Test
    void nothingKnownLeavesTheShotUnattributed() {
        var decision = CbcShotAttributionPolicy.decide(null, null, () -> null, () -> null);
        assertEquals(CbcShotAttributionPolicy.Basis.NONE, decision.basis());
        assertFalse(decision.attributed());
        assertEquals(CbcShotAttributionPolicy.Decision.NONE,
                CbcShotAttributionPolicy.decide(null, null, null, null));
    }

    @Test
    void unattributedDamageIsRefusedOnProtectedTargets() {
        assertFalse(CbcShotAttributionPolicy.mayDamageProtected(null, null));
        assertTrue(CbcShotAttributionPolicy.mayDamageProtected(PASSENGER, null));
        assertTrue(CbcShotAttributionPolicy.mayDamageProtected(null, VEHICLE_NATION));
        assertTrue(CbcShotAttributionPolicy.mayDamageProtected(PLACER, TERRITORY_NATION));
    }

    @Test
    void aPlacerWhoChangedNationNoLongerAnswersForTheGun() {
        java.util.UUID home = java.util.UUID.randomUUID();
        java.util.UUID enemy = java.util.UUID.randomUUID();
        // Still in the nation that set the gun up, on its own land: answers.
        assertTrue(CbcShotAttributionPolicy.placerAnswers(true, home, home, home, false));
        // Defected: the gun goes back to the land's owner.
        assertFalse(CbcShotAttributionPolicy.placerAnswers(true, home, enemy, home, false));
        // Gun now on another nation's land that is not an ally.
        assertFalse(CbcShotAttributionPolicy.placerAnswers(true, home, home, enemy, false));
        assertTrue(CbcShotAttributionPolicy.placerAnswers(true, home, home, enemy, true));
        // Wilderness, nation unchanged (including a nationless placer).
        assertTrue(CbcShotAttributionPolicy.placerAnswers(true, home, home, null, false));
        assertTrue(CbcShotAttributionPolicy.placerAnswers(true, null, null, null, false));
        // Placement nation never recorded: fall back to the location.
        assertFalse(CbcShotAttributionPolicy.placerAnswers(false, null, home, null, false));
    }
}
