package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.event.EventScreenSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_EventScreenActionPacket(String action) implements CustomPacketPayload {
    public static final Type<C2S_EventScreenActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "event_screen_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_EventScreenActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.action, 12),
            buffer -> new C2S_EventScreenActionPacket(buffer.readUtf(12)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) EventScreenSync.handle(player, action);
        });
    }
}
