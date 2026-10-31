package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcPlacedShellRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes already installed fuzes and items inserted by machines; no fuse/item consumption. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.fuzes.WiredFuzeItem", remap = false)
public abstract class CbcWiredFuzeMixin {
    @Inject(method = "onRedstoneSignal", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$disableWiredTrigger(ItemStack stack, Level level, BlockPos pos, BlockState state,
                                              int signal, Direction from, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
        if (signal > 0 && level instanceof ServerLevel serverLevel) CbcPlacedShellRules.warnNearby(serverLevel, pos);
    }
}
