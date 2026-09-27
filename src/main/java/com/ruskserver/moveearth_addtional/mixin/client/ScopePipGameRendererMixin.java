package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.scope.ScopePipRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ScopePipGameRendererMixin {
    @Shadow private boolean renderHand;
    @Shadow public abstract void renderLevel(DeltaTracker timer);

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void moveearth$separateWorldFov(Camera camera, float partialTick, boolean configured,
                                            CallbackInfoReturnable<Double> callback) {
        if (configured) callback.setReturnValue(ScopePipRenderer.worldFov(callback.getReturnValue()));
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void moveearth$renderLens(DeltaTracker timer, CallbackInfo callback) {
        ScopePipRenderer.beginFrame(timer, () -> {
            boolean previous = renderHand;
            renderHand = false;
            try {
                renderLevel(timer);
            } finally {
                renderHand = previous;
            }
        });
    }
}
