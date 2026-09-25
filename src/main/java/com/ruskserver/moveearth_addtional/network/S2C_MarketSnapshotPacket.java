package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_MarketSnapshotPacket(long balance, UUID selectedStation,
                                       List<StationEntry> stations, List<OrderEntry> orders,
                                       List<ClaimEntry> claims, String result) implements CustomPacketPayload {
    public static final Type<S2C_MarketSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "market_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_MarketSnapshotPacket> STREAM_CODEC = StreamCodec.of(
            S2C_MarketSnapshotPacket::encode, S2C_MarketSnapshotPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2C_MarketSnapshotPacket p) {
        buf.writeVarLong(p.balance);
        buf.writeUUID(p.selectedStation);
        buf.writeVarInt(p.stations.size());
        for (StationEntry s : p.stations) {
            buf.writeUUID(s.id); buf.writeUtf(s.nation, 64); buf.writeUtf(s.location, 80);
            buf.writeResourceLocation(s.dimension); buf.writeBlockPos(s.pos);
            buf.writeBoolean(s.own); buf.writeBoolean(s.local);
        }
        buf.writeVarInt(p.orders.size());
        for (OrderEntry o : p.orders) {
            buf.writeUUID(o.id); buf.writeUUID(o.stationId); buf.writeUtf(o.side, 4);
            buf.writeResourceLocation(o.itemId);
            ComponentSerialization.STREAM_CODEC.encode(buf, o.itemName);
            buf.writeBoolean(o.gunId != null);
            if (o.gunId != null) buf.writeResourceLocation(o.gunId);
            buf.writeVarInt(o.remaining); buf.writeVarLong(o.unitPrice);
            buf.writeUtf(o.nation, 64); buf.writeBoolean(o.own); buf.writeBoolean(o.local);
        }
        buf.writeVarInt(p.claims.size());
        for (ClaimEntry c : p.claims) {
            buf.writeUUID(c.id); buf.writeUUID(c.stationId);
            ComponentSerialization.STREAM_CODEC.encode(buf, c.itemName);
            buf.writeBoolean(c.gunId != null);
            if (c.gunId != null) buf.writeResourceLocation(c.gunId);
            buf.writeVarInt(c.quantity); buf.writeUtf(c.nation, 64); buf.writeBoolean(c.local);
        }
        buf.writeUtf(p.result, 160);
    }

    private static S2C_MarketSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        long balance = buf.readVarLong();
        UUID station = buf.readUUID();
        int stationCount = count(buf, 256);
        List<StationEntry> stations = new ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) stations.add(new StationEntry(buf.readUUID(),
                buf.readUtf(64), buf.readUtf(80), buf.readResourceLocation(), buf.readBlockPos(),
                buf.readBoolean(), buf.readBoolean()));
        int orderCount = count(buf, 100);
        List<OrderEntry> orders = new ArrayList<>(orderCount);
        for (int i = 0; i < orderCount; i++) {
            UUID id = buf.readUUID(), stationId = buf.readUUID();
            String side = buf.readUtf(4);
            ResourceLocation itemId = buf.readResourceLocation();
            Component itemName = ComponentSerialization.STREAM_CODEC.decode(buf);
            ResourceLocation gunId = buf.readBoolean() ? buf.readResourceLocation() : null;
            orders.add(new OrderEntry(id, stationId, side, itemId, itemName, gunId,
                    buf.readVarInt(), buf.readVarLong(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean()));
        }
        int claimCount = count(buf, 100);
        List<ClaimEntry> claims = new ArrayList<>(claimCount);
        for (int i = 0; i < claimCount; i++) {
            UUID id = buf.readUUID(), stationId = buf.readUUID();
            Component itemName = ComponentSerialization.STREAM_CODEC.decode(buf);
            ResourceLocation gunId = buf.readBoolean() ? buf.readResourceLocation() : null;
            claims.add(new ClaimEntry(id, stationId, itemName, gunId,
                    buf.readVarInt(), buf.readUtf(64), buf.readBoolean()));
        }
        return new S2C_MarketSnapshotPacket(balance, station, List.copyOf(stations), List.copyOf(orders),
                List.copyOf(claims), buf.readUtf(160));
    }

    private static int count(RegistryFriendlyByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) throw new IllegalArgumentException("Market snapshot too large");
        return count;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleMarket(this));
    }

    public record StationEntry(UUID id, String nation, String location,
                               ResourceLocation dimension, BlockPos pos, boolean own, boolean local) { }
    public record OrderEntry(UUID id, UUID stationId, String side, ResourceLocation itemId,
                             Component itemName, ResourceLocation gunId, int remaining, long unitPrice, String nation,
                             boolean own, boolean local) { }
    public record ClaimEntry(UUID id, UUID stationId, Component itemName, ResourceLocation gunId, int quantity,
                             String nation, boolean local) { }
}
