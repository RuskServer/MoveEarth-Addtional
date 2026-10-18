package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.cinematic.JoinCinematic;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No hands or held item while the join cinematic's camera is in the sky. Vanilla draws them whenever the view
 * is set to first person, wherever the camera is, so a TaCZ gun hung in the middle of the shot.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererJoinCinematicMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void moveearth$noHandsInJoinCinematic(Camera camera, float partialTick, Matrix4f projection,
                                                 CallbackInfo ci) {
        if (JoinCinematic.running()) ci.cancel();
    }
}
