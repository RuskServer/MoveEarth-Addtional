package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerVehicleTransportPolicyTest {
    @Test void requiresExplicitValidEscortAndOneFreeVehicleSlot() {
        assertTrue(PrisonerVehicleTransportPolicy.canLoad(true, true, true, true, true, 36.0D, true));
        assertFalse(PrisonerVehicleTransportPolicy.canLoad(false, true, true, true, true, 1.0D, true));
        assertFalse(PrisonerVehicleTransportPolicy.canLoad(true, false, true, true, true, 1.0D, true));
        assertFalse(PrisonerVehicleTransportPolicy.canLoad(true, true, false, true, true, 1.0D, true));
        assertFalse(PrisonerVehicleTransportPolicy.canLoad(true, true, true, true, false, 1.0D, true));
        assertFalse(PrisonerVehicleTransportPolicy.canLoad(true, true, true, true, true, 36.01D, true));
        assertFalse(PrisonerVehicleTransportPolicy.canLoad(true, true, true, true, true, 1.0D, false));
    }
}
