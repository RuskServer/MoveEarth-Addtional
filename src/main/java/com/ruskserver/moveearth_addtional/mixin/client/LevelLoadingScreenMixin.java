package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.loading.MoveEarthLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
abstract class LevelLoadingScreenMixin extends Screen {
    @Shadow @Final private StoringChunkProgressListener progressListener;
    @Shadow private long lastNarration;

    protected LevelLoadingScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moveearth$renderLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        long now = Util.getMillis();
        int progress = Math.max(0, Math.min(100, progressListener.getProgress()));
        MoveEarthLoadingRenderer.render(graphics, font, width, height,
                Component.translatable("screen.moveearth_addtional.loading.world"),
                progress, false, now);
        if (now - lastNarration > 2_000L) {
            lastNarration = now;
            triggerImmediateNarration(true);
        }
        callback.cancel();
    }
}
