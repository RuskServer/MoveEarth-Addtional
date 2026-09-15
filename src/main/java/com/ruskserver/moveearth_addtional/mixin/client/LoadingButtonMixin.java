package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
abstract class LoadingButtonMixin extends AbstractWidget {
    protected LoadingButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void moveearth$renderLoadingButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ConnectScreen)) return;
        Font font = minecraft.font;
        MoveEarthUi.drawButton(graphics, font,
                new MoveEarthUi.Rect(getX(), getY(), getWidth(), getHeight()),
                getMessage(), MoveEarthUi.DANGER, isHoveredOrFocused(), active);
        callback.cancel();
    }
}
