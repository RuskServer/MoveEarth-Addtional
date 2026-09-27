package com.ruskserver.moveearth_addtional.compat.create;

/** Implemented on Create's blaze burner: its current boiler burn rate, synced to clients. */
public interface BoilerBurnerFuelView {
    /** Burn ticks per game tick; 1 is Create's own rate. */
    double moveearth$getBurnRate();
}
