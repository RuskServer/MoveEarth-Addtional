package com.ruskserver.moveearth_addtional.compat.create;

/** Cache bridge added to Create kinetic block entities by mixin. */
public interface WaterWheelCapacityState {
    double moveearth$getWaterWheelMultiplier();
    long moveearth$getWaterWheelMultiplierValidUntil();
    void moveearth$setWaterWheelMultiplier(double multiplier, long validUntil);
    void moveearth$invalidateWaterWheelMultiplier();
    void moveearth$refreshWaterWheelCapacity();
}
