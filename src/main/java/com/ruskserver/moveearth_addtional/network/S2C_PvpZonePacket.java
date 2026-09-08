package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpZoneState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_PvpZonePacket(boolean active, ResourceLocation dimension, BlockPos min, BlockPos max,
                                PvpZoneState state, int redPlayers, int bluePlayers)
        implements CustomPacketPayload {
    private static final ResourceLocation DEFAULT_DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
    public static final Type<S2C_PvpZonePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_zone"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpZonePacket> STREAM_CODEC = StreamCodec.of(
            S2C_PvpZonePacket::encode, S2C_PvpZonePacket::decode);

    public S2C_PvpZonePacket {
        dimension = dimension == null ? DEFAULT_DIMENSION : dimension;
        min = min == null ? BlockPos.ZERO : min.immutable();
        max = max == null ? BlockPos.ZERO : max.immutable();
        state = state == null ? PvpZoneState.NEUTRAL : state;
        redPlayers = Math.max(0, redPlayers);
        bluePlayers = Math.max(0, bluePlayers);
    }

    public static S2C_PvpZonePacket inactive() {
        return new S2C_PvpZonePacket(false, DEFAULT_DIMENSION, BlockPos.ZERO, BlockPos.ZERO,
                PvpZoneState.NEUTRAL, 0, 0);
    }

    private static void encode(FriendlyByteBuf buffer, S2C_PvpZonePacket packet) {
        buffer.writeBoolean(packet.active);
        if (!packet.active) return;
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeBlockPos(packet.min);
        buffer.writeBlockPos(packet.max);
        buffer.writeVarInt(packet.state.networkId());
        buffer.writeVarInt(packet.redPlayers);
        buffer.writeVarInt(packet.bluePlayers);
    }

    private static S2C_PvpZonePacket decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return inactive();
        return new S2C_PvpZonePacket(true, buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readBlockPos(),
                PvpZoneState.byNetworkId(buffer.readVarInt()), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpZone(this));
    }
}
