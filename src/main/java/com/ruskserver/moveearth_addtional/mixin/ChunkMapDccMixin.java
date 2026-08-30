package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.handler.dcc.DelayedChunkCacheManager;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * サーバー側チャンク追跡（ChunkMap）に介入し、
 * 視界外チャンクの即時アンロード保留（DCC）および再侵入時の再送信スキップを適用するMixin。
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapDccMixin {

    @Inject(
            method = "updateChunkTracking",
            at = @At("HEAD"),
            cancellable = true
    )
    private void moveearthAdditional$handleDelayedChunkTracking(
            ServerPlayer player,
            ChunkPos chunkPos,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
            boolean wasTracked,
            boolean isTracked,
            CallbackInfo ci
    ) {
        if (isTracked == wasTracked) {
            return;
        }

        if (!isTracked && wasTracked) {
            // 視界外へ離脱した際：DCCが有効なら即時アンロードを保留
            if (DelayedChunkCacheManager.shouldDelayChunkDrop(player, chunkPos)) {
                ci.cancel();
            }
        } else if (isTracked && !wasTracked) {
            // 視界内へ再侵入した際：DCCに保留されていたら完全なチャンクデータ再送信をスキップ
            if (DelayedChunkCacheManager.consumeDelayedChunk(player, chunkPos)) {
                ci.cancel();
            }
        }
    }
}
