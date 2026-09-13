package com.ruskserver.moveearth_addtional.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.client.TerritoryMapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inserts territories after map pixels and before icons; Map Atlases reuses this vanilla renderer. */
@Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
public abstract class MapInstanceTerritoryOverlayMixin {
    @Shadow private MapItemSavedData data;

    @Inject(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;getDecorations()Ljava/lang/Iterable;"))
    private void moveEarth$renderTerritories(PoseStack poseStack, MultiBufferSource buffers,
                                              boolean inItemFrame, int packedLight, CallbackInfo callback) {
        TerritoryMapRenderer.render(poseStack, buffers, data);
    }
}
