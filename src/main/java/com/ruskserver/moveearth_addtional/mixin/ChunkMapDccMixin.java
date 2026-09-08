package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.config.DelayedChunkCacheConfig;
import com.ruskserver.moveearth_addtional.handler.dcc.DelayedChunkTrackingView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Integrates DCC at the authoritative chunk-tracking layer.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapDccMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void moveearthAdditional$updateDelayedChunkTracking(ServerPlayer player, CallbackInfo callback) {
        if (!DelayedChunkCacheConfig.enabled()) {
            if (player.getChunkTrackingView() instanceof DelayedChunkTrackingView) {
                DelayedChunkTrackingView.disableForPlayer(player);
                callback.cancel();
            }
            return;
        }

        if (player.level() == this.level) {
            DelayedChunkTrackingView.updatePlayer(
                    player,
                    this.getPlayerViewDistance(player),
                    DelayedChunkCacheConfig.settings(),
                    player.getChunkTrackingView() instanceof DelayedChunkTrackingView
                            ? null
                            : this.createContext(player)
            );
        }
        callback.cancel();
    }

    private DelayedChunkTrackingView.Context createContext(ServerPlayer player) {
        return new DelayedChunkTrackingView.Context() {
            @Override
            public void startTracking(ChunkPos chunkPos) {
                ChunkMapDccMixin.this.markChunkPendingToSend(player, chunkPos);
            }

            @Override
            public void stopTracking(ChunkPos chunkPos) {
                dropChunk(player, chunkPos);
            }
        };
    }

    @Shadow
    abstract int getPlayerViewDistance(ServerPlayer player);

    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos);

    @Shadow
    private static void dropChunk(ServerPlayer player, ChunkPos chunkPos) {
    }
}
