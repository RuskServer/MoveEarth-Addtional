package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.scope.ScopePipRenderer;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczScopePipZoomMixin {
    @Inject(method = "applyScopeMagnification", at = @At("HEAD"), cancellable = true, remap = false)
    private static void moveearth$keepOutsideFov(ViewportEvent.ComputeFov event, CallbackInfo callback) {
        if (ScopePipRenderer.overridesZoom() && event.usedConfiguredFov()) callback.cancel();
    }
}
