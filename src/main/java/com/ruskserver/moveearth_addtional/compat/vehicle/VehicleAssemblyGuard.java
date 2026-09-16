package com.ruskserver.moveearth_addtional.compat.vehicle;

import net.minecraft.core.BlockPos;

import java.util.Set;

/** Prevents Sable's raw source-block removal from unregistering a core being moved. */
public final class VehicleAssemblyGuard {
    private static final ThreadLocal<Set<BlockPos>> MOVING = ThreadLocal.withInitial(Set::of);
    private VehicleAssemblyGuard() { }

    public static void begin(Set<BlockPos> positions) { MOVING.set(Set.copyOf(positions)); }
    public static boolean isMoving(BlockPos pos) { return MOVING.get().contains(pos); }
    public static void end() { MOVING.remove(); }
}
