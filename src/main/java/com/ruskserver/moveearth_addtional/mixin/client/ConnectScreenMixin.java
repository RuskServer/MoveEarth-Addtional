package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.loading.MoveEarthLoadingLayout;
import com.ruskserver.moveearth_addtional.client.loading.MoveEarthLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
abstract class ConnectScreenMixin extends Screen {
    @Shadow private Component status;
    @Shadow private long lastNarration;

    protected ConnectScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void moveearth$placeCancelInLoadingPanel(CallbackInfo callback) {
        MoveEarthLoadingLayout.Layout layout = MoveEarthLoadingLayout.calculate(width, height);
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) {
                var bounds = layout.cancel();
                widget.setRectangle(bounds.width(), bounds.height(), bounds.x(), bounds.y());
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moveearth$renderLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        long now = Util.getMillis();
        MoveEarthLoadingRenderer.render(graphics, font, width, height, status, -1, true, now);
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        if (now - lastNarration > 2_000L) {
            lastNarration = now;
            minecraft.getNarrator().sayNow(Component.translatable("narrator.joining"));
        }
        callback.cancel();
    }
}
