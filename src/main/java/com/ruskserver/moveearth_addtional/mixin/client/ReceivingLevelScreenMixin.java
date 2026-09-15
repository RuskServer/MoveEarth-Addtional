package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.loading.MoveEarthLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
abstract class ReceivingLevelScreenMixin extends Screen {
    protected ReceivingLevelScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moveearth$renderLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        MoveEarthLoadingRenderer.render(graphics, font, width, height,
                Component.translatable("multiplayer.downloadingTerrain"),
                -1, false, Util.getMillis());
        callback.cancel();
    }
}
