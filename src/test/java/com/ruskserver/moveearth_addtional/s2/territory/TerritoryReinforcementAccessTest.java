package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryReinforcementAccessTest {
    @Test
    void configuringCoreCanBootstrapReinforcementInItsReservedArea() {
        assertTrue(TerritoryReinforcementAccessPolicy.canManage(false, true, false));
    }

    @Test
    void lapsedReservationStillLetsTheOwnerSealUnclaimedLand() {
        // Past the hour, but nobody else took the land: the core can still be sealed and activated.
        assertTrue(TerritoryReinforcementAccessPolicy.canManage(false, false, true));
    }

    @Test
    void reservedOuterAreaDoesNotBypassUpkeepOrCombatState() {
        assertFalse(TerritoryReinforcementAccessPolicy.canManage(false, false, false));
    }

    @Test
    void effectiveControlledTerritoryRemainsManageable() {
        assertTrue(TerritoryReinforcementAccessPolicy.canManage(true, false, false));
    }
}
