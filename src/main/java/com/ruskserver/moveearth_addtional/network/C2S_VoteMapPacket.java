package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMapVoteManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_VoteMapPacket(String mapId) implements CustomPacketPayload {
    public static final Type<C2S_VoteMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_map_vote"));
    public static final StreamCodec<FriendlyByteBuf, C2S_VoteMapPacket> STREAM_CODEC = StreamCodec.of(
            C2S_VoteMapPacket::encode, C2S_VoteMapPacket::decode);

    private static void encode(FriendlyByteBuf buffer, C2S_VoteMapPacket packet) {
        buffer.writeUtf(packet.mapId, 64);
    }

    private static C2S_VoteMapPacket decode(FriendlyByteBuf buffer) {
        return new C2S_VoteMapPacket(buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PvpMapVoteManager.INSTANCE.vote(player, mapId);
            }
        });
    }
}
