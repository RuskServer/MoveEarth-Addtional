package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.compat.create.MinecartContraptionRules;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Work heads stop on land that is not the cart's own, and a cart broken there drops its cargo as items. */
@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class MinecartContraptionActorMixin {
    @Inject(method = "tickActors", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$suspendForeignWorkDevices(CallbackInfo callback) {
        MinecartContraptionRules.rememberCart((AbstractContraptionEntity) (Object) this);
        if (MinecartContraptionRules.shouldSuspendWorkActors((AbstractContraptionEntity) (Object) this)) {
            callback.cancel();
        }
    }

    /** A cart broken on someone else's land drops its cargo there instead of building it into their land. */
    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$dropCargoOnForeignLand(CallbackInfo callback) {
        if (MinecartContraptionRules.dropInsteadOfUnloading((AbstractContraptionEntity) (Object) this)) {
            callback.cancel();
        }
    }
}
