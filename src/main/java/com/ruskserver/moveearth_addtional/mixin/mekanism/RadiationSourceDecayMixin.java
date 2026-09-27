package com.ruskserver.moveearth_addtional.mixin.mekanism;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekanismRadiationTerritory;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a Mekanism radiation source in the wilderness or near a core decay
 * faster ({@link MekanismRadiationTerritory}). Only the configured decay factor
 * is replaced; Mekanism's multiplication and removal threshold are untouched.
 */
@Pseudo
@Mixin(targets = "mekanism.common.lib.radiation.RadiationSource", remap = false)
public abstract class RadiationSourceDecayMixin {
    @Shadow @Final private BlockPos pos;

    @ModifyExpressionValue(method = "decay",
            at = @At(value = "INVOKE", target = "Lmekanism/common/config/value/CachedDoubleValue;get()D"))
    private double moveearth$territoryDecayRate(double mekanismRate) {
        return MekanismRadiationTerritory.decayRate(mekanismRate, pos);
    }
}
