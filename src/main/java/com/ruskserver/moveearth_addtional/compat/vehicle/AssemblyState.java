package com.ruskserver.moveearth_addtional.compat.vehicle;

import net.minecraft.core.BlockPos;

/** Runtime assembly snapshot, accessible from Sable's transformed helper class.
 * Must stay outside the reserved mixin package and expose public accessors.
 *
 * @param lifted          the (possibly filtered) block set handed on to Sable; matched by identity
 * @param origin          where the assembly was started
 * @param binding         vehicle the new body is bound to
 * @param liftedReinforced reinforced blocks in {@code lifted}
 * @param fromWorld       lifted from the plain world rather than split off another body
 * @param request         Simulated request behind it, or null
 */
public record AssemblyState(Iterable<BlockPos> lifted, BlockPos origin, VehicleAssemblyPolicy.Binding binding,
                            int liftedReinforced, boolean fromWorld, AssemblyRequests.Request request) { }
