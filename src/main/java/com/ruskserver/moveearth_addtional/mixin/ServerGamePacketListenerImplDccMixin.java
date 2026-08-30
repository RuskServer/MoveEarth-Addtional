package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.handler.dcc.DelayedChunkCacheManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * サーバーからクライアントへ送信されるパケットを監視し、
 * チャンクのアンロード・ロードパケットを保留（DCC）するMixin。
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplDccMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void moveearthAdditional$onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl gameListener) {
            ServerPlayer player = gameListener.player;
            if (packet instanceof ClientboundForgetLevelChunkPacket forgetPacket) {
                ChunkPos chunkPos = forgetPacket.pos();
                if (DelayedChunkCacheManager.shouldDelayChunkDrop(player, chunkPos)) {
                    ci.cancel();
                }
            } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
                ChunkPos chunkPos = new ChunkPos(chunkPacket.getX(), chunkPacket.getZ());
                if (DelayedChunkCacheManager.consumeDelayedChunk(player, chunkPos)) {
                    ci.cancel();
                }
            }
        }
    }
}