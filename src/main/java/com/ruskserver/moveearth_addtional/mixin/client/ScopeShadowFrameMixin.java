package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.scope.ScopeShadowRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once the world, the hand and a shader pack's final pass are on the screen, before the HUD. */
@Mixin(GameRenderer.class)
public abstract class ScopeShadowFrameMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void moveearth$drawDeferredScopeShadow(DeltaTracker deltaTracker, CallbackInfo callback) {
        ScopeShadowRenderer.flushDeferred();
    }
}
