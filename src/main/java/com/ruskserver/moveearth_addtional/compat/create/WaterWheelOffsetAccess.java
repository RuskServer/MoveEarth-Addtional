package com.ruskserver.moveearth_addtional.compat.create;

import net.minecraft.core.BlockPos;

import java.util.Set;

/** Access to the fluid positions Create evaluates around a water wheel. */
public interface WaterWheelOffsetAccess {
    Set<BlockPos> moveearth$getOffsetsToCheck();
}
