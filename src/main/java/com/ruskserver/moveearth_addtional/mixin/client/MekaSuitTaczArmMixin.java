package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.compat.TaczArmRenderContext;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.client.render.RenderTickHandler", remap = false)
public abstract class MekaSuitTaczArmMixin {
    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$preserveGunArm(RenderArmEvent event, CallbackInfo callback) {
        if (TaczArmRenderContext.isRenderingArm()) callback.cancel();
    }
}
