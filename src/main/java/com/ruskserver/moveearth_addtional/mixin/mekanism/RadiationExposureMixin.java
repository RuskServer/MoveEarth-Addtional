package com.ruskserver.moveearth_addtional.mixin.mekanism;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekanismRadiationTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weakens Mekanism radiation exposure across territory borders
 * ({@link MekanismRadiationTerritory}). Mekanism divides a source's magnitude by
 * the squared distance to the measured point; only that distance is adjusted.
 */
@Pseudo
@Mixin(targets = "mekanism.common.lib.radiation.RadiationUtil", remap = false)
public abstract class RadiationExposureMixin {

    @WrapOperation(method = "computeExposure",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private static double moveearth$crossBorderDistance(BlockPos measured, Vec3i source, Operation<Double> original) {
        return MekanismRadiationTerritory.exposureDistanceSqr(original.call(measured, source), measured, source);
    }
}
