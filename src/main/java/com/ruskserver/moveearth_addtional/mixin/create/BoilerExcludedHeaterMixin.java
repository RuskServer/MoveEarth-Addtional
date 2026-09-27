package com.ruskserver.moveearth_addtional.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.tank.BoilerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps electric heaters out of steam boilers.
 *
 * <p>A CEE resistive heater reaches a full boiler level on about 7.5 kW, while
 * the steam engine that level drives returns about 16.4 kW through an
 * alternator, so a boiler could heat itself and run forever without fuel. It
 * would also bypass the scarcity of blaze burners that keeps steam in check.
 * Heaters still heat basins; only the boiler ignores blocks in
 * {@code moveearth_addtional:not_boiler_heaters}.
 */
@Mixin(value = BoilerData.class, remap = false)
public abstract class BoilerExcludedHeaterMixin {
    @Unique
    private static final TagKey<Block> NOT_BOILER_HEATERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("moveearth_addtional", "not_boiler_heaters"));

    @WrapOperation(method = "updateTemperature",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/boiler/BoilerHeater;findHeat(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float moveearth$ignoreExcludedHeaters(Level level, BlockPos pos, BlockState state, Operation<Float> original) {
        if (state.is(NOT_BOILER_HEATERS)) return BoilerHeater.NO_HEAT;
        return original.call(level, pos, state);
    }
}
