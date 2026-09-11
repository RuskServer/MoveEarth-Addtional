package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_TerritoryPreviewPacket(ResourceLocation dimension, int centerChunkX,
                                         int centerChunkZ, int radius, int chunkCount)
        implements CustomPacketPayload {
    public static final Type<S2C_TerritoryPreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_preview"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TerritoryPreviewPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeResourceLocation(packet.dimension);
                buffer.writeInt(packet.centerChunkX);
                buffer.writeInt(packet.centerChunkZ);
                buffer.writeByte(packet.radius);
                buffer.writeVarInt(packet.chunkCount);
            },
            buffer -> new S2C_TerritoryPreviewPacket(buffer.readResourceLocation(), buffer.readInt(),
                    buffer.readInt(), buffer.readUnsignedByte(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleTerritoryPreview(this));
    }
}
