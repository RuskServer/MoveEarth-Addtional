package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record S2C_UpdateMapVotePacket(Map<String, Integer> votes, int secondsRemaining)
        implements CustomPacketPayload {
    public static final Type<S2C_UpdateMapVotePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_map_vote_update"));
    public static final StreamCodec<FriendlyByteBuf, S2C_UpdateMapVotePacket> STREAM_CODEC = StreamCodec.of(
            S2C_UpdateMapVotePacket::encode, S2C_UpdateMapVotePacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_UpdateMapVotePacket packet) {
        buffer.writeVarInt(packet.secondsRemaining);
        buffer.writeVarInt(packet.votes.size());
        packet.votes.forEach((mapId, count) -> {
            buffer.writeUtf(mapId, 64);
            buffer.writeVarInt(count);
        });
    }

    private static S2C_UpdateMapVotePacket decode(FriendlyByteBuf buffer) {
        int seconds = buffer.readVarInt();
        int size = buffer.readVarInt();
        Map<String, Integer> votes = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            votes.put(buffer.readUtf(64), buffer.readVarInt());
        }
        return new S2C_UpdateMapVotePacket(Map.copyOf(votes), seconds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleUpdateMapVote(this));
    }
}
