package com.ruskserver.moveearth_addtional.mixin;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Config/datapack transforms cannot re-enable impact-generated gravel or other replacement terrain. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.block_hit_effects.BlockImpactTransformationHandler", remap = false)
public abstract class CbcTerrainTransformationMixin {
    @Inject(method = "transformBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void moveearth$keepOriginalTerrain(BlockState state, CallbackInfoReturnable<BlockState> callback) {
        callback.setReturnValue(state);
    }
}
