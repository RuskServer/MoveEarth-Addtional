package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_OpenPvpScreenPacket(boolean joined, boolean active, boolean hosting, int points, String tasks,
                                      String selectedLoadoutId) implements CustomPacketPayload {
    public static final Type<S2C_OpenPvpScreenPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_open"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenPvpScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.joined); buf.writeBoolean(p.active); buf.writeBoolean(p.hosting); buf.writeVarInt(p.points); buf.writeUtf(p.tasks); buf.writeUtf(p.selectedLoadoutId, 32); },
            buf -> new S2C_OpenPvpScreenPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(), buf.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenPvp(this));
    }
}
