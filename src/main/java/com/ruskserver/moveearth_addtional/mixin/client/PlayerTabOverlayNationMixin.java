package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.NationTabList;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;

/** Nation tags and nation grouping in the tab list; see {@link NationTabList}. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayNationMixin {
    @ModifyArg(method = "getPlayerInfos", at = @At(value = "INVOKE",
            target = "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;"))
    private Comparator<PlayerInfo> moveearth$nationOrder(Comparator<PlayerInfo> original) {
        return NationTabList.order(original);
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void moveearth$nationTag(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NationTabList.decorate(info, cir.getReturnValue()));
    }
}
