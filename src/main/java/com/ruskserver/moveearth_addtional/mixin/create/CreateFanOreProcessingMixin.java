package com.ruskserver.moveearth_addtional.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.ruskserver.moveearth_addtional.compat.create.FanOreProcessing;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lengthens Create's fan processing time for ore-derived items
 * ({@link FanOreProcessing}). Create computes the time once, when an item
 * enters an air current, from its {@code fanProcessingTime} setting; only that
 * configured value is scaled, so the stack-size factor and countdown stay
 * Create's own.
 */
@Mixin(value = FanProcessing.class, remap = false)
public abstract class CreateFanOreProcessingMixin {

    /** Items on belts and depots. */
    @ModifyExpressionValue(method = "applyProcessing(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;",
            at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I"))
    private static int moveearth$slowBeltOre(int configuredTicks,
                                             @Local(argsOnly = true) TransportedItemStack transported) {
        return FanOreProcessing.processingTime(configuredTicks, transported.stack);
    }

    /** Items lying in the air current. */
    @ModifyExpressionValue(method = "decrementProcessingTime",
            at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I"))
    private static int moveearth$slowEntityOre(int configuredTicks, @Local(argsOnly = true) ItemEntity entity) {
        return FanOreProcessing.processingTime(configuredTicks, entity.getItem());
    }
}
