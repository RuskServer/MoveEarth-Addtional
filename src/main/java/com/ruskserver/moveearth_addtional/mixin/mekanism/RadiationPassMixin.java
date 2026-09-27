package com.ruskserver.moveearth_addtional.mixin.mekanism;

import com.ruskserver.moveearth_addtional.compat.mekanism.MekanismRadiationTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brackets Mekanism's radiation decay pass and exposure queries with the level
 * they run in ({@link MekanismRadiationTerritory}).
 */
@Pseudo
@Mixin(targets = "mekanism.common.lib.radiation.RadiationManager", remap = false)
public abstract class RadiationPassMixin {

    @Inject(method = "tickServerWorld", at = @At("HEAD"))
    private void moveearth$beginDecayPass(ServerLevel level, CallbackInfo callback) {
        MekanismRadiationTerritory.begin(level, null);
    }

    @Inject(method = "tickServerWorld", at = @At("RETURN"))
    private void moveearth$endDecayPass(ServerLevel level, CallbackInfo callback) {
        MekanismRadiationTerritory.end();
    }

    @Inject(method = "getRadiationLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)D",
            at = @At("HEAD"))
    private void moveearth$beginLevelQuery(Level level, BlockPos pos, CallbackInfoReturnable<Double> callback) {
        MekanismRadiationTerritory.begin(level, pos);
    }

    @Inject(method = "getRadiationLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)D",
            at = @At("RETURN"))
    private void moveearth$endLevelQuery(Level level, BlockPos pos, CallbackInfoReturnable<Double> callback) {
        MekanismRadiationTerritory.end();
    }

    @Inject(method = "getRadiationLevelAndMaxMagnitude(Lnet/minecraft/world/entity/Entity;)Lmekanism/common/lib/radiation/LevelAndMaxMagnitude;",
            at = @At("HEAD"))
    private void moveearth$beginEntityQuery(Entity entity, CallbackInfoReturnable<Object> callback) {
        MekanismRadiationTerritory.begin(entity.level(), entity.blockPosition());
    }

    @Inject(method = "getRadiationLevelAndMaxMagnitude(Lnet/minecraft/world/entity/Entity;)Lmekanism/common/lib/radiation/LevelAndMaxMagnitude;",
            at = @At("RETURN"))
    private void moveearth$endEntityQuery(Entity entity, CallbackInfoReturnable<Object> callback) {
        MekanismRadiationTerritory.end();
    }
}
