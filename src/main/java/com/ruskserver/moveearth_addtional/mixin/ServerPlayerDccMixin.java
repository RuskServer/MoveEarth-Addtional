package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.handler.dcc.DelayedChunkCacheManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * サーバー側プレイヤーのチャンク追跡・忘却メソッドに介入し、
 * 視界外チャンクの即時アンロード保留（DCC）および再侵入時の再送信スキップを適用するMixin。
 * Minecraft 1.21.1 仕様対応版。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDccMixin {

    @Inject(
            method = "untrackChunk(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void moveearthAdditional$onUntrackChunk(ChunkPos chunkPos, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        // 視界外へ離脱した際：DCCが有効なら即時アンロード（ClientboundForgetLevelChunkPacket）の送信を保留
        if (DelayedChunkCacheManager.shouldDelayChunkDrop(player, chunkPos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "trackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void moveearthAdditional$onTrackChunk(ChunkPos chunkPos, Packet<?> packet, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        // 視界内へ再侵入した際：DCCに保留されていたら完全なチャンクデータ（ClientboundLevelChunkWithLightPacket）再送信をスキップ
        if (DelayedChunkCacheManager.consumeDelayedChunk(player, chunkPos)) {
            ci.cancel();
        }
    }
}
