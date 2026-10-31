package com.ruskserver.moveearth_addtional.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** Do not arm a placed timer, or let FuzedBlockEntity mark itself removed after a blocked detonation. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity", remap = false)
public abstract class CbcPlacedShellBlockTickMixin {
    @Inject(method = "canDetonate", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$disablePlacedFuzeTick(Predicate<?> predicate, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
