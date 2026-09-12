package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;

public record S2C_OpenTerritoryCoreScreenPacket(BlockPos pos, int radius,
                                                 TerritorySavedData.CoreState coreState,
                                                 int health, int maximumHealth)
        implements CustomPacketPayload {
    public static final Type<S2C_OpenTerritoryCoreScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_territory_core"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenTerritoryCoreScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos);
                buffer.writeByte(packet.radius);
                buffer.writeEnum(packet.coreState);
                buffer.writeVarInt(packet.health);
                buffer.writeVarInt(packet.maximumHealth);
            },
            buffer -> new S2C_OpenTerritoryCoreScreenPacket(buffer.readBlockPos(), buffer.readUnsignedByte(),
                    buffer.readEnum(TerritorySavedData.CoreState.class), buffer.readVarInt(), buffer.readVarInt()));

    public S2C_OpenTerritoryCoreScreenPacket {
        maximumHealth = Math.max(1, maximumHealth);
        health = Math.max(0, Math.min(maximumHealth, health));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenTerritoryCore(this));
    }
}
