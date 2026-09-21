package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cbc.CbcAutocannonRatePolicy;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps mounted, carriage and block autocannons in the same active 120–300 RPM range. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.cannons.autocannon.breech.AbstractAutocannonBreechBlockEntity", remap = false)
public abstract class CbcAutocannonRateMixin {
    @Shadow private int fireRate;

    @Unique
    private void moveearth$normalizeRate() {
        fireRate = CbcAutocannonRatePolicy.normalizeSelector(fireRate);
    }

    @Inject(method = "setFireRate", at = @At("TAIL"))
    private void moveearth$normalizeSelectedRate(int power, CallbackInfo callback) {
        moveearth$normalizeRate();
    }

    @Inject(method = "allTick", at = @At("HEAD"))
    private void moveearth$normalizeSavedRate(Level level, CallbackInfo callback) {
        moveearth$normalizeRate();
    }

    @Inject(method = "canFire", at = @At("HEAD"))
    private void moveearth$normalizeBeforeFire(CallbackInfoReturnable<Boolean> callback) {
        moveearth$normalizeRate();
    }

    @Inject(method = "getActualFireRate", at = @At("HEAD"))
    private void moveearth$normalizeDisplayedRate(CallbackInfoReturnable<Integer> callback) {
        moveearth$normalizeRate();
    }
}
