package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.loading.MoveEarthLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Keeps vanilla progress callbacks while replacing their presentation. */
@Mixin(ProgressScreen.class)
abstract class ProgressScreenMixin extends Screen {
    @Shadow @Nullable private Component header;
    @Shadow @Nullable private Component stage;
    @Shadow private int progress;
    @Shadow private boolean stop;

    protected ProgressScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moveearth$renderLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        if (stop) return;
        MoveEarthLoadingRenderer.render(graphics, font, width, height,
                moveearth$status(), Math.max(0, Math.min(100, progress)),
                false, Util.getMillis());
        callback.cancel();
    }

    @Unique
    private Component moveearth$status() {
        if (header != null && stage != null) {
            return Component.empty().append(header).append("  •  ").append(stage);
        }
        if (header != null) return header;
        if (stage != null) return stage;
        return Component.translatable("menu.working");
    }
}
