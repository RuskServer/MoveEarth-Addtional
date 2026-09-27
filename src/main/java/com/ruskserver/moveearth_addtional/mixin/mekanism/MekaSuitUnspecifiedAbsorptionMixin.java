package com.ruskserver.moveearth_addtional.mixin.mekanism;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ruskserver.moveearth_addtional.config.MekanismBalanceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Caps what the MekaSuit absorbs for damage types its absorption data map does
 * not list. Mekanism's default of 1.0 would let the suit soak every modded
 * damage type it has never heard of; MoveEarth's data map lists only what it
 * means to absorb, so everything else falls through to this cap.
 */
@Pseudo
@Mixin(targets = "mekanism.common.item.gear.ItemMekaSuitArmor", remap = false)
public abstract class MekaSuitUnspecifiedAbsorptionMixin {

    @ModifyExpressionValue(method = "getDamageAbsorbed",
            at = @At(value = "INVOKE", target = "Lmekanism/common/config/value/CachedFloatValue;get()F"))
    private static float moveearth$capUnspecifiedAbsorption(float mekanismRatio) {
        return Math.min(mekanismRatio, (float) MekanismBalanceConfig.mekaSuitUnspecifiedAbsorption());
    }
}
