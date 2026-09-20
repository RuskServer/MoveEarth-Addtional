package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.aeronautics.PortableEngineBalancePolicy;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class PortableEngineCapacityMixin {
    @Shadow protected float lastCapacityProvided;

    @Inject(method = "calculateAddedStressCapacity", at = @At("RETURN"), cancellable = true)
    private void moveearth$capPortableEngineCapacity(CallbackInfoReturnable<Float> callback) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                ((BlockEntity) self).getBlockState().getBlock());
        float adjusted = PortableEngineBalancePolicy.cappedCapacity(
                AeronauticsSwivelConfig.portableEngineBalanceEnabled(),
                blockId.getNamespace(),
                blockId.getPath(),
                callback.getReturnValueF(),
                AeronauticsSwivelConfig.portableEngineMaxCapacity()
        );
        if (adjusted == callback.getReturnValueF()) {
            return;
        }
        lastCapacityProvided = adjusted;
        callback.setReturnValue(adjusted);
    }
}
