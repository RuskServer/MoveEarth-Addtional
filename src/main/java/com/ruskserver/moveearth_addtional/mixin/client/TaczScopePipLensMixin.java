package com.ruskserver.moveearth_addtional.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.client.scope.ScopePipRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.BedrockAttachmentModel", remap = false)
public abstract class TaczScopePipLensMixin {
    @Shadow(remap = false) private ItemStack attachmentItem;
    @Shadow(remap = false) private ItemStack currentGunItem;
    @Shadow(remap = false) protected List<Boolean> isScopeOcular;
    @Inject(method = "renderOcularAndDivision", at = @At("HEAD"), remap = false)
    private void moveearth$drawLens(PoseStack pose, ItemDisplayContext context, RenderType type,
                                   int light, int overlay, boolean selective, CallbackInfo callback) {
        if (context.firstPerson()) ScopePipRenderer.composite(currentGunItem, attachmentItem, isScopeOcular, selective);
    }
}
