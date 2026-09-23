package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_PrisonerActionResultPacket(boolean success, String messageKey)
        implements CustomPacketPayload {
    public static final Type<S2C_PrisonerActionResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "prisoner_action_result"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PrisonerActionResultPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.success());
                buffer.writeUtf(packet.messageKey(), 128);
            }, buffer -> new S2C_PrisonerActionResultPacket(buffer.readBoolean(), buffer.readUtf(128)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler
                .handlePrisonerActionResult(this));
    }
}
