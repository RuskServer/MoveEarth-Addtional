package com.ruskserver.moveearth_addtional.mixin;

import static com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionAudit.rejected;

import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WorldMutationLevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void moveearth$sourceBeforeReplacement(BlockPos pos, BlockState state, int flags, int recursion,
                                                   CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel level && !WorldMutationGuard.allowsMutation(level, pos, state)) {
            rejected("level.replace", level, pos);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void moveearth$forgetRemovedPiston(BlockPos pos, BlockState state, int flags, int recursion,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerLevel level
                && level.getServer().overworld() != null
                && !(state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock)) {
            com.ruskserver.moveearth_addtional.s2.provenance.ActuatorOwnershipSavedData.get(level.getServer())
                    .forget(level.dimension().location(), pos);
        }
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
    private boolean moveearth$sourceBeforeDrops(BlockPos pos, boolean drops, Entity entity, int recursion,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        Object origin = entity != null && !(entity instanceof net.neoforged.neoforge.common.util.FakePlayer)
                ? entity : com.ruskserver.moveearth_addtional.s2.provenance.ActionContext.current();
        if ((Object) this instanceof ServerLevel level && origin != null
                && !WorldMutationGuard.source(origin).allowed()) {
            rejected("level.destroy", level, pos);
            return false;
        }
        try (var scope = com.ruskserver.moveearth_addtional.s2.provenance.ActionContext.push(origin)) {
            return original.call(pos, drops, entity, recursion);
        }
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void moveearth$inheritActuatorOrigin(net.minecraft.world.level.block.entity.BlockEntity block,
                                                 org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if ((Object) this instanceof ServerLevel level) {
            // A freshly constructed moving piston has no level until insertion.
            if (block.getLevel() == null) block.setLevel(level);
            WorldMutationGuard.inherit(block);
        }
    }
}
