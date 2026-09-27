package com.ruskserver.moveearth_addtional.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.ruskserver.moveearth_addtional.client.scope.ScopePipRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class ScopePipTargetMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void moveearth$lensTarget(CallbackInfoReturnable<RenderTarget> callback) {
        if (ScopePipRenderer.isRenderingLens()) callback.setReturnValue(ScopePipRenderer.lensTarget());
    }
}
