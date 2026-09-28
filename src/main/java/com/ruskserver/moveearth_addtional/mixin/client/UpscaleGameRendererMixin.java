package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.upscale.UpscaleRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class UpscaleGameRendererMixin {
    /** Before the world pass, after the scope PiP has rendered its lens at HEAD. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void moveearth$beginUpscaledWorld(DeltaTracker deltaTracker, CallbackInfo callback) {
        UpscaleRenderer.beginWorld();
    }

    /**
     * After NeoForge's AFTER_LEVEL stage, just before the hand clears depth: mods' world-space overlays
     * drawn in that stage still depth-test against the world target, so no depth copy is needed.
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/neoforged/neoforge/client/ClientHooks;dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"))
    private void moveearth$finishUpscaledWorld(DeltaTracker deltaTracker, CallbackInfo callback) {
        UpscaleRenderer.finishWorld();
    }
}
