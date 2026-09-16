package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.dispatch.RecoveryDispatchViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_RequestRecoveryDispatchPacket(boolean openScreen) implements CustomPacketPayload {
    public static final Type<C2S_RequestRecoveryDispatchPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "recovery_dispatch_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestRecoveryDispatchPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeBoolean(value.openScreen),
            buffer -> new C2S_RequestRecoveryDispatchPacket(buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player)
            RecoveryDispatchViewService.send(player, openScreen); });
    }
}
