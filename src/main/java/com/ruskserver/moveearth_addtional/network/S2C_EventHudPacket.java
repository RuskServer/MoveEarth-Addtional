package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_EventHudPacket(boolean active, String title, String target, int minutes,
                                 int score, int rank, List<Leader> leaders, int pendingClaims)
        implements CustomPacketPayload {
    public static final Type<S2C_EventHudPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "event_hud"));
    public static final StreamCodec<FriendlyByteBuf, S2C_EventHudPacket> STREAM_CODEC = StreamCodec.of(
            S2C_EventHudPacket::encode, S2C_EventHudPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_EventHudPacket packet) {
        buffer.writeBoolean(packet.active);
        buffer.writeUtf(packet.title, 32);
        buffer.writeUtf(packet.target, 64);
        buffer.writeVarInt(packet.minutes);
        buffer.writeVarInt(packet.score);
        buffer.writeVarInt(packet.rank);
        buffer.writeVarInt(packet.leaders.size());
        for (Leader leader : packet.leaders) {
            buffer.writeUtf(leader.name, 32);
            buffer.writeVarInt(leader.score);
        }
        buffer.writeVarInt(packet.pendingClaims);
    }

    private static S2C_EventHudPacket decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        String title = buffer.readUtf(32);
        String target = buffer.readUtf(64);
        int minutes = buffer.readVarInt();
        int score = buffer.readVarInt();
        int rank = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > 3) throw new IllegalArgumentException("Invalid event leader count: " + count);
        List<Leader> leaders = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
            leaders.add(new Leader(buffer.readUtf(32), buffer.readVarInt()));
        return new S2C_EventHudPacket(active, title, target, minutes, score, rank,
                List.copyOf(leaders), buffer.readVarInt());
    }

    public static S2C_EventHudPacket inactive(int pendingClaims) {
        return new S2C_EventHudPacket(false, "", "", 0, 0, 0, List.of(), pendingClaims);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.EventHud.set(this));
    }

    public record Leader(String name, int score) { }
}
