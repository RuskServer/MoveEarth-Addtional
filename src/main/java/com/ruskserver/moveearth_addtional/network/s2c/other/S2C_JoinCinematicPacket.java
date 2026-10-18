package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Whether the join cinematic may play for this login; see {@code JoinCinematicService}. */
public record S2C_JoinCinematicPacket(boolean allowed) implements CustomPacketPayload {
    public static final Type<S2C_JoinCinematicPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "join_cinematic"));

    public static final StreamCodec<FriendlyByteBuf, S2C_JoinCinematicPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeBoolean(packet.allowed), buffer -> new S2C_JoinCinematicPacket(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.cinematic.JoinCinematic.permit(allowed));
    }
}
