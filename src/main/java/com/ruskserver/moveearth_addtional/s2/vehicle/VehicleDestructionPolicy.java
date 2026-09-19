package com.ruskserver.moveearth_addtional.s2.vehicle;

public final class VehicleDestructionPolicy {
    private VehicleDestructionPolicy() { }
    public static boolean transitionedToDestroyed(int beforeHealth, int afterHealth) {
        return beforeHealth > 0 && afterHealth <= 0;
    }
}
