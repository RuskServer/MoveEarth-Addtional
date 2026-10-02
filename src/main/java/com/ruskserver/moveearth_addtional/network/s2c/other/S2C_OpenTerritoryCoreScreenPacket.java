package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;

/**
 * Opens the core screen. {@code reservationMinutes} is the open time left on a configuring
 * core's reservation, 0 once it has lapsed, or -1 for a core that is not configuring.
 */
public record S2C_OpenTerritoryCoreScreenPacket(BlockPos pos, int radius,
                                                 TerritorySavedData.CoreState coreState,
                                                 int health, int maximumHealth, int reservationMinutes)
        implements CustomPacketPayload {
    /** For a core record, with its reservation looked up when it is still configuring. */
    public static S2C_OpenTerritoryCoreScreenPacket of(net.minecraft.server.MinecraftServer server,
                                                       TerritorySavedData.CoreRecord core) {
        int minutes = core.state() == TerritorySavedData.CoreState.CONFIGURING
                ? com.ruskserver.moveearth_addtional.s2.territory.ConfiguringReservationSavedData.minutesLeft(server, core)
                : com.ruskserver.moveearth_addtional.s2.territory.ConfiguringReservationPolicy.NO_RESERVATION;
        return new S2C_OpenTerritoryCoreScreenPacket(core.pos(), core.radius(), core.state(),
                core.health(), core.maximumHealth(), minutes);
    }

    public static final Type<S2C_OpenTerritoryCoreScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_territory_core"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenTerritoryCoreScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos);
                buffer.writeByte(packet.radius);
                buffer.writeEnum(packet.coreState);
                buffer.writeVarInt(packet.health);
                buffer.writeVarInt(packet.maximumHealth);
                buffer.writeVarInt(packet.reservationMinutes);
            },
            buffer -> new S2C_OpenTerritoryCoreScreenPacket(buffer.readBlockPos(), buffer.readUnsignedByte(),
                    buffer.readEnum(TerritorySavedData.CoreState.class), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt()));

    public S2C_OpenTerritoryCoreScreenPacket {
        maximumHealth = Math.max(1, maximumHealth);
        health = Math.max(0, Math.min(maximumHealth, health));
        reservationMinutes = Math.max(-1, reservationMinutes);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenTerritoryCore(this));
    }
}
