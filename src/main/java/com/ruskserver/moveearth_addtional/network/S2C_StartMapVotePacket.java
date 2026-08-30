package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMapDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_StartMapVotePacket(List<PvpMapDefinition> candidates, int durationSeconds)
        implements CustomPacketPayload {
    public static final Type<S2C_StartMapVotePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_map_vote_start"));
    public static final StreamCodec<FriendlyByteBuf, S2C_StartMapVotePacket> STREAM_CODEC = StreamCodec.of(
            S2C_StartMapVotePacket::encode, S2C_StartMapVotePacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_StartMapVotePacket packet) {
        buffer.writeVarInt(packet.durationSeconds);
        buffer.writeVarInt(packet.candidates.size());
        for (PvpMapDefinition map : packet.candidates) {
            map.write(buffer);
        }
    }

    private static S2C_StartMapVotePacket decode(FriendlyByteBuf buffer) {
        int duration = buffer.readVarInt();
        int size = buffer.readVarInt();
        List<PvpMapDefinition> candidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            candidates.add(PvpMapDefinition.read(buffer));
        }
        return new S2C_StartMapVotePacket(List.copyOf(candidates), duration);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleStartMapVote(this));
    }
}
