package com.ruskserver.moveearth_addtional.s2.vehicle;

/** Pure clamping rules shared by persistent vehicle-core damage and tests. */
public final class VehicleCoreHealthPolicy {
    private VehicleCoreHealthPolicy() { }

    public static int damage(int health, int amount) {
        if (health <= 0) return 0;
        return Math.max(0, health - Math.max(0, amount));
    }
}
