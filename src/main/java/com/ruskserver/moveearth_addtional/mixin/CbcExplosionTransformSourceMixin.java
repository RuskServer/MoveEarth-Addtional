package com.ruskserver.moveearth_addtional.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disable CBC blast-driven surrounding-block conversion, including gravel generation. */
@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.munitions.ShellExplosion",
        "rbasamoyai.createbigcannons.munitions.ImpactExplosion",
        "rbasamoyai.createbigcannons.munitions.big_cannon.mortar_stone.MortarStoneExplosion"
}, remap = false)
public abstract class CbcExplosionTransformSourceMixin {
    @Inject(method = "editBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$requireKnownTransformation(Level level, BlockPos pos, BlockState state,
                                                       FluidState fluid, float power, CallbackInfo callback) {
        if (level instanceof ServerLevel) callback.cancel();
    }
}
