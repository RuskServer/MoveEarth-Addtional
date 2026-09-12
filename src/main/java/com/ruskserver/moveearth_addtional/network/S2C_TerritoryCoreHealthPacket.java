package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_TerritoryCoreHealthPacket(BlockPos pos, int health, int maximumHealth)
        implements CustomPacketPayload {
    public static final Type<S2C_TerritoryCoreHealthPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_core_health"));
    public static final StreamCodec<FriendlyByteBuf, S2C_TerritoryCoreHealthPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos);
                buffer.writeVarInt(packet.health);
                buffer.writeVarInt(packet.maximumHealth);
            },
            buffer -> new S2C_TerritoryCoreHealthPacket(
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt()));

    public S2C_TerritoryCoreHealthPacket {
        maximumHealth = Math.max(1, maximumHealth);
        health = Math.max(0, Math.min(maximumHealth, health));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleTerritoryCoreHealth(this));
    }
}
