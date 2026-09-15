package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.loading.MoveEarthLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Replaces world data/resource preparation and saving wait screens. */
@Mixin(GenericMessageScreen.class)
abstract class GenericMessageScreenMixin extends Screen {
    @Shadow @Nullable private FocusableTextWidget textWidget;

    protected GenericMessageScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void moveearth$hideVanillaMessage(CallbackInfo callback) {
        if (textWidget != null) textWidget.visible = false;
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void moveearth$renderLoadingBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                                    float partialTick, CallbackInfo callback) {
        MoveEarthLoadingRenderer.render(graphics, font, width, height, title,
                -1, false, Util.getMillis());
        callback.cancel();
    }
}
