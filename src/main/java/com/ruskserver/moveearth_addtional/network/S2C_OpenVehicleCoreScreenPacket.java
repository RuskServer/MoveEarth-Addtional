package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_OpenVehicleCoreScreenPacket(String nationName, int health, int maximumHealth,
                                               String upkeepState, long upkeepCost,
                                               int connectedBodies, int reinforcedBlocks)
        implements CustomPacketPayload {
    public static final Type<S2C_OpenVehicleCoreScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_vehicle_core"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenVehicleCoreScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUtf(packet.nationName, 128);
                buffer.writeVarInt(packet.health);
                buffer.writeVarInt(packet.maximumHealth);
                buffer.writeUtf(packet.upkeepState, 32);
                buffer.writeVarLong(packet.upkeepCost);
                buffer.writeVarInt(packet.connectedBodies);
                buffer.writeVarInt(packet.reinforcedBlocks);
            },
            buffer -> new S2C_OpenVehicleCoreScreenPacket(buffer.readUtf(128), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readUtf(32), buffer.readVarLong(), buffer.readVarInt(),
                    buffer.readVarInt()));

    public S2C_OpenVehicleCoreScreenPacket {
        nationName = nationName == null ? "" : nationName;
        upkeepState = upkeepState == null ? "disabled" : upkeepState.toLowerCase(java.util.Locale.ROOT);
        maximumHealth = Math.max(1, maximumHealth);
        health = Math.max(0, Math.min(maximumHealth, health));
        upkeepCost = Math.max(0L, upkeepCost);
        connectedBodies = Math.max(1, connectedBodies);
        reinforcedBlocks = Math.max(0, reinforcedBlocks);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenVehicleCore(this));
    }
}
