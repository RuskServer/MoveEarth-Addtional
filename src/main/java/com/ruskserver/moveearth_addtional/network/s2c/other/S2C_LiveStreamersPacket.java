package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every online player who has turned on {@code /live}, for the {@code [LIVE]} tag above their head. */
public record S2C_LiveStreamersPacket(List<UUID> players) implements CustomPacketPayload {
    /** Bounds what a hostile server can make a client allocate. */
    public static final int MAX_PLAYERS = 4096;

    public static final Type<S2C_LiveStreamersPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "live_streamers"));
    public static final StreamCodec<FriendlyByteBuf, S2C_LiveStreamersPacket> STREAM_CODEC = StreamCodec.of(
            S2C_LiveStreamersPacket::encode, S2C_LiveStreamersPacket::decode);

    public S2C_LiveStreamersPacket {
        players = List.copyOf(players.size() > MAX_PLAYERS ? players.subList(0, MAX_PLAYERS) : players);
    }

    private static void encode(FriendlyByteBuf buffer, S2C_LiveStreamersPacket packet) {
        buffer.writeVarInt(packet.players.size());
        for (UUID player : packet.players) buffer.writeUUID(player);
    }

    private static S2C_LiveStreamersPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_PLAYERS) throw new IllegalArgumentException("Too many live players: " + size);
        List<UUID> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) players.add(buffer.readUUID());
        return new S2C_LiveStreamersPacket(players);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleLiveStreamers(this));
    }
}
