package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.scope.ScopePipIrisBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisScopePipHandMixin {
    @Inject(method = {"renderSolid", "renderTranslucent"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void moveearth$skipLensHand(CallbackInfo callback) {
        if (ScopePipIrisBridge.skipHand()) callback.cancel();
    }
}
