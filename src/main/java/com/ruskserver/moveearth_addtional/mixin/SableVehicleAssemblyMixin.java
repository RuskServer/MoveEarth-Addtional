package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.vehicle.SableAssemblyCoordinator;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableAssemblyExclusions;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableBlockRelocation;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps MoveEarth's ownership rules and per-block metadata in step with Sable's
 * block moves.
 *
 * <p>{@code assembleBlocks} decides what is lifted and which vehicle the new
 * body belongs to; {@code moveBlocks}, which assembly and every Simulated
 * disassembly (Physics Assembler, swivel bearing, merging glue) share, carries
 * reinforcement, battle damage and vehicle core records with the blocks.
 */
@Pseudo
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableVehicleAssemblyMixin {
    /**
     * Filters the block set, then remembers what the new body is bound to.
     *
     * <p>Ore deposits stay in the ground. A deposit is an ordinary block -- no
     * block entity, so none of Create's protections for blocks that carry data
     * apply to it, and it is not tagged immovable. The resource <em>is</em> those
     * blocks, which the miner counts as it works. Glue a few to a hull, assemble,
     * fly home, set it down, and the deposit has moved: whichever region was given
     * that resource no longer has it, and whichever one flew there does.
     *
     * <p>Territory cores, market stations and storage wreckage are tied to where
     * they stand in the same way, and another nation's active reinforcement or
     * live vehicle core must not leave its owner's land or craft: Create's
     * structure search follows slime, honey glue, chassis and attached blocks
     * across a border with no ownership check, and a lifted wall comes off the
     * ground without its reinforcement. See {@link SableAssemblyExclusions}.
     *
     * <p>Those are excluded rather than refused, so a neighbour's wall touching a
     * hangar cannot block its assembly; the players around the assembler are told
     * how many blocks stayed and why. What would tear the assembling nation's own
     * hull, or bind one body to two cores, Simulated's assembler refuses before
     * anything moves ({@code SimulatedAssemblyHelperMixin}).
     *
     * <p>Filtering here, in one handler, rather than in a separate HEAD injector:
     * two HEAD handlers have no guaranteed order.
     */
    @ModifyVariable(method = "assembleBlocks", at = @At("HEAD"), argsOnly = true, remap = false)
    private static Iterable<BlockPos> moveearth$filterAndCapture(Iterable<BlockPos> positions,
                                                                 ServerLevel level, BlockPos anchor) {
        return SableAssemblyCoordinator.begin(level, anchor, positions);
    }

    @Inject(method = "assembleBlocks", at = @At("RETURN"), remap = false)
    private static void moveearth$finishVehicle(ServerLevel level, BlockPos anchor,
                                                Iterable<BlockPos> positions, BoundingBox3ic bounds,
                                                CallbackInfoReturnable<ServerSubLevel> callback) {
        SableAssemblyCoordinator.finish(level, positions, callback.getReturnValue());
    }

    @Inject(method = "moveBlocks", at = @At("HEAD"), remap = false)
    private static void moveearth$beginMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                            Iterable<BlockPos> positions, CallbackInfo callback) {
        SableBlockRelocation.begin(level, transform, positions);
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"), remap = false)
    private static void moveearth$finishMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                             Iterable<BlockPos> positions, CallbackInfo callback) {
        SableBlockRelocation.finish(transform);
    }
}
