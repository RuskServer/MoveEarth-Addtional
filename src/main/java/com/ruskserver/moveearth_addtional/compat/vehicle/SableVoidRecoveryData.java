package com.ruskserver.moveearth_addtional.compat.vehicle;

import net.minecraft.nbt.CompoundTag;

/** Persist attempts across body unloading/reloading; unloaded time never counts as safe recovery time. */
final class SableVoidRecoveryData {
    private static final String KEY = "MoveEarthVoidRecovery";
    private SableVoidRecoveryData() { }

    static SableVoidRescuePolicy.State read(CompoundTag userData) {
        if (userData == null || !userData.contains(KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return SableVoidRescuePolicy.State.EMPTY;
        }
        var data = userData.getCompound(KEY);
        int attempts = Math.max(0, data.getInt("Attempts"));
        if (attempts == 0 || !data.contains("LastAttempt", net.minecraft.nbt.Tag.TAG_LONG)) {
            return SableVoidRescuePolicy.State.EMPTY;
        }
        return SableVoidRescuePolicy.restore(attempts, data.getLong("LastAttempt"), data.getBoolean("SuspensionReported"));
    }

    static CompoundTag write(CompoundTag userData, SableVoidRescuePolicy.State state) {
        var result = userData == null ? new CompoundTag() : userData.copy();
        if (state.attempts() == 0) { result.remove(KEY); return result; }
        var data = new CompoundTag();
        data.putInt("Attempts", state.attempts());
        data.putLong("LastAttempt", state.lastAttempt());
        data.putBoolean("SuspensionReported", state.suspensionReported());
        result.put(KEY, data);
        return result;
    }
}
