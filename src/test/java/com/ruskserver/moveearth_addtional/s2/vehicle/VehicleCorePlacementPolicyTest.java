package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleCorePlacementPolicyTest {
    private final UUID hull = UUID.randomUUID();
    private final UUID turret = UUID.randomUUID();
    private final UUID vehicle = UUID.randomUUID();

    @Test
    void anUnboundCraftMayTakeACore() {
        assertTrue(VehicleCorePlacementPolicy.mayBind(Set.of(hull, turret), Map.of(), id -> null));
    }

    @Test
    void aCraftWithALiveCoreRefusesASecondOne() {
        assertFalse(VehicleCorePlacementPolicy.mayBind(Set.of(hull, turret), Map.of(hull, vehicle),
                id -> id.equals(vehicle) ? hull : null));
    }

    @Test
    void placingOnAConnectedPartStillCountsTheCoreElsewhereInTheChain() {
        // The core sits on the hull; the new one goes on the turret joined to it by a bearing.
        assertFalse(VehicleCorePlacementPolicy.mayBind(Set.of(hull, turret), Map.of(turret, vehicle),
                id -> id.equals(vehicle) ? hull : null));
    }

    @Test
    void aCoreWhoseRecordIsGoneOrWhoseBodyLeftMayBeReplaced() {
        assertTrue(VehicleCorePlacementPolicy.mayBind(Set.of(hull), Map.of(hull, vehicle), id -> null));
        assertTrue(VehicleCorePlacementPolicy.mayBind(Set.of(hull), Map.of(hull, vehicle),
                id -> UUID.randomUUID()));
    }
}
