package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime assembly snapshot, accessible from Sable's transformed helper class.
 * Must stay outside the reserved mixin package and expose public accessors.
 */
public record AssemblyState(BlockPos anchor, List<BlockPos> positions,
                            Map<BlockPos, ReinforcementEntry> reinforcements,
                            UUID vehicleId, boolean containsCore) { }
