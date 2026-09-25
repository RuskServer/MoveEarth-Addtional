package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_EconomyHudPacket(long balance) implements CustomPacketPayload {
    public static final Type<S2C_EconomyHudPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "economy_hud"));
    public static final StreamCodec<FriendlyByteBuf, S2C_EconomyHudPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarLong(packet.balance),
            buf -> new S2C_EconomyHudPacket(buf.readVarLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.EconomyWaypointHud.setBalance(balance));
    }
}
