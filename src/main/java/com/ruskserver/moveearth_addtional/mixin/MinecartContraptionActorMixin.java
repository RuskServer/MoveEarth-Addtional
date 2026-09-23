package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.create.MinecartContraptionRules;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Work heads remain cargo in foreign territory instead of becoming a mobile grief machine. */
@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class MinecartContraptionActorMixin {
    @Inject(method = "tickActors", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$suspendForeignWorkDevices(CallbackInfo callback) {
        if (MinecartContraptionRules.shouldSuspendWorkActors((AbstractContraptionEntity) (Object) this)) {
            callback.cancel();
        }
    }
}
