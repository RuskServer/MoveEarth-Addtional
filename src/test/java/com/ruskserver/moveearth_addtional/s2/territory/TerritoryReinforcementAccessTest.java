package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryReinforcementAccessTest {
    @Test
    void configuringCoreCanBootstrapReinforcementInItsReservedArea() {
        assertTrue(TerritoryReinforcementAccessPolicy.canManage(false, true));
    }

    @Test
    void reservedOuterAreaDoesNotBypassUpkeepOrCombatState() {
        assertFalse(TerritoryReinforcementAccessPolicy.canManage(false, false));
    }

    @Test
    void effectiveControlledTerritoryRemainsManageable() {
        assertTrue(TerritoryReinforcementAccessPolicy.canManage(true, false));
    }
}
