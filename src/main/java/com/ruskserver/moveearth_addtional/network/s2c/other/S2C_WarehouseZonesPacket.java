package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseZoneView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Every Warehouse footprint and whether entering it calls out the guards; see {@link WarehouseZoneView}. */
public record S2C_WarehouseZonesPacket(List<Zone> zones) implements CustomPacketPayload {
    /** Far above the number of regions; bounds what a hostile server can make a client allocate. */
    public static final int MAX_ZONES = 1024;

    public static final Type<S2C_WarehouseZonesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "warehouse_zones"));
    public static final StreamCodec<FriendlyByteBuf, S2C_WarehouseZonesPacket> STREAM_CODEC = StreamCodec.of(
            S2C_WarehouseZonesPacket::encode, S2C_WarehouseZonesPacket::decode);

    public record Zone(ResourceLocation dimension, BlockPos min, WarehouseZoneView.State state) { }

    public S2C_WarehouseZonesPacket {
        zones = List.copyOf(zones.size() > MAX_ZONES ? zones.subList(0, MAX_ZONES) : zones);
    }

    private static void encode(FriendlyByteBuf buffer, S2C_WarehouseZonesPacket packet) {
        buffer.writeVarInt(packet.zones.size());
        for (Zone zone : packet.zones) {
            buffer.writeResourceLocation(zone.dimension());
            buffer.writeBlockPos(zone.min());
            buffer.writeEnum(zone.state());
        }
    }

    private static S2C_WarehouseZonesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ZONES) throw new IllegalArgumentException("Too many warehouse zones: " + size);
        List<Zone> zones = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zones.add(new Zone(buffer.readResourceLocation(), buffer.readBlockPos(),
                    buffer.readEnum(WarehouseZoneView.State.class)));
        }
        return new S2C_WarehouseZonesPacket(zones);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleWarehouseZones(this));
    }
}
