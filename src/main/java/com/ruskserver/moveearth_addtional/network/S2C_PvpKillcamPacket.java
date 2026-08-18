package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record S2C_PvpKillcamPacket(UUID killerId, String killer, double x, double y, double z, int ticks) implements CustomPacketPayload {
    public static final Type<S2C_PvpKillcamPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_killcam"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpKillcamPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.killerId); buf.writeUtf(p.killer); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); buf.writeVarInt(p.ticks); },
            buf -> new S2C_PvpKillcamPacket(buf.readUUID(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpKillcam(this));
    }
}
