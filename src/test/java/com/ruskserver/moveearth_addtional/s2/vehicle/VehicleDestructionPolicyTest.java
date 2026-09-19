package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleDestructionPolicyTest {
    @Test void recordsOnlyThePositiveToZeroTransition() {
        assertTrue(VehicleDestructionPolicy.transitionedToDestroyed(1, 0));
        assertFalse(VehicleDestructionPolicy.transitionedToDestroyed(0, 0));
        assertFalse(VehicleDestructionPolicy.transitionedToDestroyed(10, 5));
    }
}
