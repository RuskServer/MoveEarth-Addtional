package com.ruskserver.moveearth_addtional.mixin.mekanism;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ruskserver.moveearth_addtional.config.MekanismBalanceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Caps how much fall damage MekaSuit boots cancel. Airborne landings stay
 * possible, but the height a player drops from still matters.
 */
@Pseudo
@Mixin(targets = "mekanism.common.CommonPlayerTickHandler", remap = false)
public abstract class MekaSuitFallAbsorptionMixin {

    @ModifyExpressionValue(method = "livingFall",
            at = @At(value = "INVOKE", target = "Lmekanism/api/functions/FloatSupplier;getAsFloat()F"))
    private float moveearth$capFallAbsorption(float mekanismRatio) {
        return Math.min(mekanismRatio, (float) MekanismBalanceConfig.mekaSuitFallAbsorption());
    }
}
