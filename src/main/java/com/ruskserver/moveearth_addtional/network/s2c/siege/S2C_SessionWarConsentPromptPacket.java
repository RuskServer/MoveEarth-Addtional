package com.ruskserver.moveearth_addtional.network.s2c.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Optional variant: changes the displayed penalty rule without changing the required v3.7 packet codec. */
public record S2C_SessionWarConsentPromptPacket(S2C_WarConsentPromptPacket prompt) implements CustomPacketPayload {
    public static final Type<S2C_SessionWarConsentPromptPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "session_war_consent_prompt"));
    public static final StreamCodec<FriendlyByteBuf, S2C_SessionWarConsentPromptPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> S2C_WarConsentPromptPacket.STREAM_CODEC.encode(buffer, packet.prompt()),
            buffer -> new S2C_SessionWarConsentPromptPacket(S2C_WarConsentPromptPacket.STREAM_CODEC.decode(buffer)));

    public S2C_SessionWarConsentPromptPacket { java.util.Objects.requireNonNull(prompt); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.WarConsentClient.prompt(prompt, true));
    }
}
