package com.ruskserver.moveearth_addtional.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class UpscaleMainTargetMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void moveearth$worldOnlyRenderTarget(CallbackInfoReturnable<RenderTarget> callback) {
        RenderTarget override = UpscaleRenderer.worldTargetOverride();
        if (override != null) callback.setReturnValue(override);
    }
}
