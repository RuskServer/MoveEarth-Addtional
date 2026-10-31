package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcPlacedShellRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers wired, timed and physics callbacks before the shell block is removed. Firing uses getProjectile instead. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock", remap = false)
public abstract class CbcPlacedShellDetonationMixin {
    @Inject(method = "detonateProjectileOnTheSpot", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$keepPlacedShellInert(Level level, BlockPos pos, BlockState state,
                                               Direction direction, CallbackInfo callback) {
        callback.cancel();
        if (level instanceof ServerLevel serverLevel) CbcPlacedShellRules.warnNearby(serverLevel, pos);
    }
}
