package com.ruskserver.moveearth_addtional.network.s2c.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A PvP map's {@link com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalMap}, compressed, for the deploy screen. */
public record S2C_PvpTacticalMapPacket(String mapId, long version, int originX, int originZ, int cellSize, int width,
                                       int depth, int baseY, byte[] data) implements CustomPacketPayload {
    private static final int MAX_DATA = 1 << 19;

    public static final Type<S2C_PvpTacticalMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_tactical_map"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpTacticalMapPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.mapId, 64);
                buf.writeLong(p.version);
                buf.writeInt(p.originX);
                buf.writeInt(p.originZ);
                buf.writeVarInt(p.cellSize);
                buf.writeVarInt(p.width);
                buf.writeVarInt(p.depth);
                buf.writeInt(p.baseY);
                buf.writeByteArray(p.data);
            },
            buf -> new S2C_PvpTacticalMapPacket(buf.readUtf(64), buf.readLong(), buf.readInt(), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readByteArray(MAX_DATA)));

    public static S2C_PvpTacticalMapPacket of(com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalMap map) {
        return new S2C_PvpTacticalMapPacket(map.mapId(), map.version(), map.originX(), map.originZ(), map.cellSize(),
                map.width(), map.depth(), map.baseY(), map.compressed());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpTacticalMap(this));
    }
}
