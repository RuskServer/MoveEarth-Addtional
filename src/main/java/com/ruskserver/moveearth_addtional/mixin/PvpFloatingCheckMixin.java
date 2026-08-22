package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PvpFloatingCheckMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void moveearthAdditional$resetPvpFloatingTicks(CallbackInfo callbackInfo) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            aboveGroundTickCount = 0;
        }
    }
}
