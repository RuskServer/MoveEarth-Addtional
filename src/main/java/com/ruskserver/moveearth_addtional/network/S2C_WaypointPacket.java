package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.WaypointSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_WaypointPacket(boolean active, String name, ResourceLocation dimension, BlockPos pos, boolean market)
        implements CustomPacketPayload {
    public static final Type<S2C_WaypointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "waypoint"));
    public static final StreamCodec<FriendlyByteBuf, S2C_WaypointPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.active);
                if (packet.active) {
                    buf.writeUtf(packet.name, 48);
                    buf.writeResourceLocation(packet.dimension);
                    buf.writeBlockPos(packet.pos);
                    buf.writeBoolean(packet.market);
                }
            },
            buf -> buf.readBoolean() ? new S2C_WaypointPacket(true, buf.readUtf(48),
                    buf.readResourceLocation(), buf.readBlockPos(), buf.readBoolean()) : clear());

    public static S2C_WaypointPacket clear() { return new S2C_WaypointPacket(false, "", null, null, false); }
    public static S2C_WaypointPacket of(WaypointSavedData.Waypoint waypoint) {
        return new S2C_WaypointPacket(true, waypoint.name(), waypoint.dimension(), waypoint.pos(), waypoint.market());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.EconomyWaypointHud.setWaypoint(this));
    }
}
