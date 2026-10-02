package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ruskserver.moveearth_addtional.compat.vehicle.AssemblyRequests;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableAssemblyCoordinator;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

/**
 * Lets Simulated's own assembly path (Physics Assembler, swivel bearing)
 * refuse a Sable assembly that MoveEarth cannot carry out without loss.
 *
 * <p>{@code assembleFromSingleBlock} declares {@link AssemblyException}, and
 * every caller already reports one to the player (the assembler's goggle
 * tooltip and failure animation). The check runs once the structure search
 * has found the block set and before {@code disassembleAndAddCreateContraptions}
 * -- the first step that changes the world -- so a refusal leaves everything as
 * it was. The request it opens tells the Sable hook where the assembler stands
 * and who pulled its lever.
 *
 * <p>Verified against Simulated 1.3.1 and 1.3.2 (bundled in Aeronautics 1.3.2):
 * {@code assembleFromSingleBlock(Level, BlockPos, BlockPos start, boolean, boolean)}
 * searches from {@code start} and calls {@code disassembleAndAddCreateContraptions}
 * and then {@code SubLevelAssemblyHelper.assembleBlocks} with the same collection.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.SimAssemblyHelper", remap = false)
public abstract class SimulatedAssemblyHelperMixin {
    @WrapOperation(method = "assembleFromSingleBlock", at = @At(value = "INVOKE",
            target = "Ldev/simulated_team/simulated/util/SimAssemblyHelper;disassembleAndAddCreateContraptions(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;Ljava/util/Collection;ZLjava/util/List;)V"),
            remap = false)
    private static void moveearth$reviewAssembly(Level level, BoundingBox3ic bounds, Collection<BlockPos> blocks,
                                                 boolean merge, List<AABB> boxes, Operation<Void> original,
                                                 @Local(argsOnly = true, ordinal = 1) BlockPos start)
            throws AssemblyException {
        if (level instanceof ServerLevel serverLevel) {
            ServerPlayer actor = AssemblyRequests.actor();
            Component refusal = SableAssemblyCoordinator.review(serverLevel, start, blocks, actor);
            if (refusal != null) {
                AssemblyRequests.close();
                throw new AssemblyException(refusal);
            }
            AssemblyRequests.open(new AssemblyRequests.Request(start.immutable(), actor, blocks));
        }
        original.call(level, bounds, blocks, merge, boxes);
    }

    @Inject(method = "assembleFromSingleBlock", at = @At("RETURN"), remap = false)
    private static void moveearth$closeRequest(CallbackInfoReturnable<?> callback) {
        AssemblyRequests.close();
    }
}
