package com.ruskserver.moveearth_addtional.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.client.loading.EarlyWorldEntry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadStatusManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Replace the render-mesh wait only; vanilla still owns the server-ready gate, screen close and timeout. */
@Mixin(LevelLoadStatusManager.class)
abstract class LevelLoadStatusManagerMixin {
    @Shadow @Final private LocalPlayer player;
    @Shadow @Final private ClientLevel level;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean moveearth$readyOnReceivedChunk(LevelRenderer renderer, BlockPos pos, Operation<Boolean> original) {
        return EarlyWorldEntry.receivedFootprint(level, player) || original.call(renderer, pos);
    }
}
