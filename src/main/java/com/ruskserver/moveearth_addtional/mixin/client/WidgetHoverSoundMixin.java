package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.ui.UiSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports a vanilla button, slider or tab under the cursor to {@link UiSounds}, so menus of every mod
 * built from them hover with the same sound. Text boxes and labels are left alone.
 */
@Mixin(AbstractWidget.class)
public abstract class WidgetHoverSoundMixin {
    @Shadow protected boolean isHovered;
    @Shadow public boolean active;
    @Shadow public boolean visible;

    @Inject(method = "render", at = @At("TAIL"))
    private void moveearth$reportHover(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                       CallbackInfo callback) {
        if (!isHovered || !active || !visible) return;
        Object self = this;
        if (self instanceof AbstractButton || self instanceof AbstractSliderButton || self instanceof TabButton) {
            UiSounds.hoveredWidget(self);
        }
    }
}
