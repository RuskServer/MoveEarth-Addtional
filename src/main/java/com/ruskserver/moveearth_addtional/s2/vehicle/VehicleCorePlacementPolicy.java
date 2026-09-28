package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Whether a new vehicle core may claim the Sable body it was placed on. A craft
 * whose connected bodies already carry a live core belongs to that core: letting
 * a second one bind would hand an intruder the craft, drop its reinforcement to
 * the new record and restore full core HP. A core whose record is gone, or whose
 * own body has left the chain, no longer governs the craft and may be replaced.
 */
public final class VehicleCorePlacementPolicy {
    private VehicleCorePlacementPolicy() { }

    /**
     * @param bodies        every body connected to the one the core was placed on
     * @param boundVehicles vehicle id stored on each body that has one
     * @param coreBodyOf    the body a vehicle record's core sits on, or null when the record is gone
     */
    public static boolean mayBind(Set<UUID> bodies, Map<UUID, UUID> boundVehicles,
                                  Function<UUID, UUID> coreBodyOf) {
        for (UUID body : bodies) {
            UUID vehicle = boundVehicles.get(body);
            if (vehicle == null) continue;
            UUID coreBody = coreBodyOf.apply(vehicle);
            if (coreBody != null && bodies.contains(coreBody)) return false;
        }
        return true;
    }
}
