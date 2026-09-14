package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_CloseOnboardingPacket() implements CustomPacketPayload {
    public static final Type<S2C_CloseOnboardingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "close_onboarding"));
    public static final StreamCodec<FriendlyByteBuf, S2C_CloseOnboardingPacket> STREAM_CODEC =
            StreamCodec.unit(new S2C_CloseOnboardingPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(ClientPacketHandler::handleCloseOnboarding);
    }
}
