package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_RecoveryDispatchActionResultPacket(int requestId, boolean success, String messageKey)
        implements CustomPacketPayload {
    public static final Type<S2C_RecoveryDispatchActionResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "recovery_dispatch_result"));
    public static final StreamCodec<FriendlyByteBuf, S2C_RecoveryDispatchActionResultPacket> STREAM_CODEC = StreamCodec.of(
            (b, value) -> { b.writeVarInt(value.requestId); b.writeBoolean(value.success); b.writeUtf(value.messageKey, 128); },
            b -> new S2C_RecoveryDispatchActionResultPacket(b.readVarInt(), b.readBoolean(), b.readUtf(128)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleRecoveryDispatchResult(this));
    }
}
