package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_PvpHudPacket(boolean active, int red, int blue, int target, int ticksLeft, String hill) implements CustomPacketPayload {
    public static final Type<S2C_PvpHudPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_hud"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpHudPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.active); buf.writeVarInt(p.red); buf.writeVarInt(p.blue); buf.writeVarInt(p.target); buf.writeVarInt(p.ticksLeft); buf.writeUtf(p.hill); },
            buf -> new S2C_PvpHudPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
    public static S2C_PvpHudPacket inactive() { return new S2C_PvpHudPacket(false, 0, 0, 0, 0, ""); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpHud(this));
    }
}
