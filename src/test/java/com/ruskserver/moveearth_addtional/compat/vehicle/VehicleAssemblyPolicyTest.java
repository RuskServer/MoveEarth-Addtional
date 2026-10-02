package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.compat.vehicle.AssemblyExclusionPolicy.Kind.*;
import static com.ruskserver.moveearth_addtional.compat.vehicle.VehicleAssemblyPolicy.Placement;
import static com.ruskserver.moveearth_addtional.compat.vehicle.VehicleAssemblyPolicy.Refusal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleAssemblyPolicyTest {
    private final UUID home = UUID.randomUUID();
    private final UUID neighbour = UUID.randomUUID();
    private final UUID coreA = UUID.randomUUID();
    private final UUID coreB = UUID.randomUUID();

    @Test
    void ownHullOutsideTheAssemblersTerritoryIsTheActorsOwnNotForeign() {
        // Assembler placed on unclaimed land, hull standing on the actor's own territory.
        assertEquals(Placement.OWN, VehicleAssemblyPolicy.place(NATION_PROTECTED, home, null, home));
        assertEquals(Placement.FOREIGN, VehicleAssemblyPolicy.place(NATION_PROTECTED, neighbour, null, home));
        assertEquals(Placement.FOREIGN, VehicleAssemblyPolicy.place(NATION_PROTECTED, home, null, null));
    }

    @Test
    void placementFollowsTheExclusionPolicy() {
        assertEquals(Placement.KEPT, VehicleAssemblyPolicy.place(NATION_PROTECTED, home, home, home));
        assertEquals(Placement.KEPT, VehicleAssemblyPolicy.place(ORDINARY, neighbour, home, home));
        assertEquals(Placement.FIXTURE, VehicleAssemblyPolicy.place(FIXTURE, null, home, home));
        assertEquals(Placement.FOREIGN, VehicleAssemblyPolicy.place(NATION_PROTECTED, neighbour, home, home));
    }

    @Test
    void tallyCountsEveryBlockLeftBehindPerReason() {
        VehicleAssemblyPolicy.Tally tally = VehicleAssemblyPolicy.Tally.EMPTY;
        for (Placement placement : List.of(Placement.KEPT, Placement.FIXTURE, Placement.FOREIGN,
                Placement.FOREIGN, Placement.OWN)) {
            tally = tally.with(placement);
        }
        assertEquals(new VehicleAssemblyPolicy.Tally(1, 2, 1), tally);
        assertEquals(4, tally.total());
        assertFalse(VehicleAssemblyPolicy.Tally.EMPTY.any());
    }

    @Test
    void tearingTheNationsOwnHullIsRefused() {
        var binding = VehicleAssemblyPolicy.binding(Set.of(coreA), null);
        assertEquals(Refusal.OWN_BLOCKS_LEFT_BEHIND,
                VehicleAssemblyPolicy.refusal(new VehicleAssemblyPolicy.Tally(0, 0, 3), binding));
    }

    @Test
    void foreignBlocksAndFixturesAreLeftBehindWithoutRefusing() {
        var binding = VehicleAssemblyPolicy.binding(Set.of(coreA), null);
        assertEquals(Refusal.NONE,
                VehicleAssemblyPolicy.refusal(new VehicleAssemblyPolicy.Tally(4, 7, 0), binding));
    }

    @Test
    void twoCoresInOneSetAreRefusedAndBindNothing() {
        Set<UUID> cores = new LinkedHashSet<>(List.of(coreA, coreB));
        var binding = VehicleAssemblyPolicy.binding(cores, null);
        assertNull(binding.vehicleId());
        assertEquals(Refusal.MULTIPLE_CORES, VehicleAssemblyPolicy.refusal(VehicleAssemblyPolicy.Tally.EMPTY, binding));
    }

    @Test
    void aCoreForeignToTheBodyItIsLiftedFromConflicts() {
        var binding = VehicleAssemblyPolicy.binding(Set.of(coreB), coreA);
        assertNull(binding.vehicleId());
        assertEquals(Refusal.CORE_CONFLICT, binding.conflict());
    }

    @Test
    void bindingPrefersTheLiftedCoreThenTheGoverningVehicle() {
        assertEquals(coreA, VehicleAssemblyPolicy.binding(Set.of(coreA), null).vehicleId());
        assertEquals(coreA, VehicleAssemblyPolicy.binding(Set.of(coreA), coreA).vehicleId());
        assertEquals(coreA, VehicleAssemblyPolicy.binding(Set.of(), coreA).vehicleId());
        var none = VehicleAssemblyPolicy.binding(Set.of(), null);
        assertNull(none.vehicleId());
        assertFalse(none.conflicting());
    }

    @Test
    void reinforcementWithoutACoreMovesInactiveInsteadOfBeingDropped() {
        var noCore = VehicleAssemblyPolicy.binding(Set.of(), null);
        assertEquals(Refusal.NONE, VehicleAssemblyPolicy.refusal(VehicleAssemblyPolicy.Tally.EMPTY, noCore));
        assertTrue(VehicleAssemblyPolicy.reinforcementInactive(12, noCore.vehicleId()));
        assertFalse(VehicleAssemblyPolicy.reinforcementInactive(12, coreA));
        assertFalse(VehicleAssemblyPolicy.reinforcementInactive(0, null));
    }
}
