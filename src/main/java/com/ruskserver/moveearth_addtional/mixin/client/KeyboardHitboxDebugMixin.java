package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents F3+B from exposing entity hitboxes during normal play. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHitboxDebugMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void moveearth$disableHitboxDebug(int key, CallbackInfoReturnable<Boolean> callback) {
        if (key != GLFW.GLFW_KEY_B) {
            return;
        }

        minecraft.getEntityRenderDispatcher().setRenderHitBoxes(false);
        minecraft.gui.getChat().addMessage(MoveEarthMessage.info(
                Component.translatable("message.moveearth_addtional.debug.hitboxes_disabled")));
        callback.setReturnValue(true);
    }
}
