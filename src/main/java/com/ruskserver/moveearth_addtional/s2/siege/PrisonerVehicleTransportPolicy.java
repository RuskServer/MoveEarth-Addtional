package com.ruskserver.moveearth_addtional.s2.siege;

public final class PrisonerVehicleTransportPolicy {
    private PrisonerVehicleTransportPolicy() { }
    public static boolean canLoad(boolean escorting, boolean ownedVehicle, boolean vehicleActive,
                                  boolean captiveAvailable, boolean sameDimension, double distanceSquared,
                                  boolean vehicleSlotFree) {
        return escorting && ownedVehicle && vehicleActive && captiveAvailable && sameDimension
                && distanceSquared <= 36.0D && vehicleSlotFree;
    }
}
