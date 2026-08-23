package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_PvpResultPacket(int outcome, int redScore, int blueScore, int ticks)
        implements CustomPacketPayload {
    public static final int LOSS = 0;
    public static final int DRAW = 1;
    public static final int WIN = 2;
    public static final Type<S2C_PvpResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_result"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpResultPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.outcome);
                buffer.writeVarInt(packet.redScore);
                buffer.writeVarInt(packet.blueScore);
                buffer.writeVarInt(packet.ticks);
            },
            buffer -> new S2C_PvpResultPacket(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt()));

    public static S2C_PvpResultPacket clear() {
        return new S2C_PvpResultPacket(DRAW, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpResult(this));
    }
}
