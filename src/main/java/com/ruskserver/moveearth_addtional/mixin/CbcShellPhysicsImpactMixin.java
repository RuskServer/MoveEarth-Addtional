package com.ruskserver.moveearth_addtional.mixin;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A blocked explosion must not let CBC remove the physical collision as if the shell had detonated. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.compat.sable.ShellSubLevelImpactCallback", remap = false)
public abstract class CbcShellPhysicsImpactMixin {
    @Inject(method = "sable$onCollision", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$keepNormalCollision(BlockPos pos, BlockPos otherPos, Vector3d hitPos, double velocity,
            CallbackInfoReturnable<BlockSubLevelCollisionCallback.CollisionResult> callback) {
        callback.setReturnValue(BlockSubLevelCollisionCallback.CollisionResult.NONE);
    }
}
